#!/usr/bin/env python3
"""Checks the code of a release APK for calls that crash on old Android versions.

The Kotlin Toolchain has no core library desugaring, so a call to a Java library API that is newer
than minSdk crashes on older devices with NoSuchMethodError. Android Lint does not see the code of
the libraries, so this check reads the dex code of the whole APK.

It reads every java.* and javax.* call in the APK and looks up its API level in api-versions.xml of
the newest installed SDK platform. These calls are allowed, because they run only where the API
exists:
- calls inside the API outlines that R8 and D8 write for guarded calls ($$ExternalSyntheticApiModelOutline),
- super calls inside an override of the same method,
- the callers in ALLOWED and ALLOWED_PREFIXES, each with a reason.

The check also fails when the R8 mapping names a class of Google Play services or Tink. The app
must not ship either one.

Usage: python3 scripts/apk_api_check.py <apk> [--mapping <mapping.txt>] [--min-sdk <level>]
scripts/android_release.py runs it on every release APK. --min-sdk replaces the minSdk of the APK,
to see the check fail on a lower level.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile

# Deobfuscated caller class -> reason. Keep each entry short and checkable.
ALLOWED = {
    "kotlinx.serialization.internal.ClassValueReferences":
        "kotlinx.serialization uses ClassValue only after it checks that the class exists",
    # Netty uses these VarHandle classes only when PlatformDependent.hasVarHandle() is true. That
    # needs javaVersion() >= 9, and Netty reports Java 6 on Android.
    "io.netty.buffer.VarHandleByteBufferAccess": "used only when PlatformDependent.hasVarHandle() is true",
    "io.netty.util.internal.RefCnt$VarHandleRefCnt": "used only when PlatformDependent.hasVarHandle() is true",
    "io.netty.util.internal.VarHandleReferenceCountUpdater": "used only when PlatformDependent.hasVarHandle() is true",
}

# Deobfuscated caller package prefix -> reason, for libraries that pick a whole class family at run time.
ALLOWED_PREFIXES = {
    "io.netty.util.internal.shaded.org.jctools.queues.varhandle.":
        "Netty makes these queues only when PlatformDependent.javaVersion() >= 11, and it reports 6 on Android",
}

# Packages that must not reach the APK. The Gradle build excluded both from the dependencies.
FORBIDDEN_PACKAGES = ("com.google.android.gms.", "com.google.crypto.tink.")

INVOKE = re.compile(r"(invoke-[a-z/-]+) \{[^}]*\}, L([^;]+);\.([^:]+):(\([^)]*\)\S+)")
CLASS = re.compile(r"Class descriptor\s*: 'L([^;]+);'")
METHOD_NAME = re.compile(r"^\s+name\s+: '([^']+)'")


def newest(paths: list[str]) -> str:
    def key(path: str) -> list[int]:
        return [int(part) for part in re.findall(r"\d+", os.path.basename(path.rstrip("/")))]

    return sorted(paths, key=key)[-1]


def sdk_dir() -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise SystemExit("apk_api_check: set ANDROID_HOME.")
    return sdk


def api_levels(api_xml: str) -> dict:
    """Class name -> (class level, {method signature: level}, parent class names) for java/ and javax/."""
    def level(value: str | None, default: float) -> float:
        return float(value.split(":")[0]) if value else default

    classes = {}
    for cls in ET.parse(api_xml).getroot().iter("class"):
        name = cls.get("name")
        if not name.startswith(("java/", "javax/")):
            continue
        since = level(cls.get("since"), 1)
        methods = {m.get("name"): level(m.get("since"), since) for m in cls.iter("method")}
        parents = [e.get("name") for e in cls if e.tag in ("extends", "implements")]
        classes[name] = (since, methods, parents)
    return classes


def method_level(classes: dict, owner: str, signature: str, seen: set | None = None) -> float | None:
    seen = seen if seen is not None else set()
    if owner in seen or owner not in classes:
        return None
    seen.add(owner)
    since, methods, parents = classes[owner]
    if signature in methods:
        return methods[signature]
    for parent in parents:
        found = method_level(classes, parent, signature, seen)
        if found is not None:
            return found
    return None


def deobfuscation(mapping: str | None) -> dict:
    """Obfuscated class name -> original class name, from an R8 mapping file."""
    names = {}
    if not mapping or not os.path.isfile(mapping):
        return names
    with open(mapping, encoding="utf-8") as lines:
        for line in lines:
            if line[:1] not in ("#", " ", "\t", "") and line.rstrip().endswith(":") and " -> " in line:
                original, obfuscated = line.rstrip()[:-1].split(" -> ")
                names[obfuscated] = original
    return names


def forbidden_classes(names: dict) -> list[str]:
    return sorted(original for original in names.values() if original.startswith(FORBIDDEN_PACKAGES))


def check(apk: str, mapping: str | None = None, min_sdk: int | None = None) -> list[str]:
    """Returns the findings for [apk]. An empty list means the APK passes."""
    sdk = sdk_dir()
    tools = newest([os.path.join(sdk, "build-tools", d) for d in os.listdir(os.path.join(sdk, "build-tools"))])
    platforms = [
        os.path.join(sdk, "platforms", d)
        for d in os.listdir(os.path.join(sdk, "platforms"))
        if os.path.isfile(os.path.join(sdk, "platforms", d, "data", "api-versions.xml"))
    ]
    if not platforms:
        raise SystemExit(f"apk_api_check: no api-versions.xml under {sdk}/platforms.")
    api_xml = os.path.join(newest(platforms), "data", "api-versions.xml")

    if min_sdk is None:
        badging = subprocess.run(
            [os.path.join(tools, "aapt2"), "dump", "badging", apk], check=True, capture_output=True, text=True,
        ).stdout
        # aapt2 prints "minSdkVersion:'26'", the older aapt prints "sdkVersion:'26'".
        declared = re.search(r"(?:minSdkVersion|sdkVersion):'(\d+)'", badging)
        if not declared:
            raise SystemExit(f"apk_api_check: no minSdk in the badging of {apk}.")
        min_sdk = int(declared.group(1))

    names = deobfuscation(mapping)
    findings = {f"{name} is a forbidden library class" for name in forbidden_classes(names)}
    classes = api_levels(api_xml)
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", entry):
                continue
            dex = archive.extract(entry, tmp)
            dump = subprocess.Popen(
                [os.path.join(tools, "dexdump"), "-d", dex], stdout=subprocess.PIPE, text=True, errors="replace",
            )
            caller, method, in_method_header = "?", "?", False
            for line in dump.stdout:
                found = CLASS.search(line)
                if found:
                    caller = found.group(1).replace("/", ".")
                    continue
                if "(in L" in line:
                    in_method_header = True
                    continue
                if in_method_header:
                    found = METHOD_NAME.match(line)
                    if found:
                        method, in_method_header = found.group(1), False
                    continue
                found = INVOKE.search(line)
                if not found or not found.group(2).startswith(("java/", "javax/")):
                    continue
                kind, owner, name, descriptor = found.groups()
                since = method_level(classes, owner, name + descriptor)
                if since is None and owner in classes:
                    since = classes[owner][0]
                if since is None or since <= min_sdk:
                    continue
                original = names.get(caller, caller)
                if "$$ExternalSyntheticApiModelOutline" in original:
                    continue
                if kind.startswith("invoke-super") and name == method:
                    continue
                if original in ALLOWED or original.startswith(tuple(ALLOWED_PREFIXES)):
                    continue
                findings.add(f"{original} calls {owner.replace('/', '.')}.{name}{descriptor} (API {since:g}, minSdk {min_sdk})")
            if dump.wait() != 0:
                raise SystemExit(f"apk_api_check: dexdump failed on {entry} of {apk}.")
            os.remove(dex)
    return sorted(findings)


def main() -> int:
    parser = argparse.ArgumentParser(description="Checks a release APK for Java API calls above its minSdk.")
    parser.add_argument("apk")
    parser.add_argument("--mapping", help="the R8 mapping of the APK, for readable class names")
    parser.add_argument("--min-sdk", type=int, help="check against this level instead of the minSdk of the APK")
    args = parser.parse_args()
    findings = check(args.apk, args.mapping, args.min_sdk)
    name = os.path.basename(args.apk)
    if findings:
        print(f"{name}: {len(findings)} finding(s). A Java API call above minSdk crashes on older devices,")
        print("because the build has no core library desugaring. Guard each call with Build.VERSION.SDK_INT")
        print(f"or use another API. If a library already guards it, add the caller to ALLOWED in {__file__}.")
        for finding in findings:
            print(f"  {finding}")
        return 1
    print(f"{name}: no Java API call above minSdk outside guarded code, and no forbidden library.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
