#!/usr/bin/env python3
"""Builds every shippable Android file into AndroidAppOutput/.

The files are:
  synkplay-<version>-full-universal.apk   the full app with every video engine
  synkplay-<version>-full.aab             the same app for Google Play
  syncplay-<version>-exo-only.apk         the exoOnly app: ExoPlayer only, no native player library
  symbols/mapping.txt                     the R8 mapping of the full app
  symbols/native-debug-symbols.zip        the native debug symbols of the full app, for Google Play

The exoOnly APK keeps "syncplay" in its name on purpose. IzzyOnDroid's updater fetches the release
asset by that name.

The full universal APK gets compressed native libraries (see compress_libraries). The exoOnly APK
and the AAB keep the uncompressed libraries of the toolchain build.

Anyone can rebuild the exoOnly APK and get the published bytes (docs/DEVELOPING.md, "Reproducing a
published APK"). So the script writes the APKs itself (write_apk): each entry keeps its compressed
bytes, and the script aligns the entries as zipalign does. The exoOnly APK takes its native
libraries as the dependencies ship them (strip_libraries). The APK then does not depend on the
zlib, the zipalign or the NDK of the machine.

Usage:
  python3 scripts/android_release.py              build all three files
  python3 scripts/android_release.py --exo-only   build only the exoOnly APK
  python3 scripts/android_release.py --full-only  build only the full APK and the AAB

Warning: the script deletes the output folder first. Pass --output <folder> to write somewhere else.

Signing: the toolchain signs the full APK and the AAB with keystore.properties at the project root
(storeFile, storePassword, keyAlias, keyPassword; storeFile is relative to androidApp/). This script
changes both APKs after the build, so it signs them again with the same file. Set
SYNKPLAY_UNSIGNED_RELEASE=true to build unsigned files on purpose, for example to check that a
published APK was built from this source. Their names then end in "-unsigned".

Without --allow-unverified, the script fails unless apksigner accepts each signed APK with the
release certificate and each APK carries the version of shared/module.yaml. It also fails when an
APK calls a Java API above minSdk, or holds a class of Google Play services or Tink
(scripts/apk_api_check.py). The build has no core library desugaring, so such a call would crash on
older devices.
"""

import argparse
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# apk_api_check.py is a file next to this one. No .pyc file is written into the repository.
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / "scripts"))
import apk_api_check  # noqa: E402
OUTPUT = ROOT / "AndroidAppOutput"
APP_MODULE = ROOT / "androidApp" / "module.yaml"
KEYSTORE_PROPERTIES = ROOT / "keystore.properties"

FULL_APPLICATION_ID = "com.yuroyami.syncplay"
EXO_APPLICATION_ID = "com.reddnek.syncplay"

# The SHA-256 digest of the release signing certificate, as `apksigner verify --print-certs` prints
# it. It is public: anyone can read it from a published APK.
RELEASE_CERT_SHA256 = "cfeed0f6458db903ef44f558ed97a6edd0a656bb76bc645178b3f266de2a9566"

# The native libraries that the exoOnly APK drops: every library of libmpvKt, and KitePlayer's
# FFmpeg decoder and subtitle renderer. Keep the list equal to what the pinned versions ship; the
# exoOnly check below fails if a player library is still inside.
EXO_ONLY_DROPPED = [
    "libavcodec.so", "libavdevice.so", "libavfilter.so", "libavformat.so", "libavutil.so",
    "libswresample.so", "libswscale.so", "libmpv.so", "libmpvkt_jni.so", "libc++_shared.so",
    "libkitecodec_jni.so", "libkiteplayer_libass_jni.so",
]
EXO_ONLY_FORBIDDEN_PREFIXES = ["libmpv", "libmpvkt_jni", "libav", "libsw", "libkitecodec", "libkiteplayer", "libc++_shared"]

# KitePlayer's decoder, and the ABIs that KitePlayer does not build for. mpv and ExoPlayer still
# run there, and KitePlayer reports itself unavailable.
KITE_DECODER = "libkitecodec_jni.so"
ABIS_WITHOUT_KITE = {"x86"}

UNSIGNED = os.environ.get("SYNKPLAY_UNSIGNED_RELEASE", "").lower() == "true"

# The release build of the toolchain: a generated Gradle project with the app at :androidApp.
RELEASE_BUILD = "build/tasks/_androidApp_buildAndroidRelease"

# android:extractNativeLibs in a compiled manifest: its resource ID, and the binary XML types.
EXTRACT_NATIVE_LIBS_ID = 0x010104EA
RES_XML_START_ELEMENT = 0x0102
RES_XML_RESOURCE_MAP = 0x0180
TYPE_INT_BOOLEAN = 0x12

# The file in which the Android Gradle plugin records the git revision of the build.
VCS_INFO = "META-INF/version-control-info.textproto"

# The zip records that write_apk reads and writes: the local file header, the central directory
# record and the end of central directory record.
LOCAL_HEADER = struct.Struct("<IHHHHHIIIHH")
CENTRAL_HEADER = struct.Struct("<IHHHHHHIIIHHHHHII")
END_RECORD = struct.Struct("<IHHHHIIH")
# The flag of an entry whose sizes follow its data in a data descriptor.
DATA_DESCRIPTOR_FLAG = 0x0008
# The extra field that pads an entry to its alignment, as apksigner and the Android Gradle plugin
# write it: the ID, the size, the alignment, then zero bytes.
ALIGNMENT_EXTRA_ID = 0xD935
# Uncompressed native libraries start on a 16 KB page, and every other uncompressed entry on a
# multiple of 4 bytes. This is the layout of `zipalign -P 16 4`.
LIBRARY_ALIGNMENT = 16384
ENTRY_ALIGNMENT = 4
# A change function returns this to leave an entry out.
DROP = object()


def kotlin(*args: str, env: dict | None = None) -> str:
    """Runs the ./kotlin wrapper in the project root and returns its output."""
    command = [str(ROOT / ("kotlin.bat" if os.name == "nt" else "kotlin")), *args]
    print("> " + " ".join(command[1:]), flush=True)
    process = subprocess.run(
        command, cwd=ROOT, env={**os.environ, "KOTLIN_CLI_NO_WELCOME_BANNER": "1", **(env or {})},
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    print(process.stdout, end="", flush=True)
    if process.returncode != 0:
        sys.exit(f"android_release: `./kotlin {' '.join(args)}` failed with exit code {process.returncode}")
    return process.stdout


def release_identity() -> dict:
    out = kotlin("do", "printReleaseIdentity")
    # The toolchain puts `[module][task]` before each line that a build plugin prints.
    identity = dict(re.findall(r"^(?:\[[^\]]*\])* *([A-Z_]+)=(\S+)$", out, re.MULTILINE))
    for key in ("VERSION", "VERSION_CODE", "APPLICATION_ID"):
        if key not in identity:
            sys.exit(f"android_release: printReleaseIdentity printed no {key}")
    return identity


def build_tool(name: str) -> Path | None:
    """The newest Android SDK build-tools copy of [name], or None."""
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not (Path(sdk) / "build-tools").is_dir():
        return None
    def version(path: Path):
        return [int(p) if p.isdigit() else 0 for p in re.split(r"[.-]", path.name)]
    for folder in sorted((Path(sdk) / "build-tools").iterdir(), key=version, reverse=True):
        tool = folder / (name + (".bat" if os.name == "nt" and name == "apksigner" else ""))
        if tool.exists():
            return tool
    return None


def ndk_tool(name: str) -> Path | None:
    """[name] from the NDK in ANDROID_NDK_VERSION, else from the newest installed NDK, or None."""
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not (Path(sdk) / "ndk").is_dir():
        return None
    versions = [d for d in (Path(sdk) / "ndk").iterdir() if d.is_dir() and re.fullmatch(r"[0-9.]+", d.name)]
    wanted = os.environ.get("ANDROID_NDK_VERSION")
    if wanted:
        versions = [d for d in versions if d.name == wanted]
    for ndk in sorted(versions, key=lambda d: [int(p) for p in d.name.split(".") if p], reverse=True):
        found = sorted(ndk.glob(f"toolchains/llvm/prebuilt/*/bin/{name}{'.exe' if os.name == 'nt' else ''}"))
        if found:
            return found[0]
    return None


def only_file(pattern: str) -> Path:
    found = sorted(ROOT.glob(pattern), key=lambda p: p.stat().st_mtime)
    if not found:
        sys.exit(f"android_release: no file matches {pattern}")
    return found[-1]


def native_libraries(apk: Path) -> set[str]:
    with zipfile.ZipFile(apk) as zf:
        return {n for n in zf.namelist() if n.startswith("lib/") and n.endswith(".so")}


def check_full_decoders(apk: Path) -> None:
    libs = native_libraries(apk)
    abis = sorted({name.split("/")[1] for name in libs})
    gaps = [abi for abi in abis if abi not in ABIS_WITHOUT_KITE and f"lib/{abi}/{KITE_DECODER}" not in libs]
    if gaps:
        sys.exit(f"android_release: KitePlayer cannot start on these ABIs of {apk.name}: {', '.join(gaps)}")
    print(f"{apk.name}: KitePlayer's decoder is in every ABI that it supports.")


def check_exo_only(apk: Path) -> None:
    offenders = [n for n in native_libraries(apk) if any(n.rsplit("/", 1)[-1].startswith(p) for p in EXO_ONLY_FORBIDDEN_PREFIXES)]
    if offenders:
        sys.exit(f"android_release: exoOnly must ship no native player library, but {apk.name} has: {', '.join(sorted(offenders))}")
    print(f"{apk.name}: no native player library.")


def revision_record() -> bytes | None:
    """The git revision record that the Android Gradle plugin writes into a release APK, or None.

    The toolchain builds the APK in a generated project that has no git checkout, so its APK
    records no revision. The two copy functions below put the record back.
    """
    try:
        revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, stdout=subprocess.PIPE,
                                  stderr=subprocess.DEVNULL, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        return None
    return f'repositories {{\n  system: GIT\n  local_root_path: "$PROJECT_DIR"\n  revision: "{revision}"\n}}\n'.encode()


def is_v1_signature(name: str) -> bool:
    """True for a v1 signature file of the toolchain build. The new signature has no v1 part."""
    return name.startswith("META-INF/") and re.search(r"\.(SF|RSA|DSA|EC|MF)$", name) is not None


def central_directory(apk: bytes) -> list[tuple[bytes, str]]:
    """The central directory records of [apk] in their order, each with the name of its entry."""
    end = apk.rfind(b"PK\x05\x06", max(0, len(apk) - 65557))
    if end < 0:
        sys.exit("android_release: the APK has no end of central directory record")
    _, _, _, _, count, _, position, _ = END_RECORD.unpack_from(apk, end)
    if count == 0xFFFF or position == 0xFFFFFFFF:
        sys.exit("android_release: the APK is a zip64 archive, which this script cannot copy")
    records = []
    for _ in range(count):
        fields = CENTRAL_HEADER.unpack_from(apk, position)
        if fields[0] != 0x02014B50:
            sys.exit("android_release: the central directory of the APK is damaged")
        size = CENTRAL_HEADER.size + fields[10] + fields[11] + fields[12]
        name = apk[position + CENTRAL_HEADER.size: position + CENTRAL_HEADER.size + fields[10]]
        records.append((apk[position: position + size], name.decode("utf-8" if fields[3] & 0x800 else "cp437")))
        position += size
    return records


def without_alignment(extra: bytes) -> bytes:
    """[extra] without the padding of an earlier alignment: alignment fields and zipalign's zero bytes."""
    kept, position = b"", 0
    while position + 4 <= len(extra):
        header_id, size = struct.unpack_from("<HH", extra, position)
        if position + 4 + size > len(extra):
            break
        if header_id not in (ALIGNMENT_EXTRA_ID, 0):
            kept += extra[position: position + 4 + size]
        position += 4 + size
    return kept


def deflate(content: bytes) -> bytes:
    compressor = zlib.compressobj(zlib.Z_DEFAULT_COMPRESSION, zlib.DEFLATED, -15)
    return compressor.compress(content) + compressor.flush()


def write_apk(source: Path, target: Path, change) -> None:
    """Writes the entries of [source] to [target], aligned as `zipalign -P 16 4` aligns them.

    [change] gets the name, the compression method and a function that reads the content of each
    entry. It returns None to copy the entry, DROP to leave it out, or (content, method) for new
    content. A copied entry keeps its compressed bytes, so the result depends on no zlib and no
    zipalign of the machine. The APK Signing Block of [source] is not copied.
    """
    apk = source.read_bytes()
    out, central, count = bytearray(), bytearray(), 0
    with zipfile.ZipFile(source) as zf:
        for record, name in central_directory(apk):
            fields = list(CENTRAL_HEADER.unpack_from(record))
            result = change(name, fields[4], lambda: zf.read(name))
            if result is DROP:
                continue
            local = list(LOCAL_HEADER.unpack_from(apk, fields[16]))
            if local[0] != 0x04034B50:
                sys.exit(f"android_release: the local header of {name} is damaged")
            name_start = fields[16] + LOCAL_HEADER.size
            extra_start = name_start + local[9]
            extra = without_alignment(apk[extra_start: extra_start + local[10]])
            if result is None:
                data_start = extra_start + local[10]
                data = apk[data_start: data_start + fields[8]]
            else:
                content, method = result
                data = content if method == zipfile.ZIP_STORED else deflate(content)
                fields[4], fields[7], fields[8], fields[9] = method, zlib.crc32(content), len(data), len(content)
                if method != zipfile.ZIP_STORED:
                    fields[2] = local[1] = max(local[1], 20)
            # The local header gets the sizes, so no entry needs a data descriptor.
            fields[3] &= ~DATA_DESCRIPTOR_FLAG
            local[2] &= ~DATA_DESCRIPTOR_FLAG
            if fields[4] == zipfile.ZIP_STORED:
                alignment = LIBRARY_ALIGNMENT if name.endswith(".so") else ENTRY_ALIGNMENT
                padding = -(len(out) + LOCAL_HEADER.size + local[9] + len(extra) + 6) % alignment
                extra += struct.pack("<HHH", ALIGNMENT_EXTRA_ID, 2 + padding, alignment) + bytes(padding)
            local[3], local[6], local[7], local[8], local[10] = fields[4], fields[7], fields[8], fields[9], len(extra)
            fields[16] = len(out)
            out += LOCAL_HEADER.pack(*local) + apk[name_start: extra_start] + extra + data
            central += CENTRAL_HEADER.pack(*fields) + record[CENTRAL_HEADER.size:]
            count += 1
    central_start = len(out)
    out += central + END_RECORD.pack(0x06054B50, 0, 0, count, count, len(central), central_start, 0)
    target.write_bytes(out)


def merged_libraries() -> dict[str, bytes]:
    """The native libraries of the release build before the Android Gradle plugin strips them, by entry name."""
    found = {}
    for library in sorted(ROOT.glob(f"{RELEASE_BUILD}/**/intermediates/merged_native_libs/release/**/lib/*/*.so")):
        name, content = f"lib/{library.parent.name}/{library.name}", library.read_bytes()
        if found.setdefault(name, content) != content:
            sys.exit(f"android_release: the release build has two different merged copies of {name}")
    return found


def strip_libraries(source: Path, target: Path) -> None:
    """Writes [target] from [source] without the exoOnly libraries and without the old signature.

    The other native libraries go in as their dependencies ship them. The Android Gradle plugin
    strips them only when its default NDK is installed, so a stripped copy would make the APK
    depend on the NDK of the machine.
    """
    revision = revision_record()
    originals = merged_libraries()

    def change(name: str, method: int, read):
        if is_v1_signature(name):
            return DROP
        if name == VCS_INFO and revision is not None:
            return revision, zipfile.ZIP_STORED
        if name.startswith("lib/") and name.endswith(".so"):
            if name.rsplit("/", 1)[-1] in EXO_ONLY_DROPPED:
                return DROP
            if name not in originals:
                sys.exit(f"android_release: the release build has no merged copy of {name}")
            return originals[name], zipfile.ZIP_STORED
        return None

    write_apk(source, target, change)


def set_extract_native_libs(manifest: bytes) -> bytes:
    """Sets android:extractNativeLibs to true in a compiled (binary XML) AndroidManifest.xml."""
    data = bytearray(manifest)
    resource_ids: tuple = ()
    changed = 0
    # Each chunk starts with its type, its header size and its total size.
    offset = struct.unpack_from("<H", data, 2)[0]
    while offset < len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        if chunk_type == RES_XML_RESOURCE_MAP:
            resource_ids = struct.unpack_from(f"<{(chunk_size - header_size) // 4}I", data, offset + header_size)
        elif chunk_type == RES_XML_START_ELEMENT:
            element = offset + header_size
            attribute_start, attribute_size, attribute_count = struct.unpack_from("<HHH", data, element + 8)
            for index in range(attribute_count):
                attribute = element + attribute_start + index * attribute_size
                name = struct.unpack_from("<I", data, attribute + 4)[0]
                if name < len(resource_ids) and resource_ids[name] == EXTRACT_NATIVE_LIBS_ID:
                    if data[attribute + 15] != TYPE_INT_BOOLEAN:
                        sys.exit("android_release: android:extractNativeLibs in the manifest is not a boolean")
                    struct.pack_into("<I", data, attribute + 16, 0xFFFFFFFF)
                    changed += 1
        offset += chunk_size
    if changed != 1:
        sys.exit(f"android_release: expected one android:extractNativeLibs in the manifest, found {changed}")
    return bytes(data)


def compress_libraries(source: Path, target: Path) -> None:
    """Copies [source] to [target] with compressed native libraries and without the old signature.

    The toolchain stores the native libraries uncompressed and has no setting to change that. The
    copy compresses them and sets android:extractNativeLibs to true, so Android unpacks them at
    install time. This makes the universal APK much smaller to download.
    """
    revision = revision_record()

    def change(name: str, method: int, read):
        if is_v1_signature(name):
            return DROP
        if name == VCS_INFO and revision is not None:
            return revision, zipfile.ZIP_STORED
        if name == "AndroidManifest.xml":
            return set_extract_native_libs(read()), method
        if name.startswith("lib/") and name.endswith(".so"):
            return read(), zipfile.ZIP_DEFLATED
        return None

    write_apk(source, target, change)


def rewrite_apk(source: Path, target: Path, copy) -> None:
    """Writes [target] from [source] with [copy], checks its alignment, and signs it unless the release is unsigned."""
    zipalign = build_tool("zipalign")
    if zipalign is None:
        sys.exit("android_release: zipalign was not found. Set ANDROID_HOME.")
    copy(source, target)
    # zipalign only checks the layout. write_apk aligned the entries itself.
    result = subprocess.run([str(zipalign), "-c", "-P", "16", "4", str(target)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if result.returncode != 0:
        sys.exit(f"android_release: {target.name} is not aligned:\n{result.stdout[-2000:]}")
    if not UNSIGNED:
        sign(target)


def check_compressed_libraries(apk: Path) -> None:
    aapt2 = build_tool("aapt2")
    if aapt2 is not None:
        tree = subprocess.run([str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)],
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout
        if not re.search(r":extractNativeLibs\(0x010104ea\)=true", tree):
            sys.exit(f"android_release: {apk.name} does not set android:extractNativeLibs to true")
    with zipfile.ZipFile(apk) as zf:
        stored = [i.filename for i in zf.infolist() if i.filename.startswith("lib/") and i.compress_type != zipfile.ZIP_DEFLATED]
    if stored:
        sys.exit(f"android_release: {apk.name} has uncompressed native libraries: {', '.join(stored[:5])}")
    print(f"{apk.name}: native libraries compressed, extractNativeLibs is true.")


def read_signing_properties() -> dict:
    """The four signing keys from keystore.properties. The values are passed on and never printed."""
    values = {}
    for line in KEYSTORE_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    missing = [k for k in ("storeFile", "storePassword", "keyAlias", "keyPassword") if not values.get(k)]
    if missing:
        sys.exit(f"android_release: keystore.properties has no {', '.join(missing)}")
    return values


def sign(apk: Path) -> None:
    apksigner = build_tool("apksigner")
    if apksigner is None:
        sys.exit("android_release: apksigner was not found. Set ANDROID_HOME.")
    props = read_signing_properties()
    keystore = (ROOT / "androidApp" / props["storeFile"]).resolve()
    env = {**os.environ, "SYNKPLAY_KS_PASS": props["storePassword"], "SYNKPLAY_KEY_PASS": props["keyPassword"]}
    # The same signature as the old Gradle build: scheme v2 only, and the entry layout kept. The
    # signed APK is then the unsigned APK plus zero padding and the APK Signing Block, and
    # apksigcopier can copy the signature onto a rebuilt unsigned APK. Without these flags, apksigner adds v1 signature files,
    # realigns every stored entry and writes an .idsig file next to the APK.
    result = subprocess.run(
        [str(apksigner), "sign", "--alignment-preserved", "true", "--v1-signing-enabled", "false",
         "--v2-signing-enabled", "true", "--v3-signing-enabled", "false", "--v4-signing-enabled", "false",
         "--ks", str(keystore), "--ks-key-alias", props["keyAlias"],
         "--ks-pass", "env:SYNKPLAY_KS_PASS", "--key-pass", "env:SYNKPLAY_KEY_PASS", str(apk)],
        env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"android_release: apksigner could not sign {apk.name}:\n{result.stdout}")


def verify(apk: Path, version: str, application_id: str, allow_unverified: bool) -> None:
    apksigner, aapt2 = build_tool("apksigner"), build_tool("aapt2")
    if apksigner is None or aapt2 is None:
        if allow_unverified:
            print(f"WARNING: {apk.name} is NOT verified: apksigner or aapt2 is missing.")
            return
        sys.exit("android_release: apksigner or aapt2 was not found. Set ANDROID_HOME, or pass --allow-unverified.")
    badging = subprocess.run([str(aapt2), "dump", "badging", str(apk)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True).stdout
    name = re.search(r"package: name='([^']*)'", badging)
    embedded = re.search(r"versionName='([^']*)'", badging)
    if not name or name.group(1) != application_id:
        sys.exit(f"android_release: {apk.name} has the package {name and name.group(1)}, not {application_id}")
    if not embedded or embedded.group(1) != version:
        sys.exit(f"android_release: {apk.name} carries version {embedded and embedded.group(1)}, not {version}")
    if UNSIGNED:
        print(f"{apk.name}: {application_id} {version}, unsigned on purpose.")
        return
    result = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if result.returncode != 0:
        sys.exit(f"android_release: {apk.name} failed signature verification:\n{result.stdout}")
    digests = {line.split("digest:", 1)[1].strip().lower() for line in result.stdout.splitlines() if "certificate SHA-256 digest:" in line}
    if digests != {RELEASE_CERT_SHA256}:
        sys.exit(f"android_release: {apk.name} is not signed with the release certificate. Found: {digests}")
    print(f"{apk.name}: {application_id} {version}, signed with the release certificate.")


def check_api_level(apk: Path, mapping: Path, allow_unverified: bool) -> None:
    """Fails on a Java API call above minSdk and on a Play services or Tink class (apk_api_check.py)."""
    if build_tool("dexdump") is None or build_tool("aapt2") is None:
        if allow_unverified:
            print(f"WARNING: the API levels of {apk.name} are NOT checked: dexdump or aapt2 is missing.")
            return
        sys.exit("android_release: dexdump or aapt2 was not found. Set ANDROID_HOME, or pass --allow-unverified.")
    findings = apk_api_check.check(str(apk), str(mapping))
    if findings:
        sys.exit(f"android_release: {apk.name} fails the API level check:\n" + "\n".join(f"  {f}" for f in findings))
    print(f"{apk.name}: no Java API call above minSdk outside guarded code, and no forbidden library.")


def write_symbols(target: Path) -> list[Path]:
    """Copies the R8 mapping of the release build and writes its native debug symbols into [target].

    The toolchain does not set ndk.debugSymbolLevel, so its build writes no native-debug-symbols.zip.
    This function writes the file as debugSymbolLevel FULL did: the debug sections of each native
    library before stripping, as <abi>/<library>.dbg, extracted with the NDK's llvm-objcopy.
    """
    target.mkdir(parents=True, exist_ok=True)
    mapping = target / "mapping.txt"
    shutil.copyfile(only_file(f"{RELEASE_BUILD}/**/outputs/mapping/release/mapping.txt"), mapping)
    libraries = sorted(ROOT.glob(f"{RELEASE_BUILD}/**/intermediates/merged_native_libs/release/**/lib/*/*.so"))
    if not libraries:
        sys.exit("android_release: the release build has no merged native libraries")
    objcopy = ndk_tool("llvm-objcopy")
    if objcopy is None:
        print("WARNING: no NDK found under ANDROID_HOME, so the native debug symbols are NOT written.")
        return [mapping]
    symbols = target / "native-debug-symbols.zip"
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(symbols, "w", zipfile.ZIP_DEFLATED) as zout:
        for library in libraries:
            abi = library.parent.name
            extracted = Path(tmp) / f"{abi}-{library.name}.dbg"
            subprocess.run([str(objcopy), "--only-keep-debug", str(library), str(extracted)], check=True)
            zout.write(extracted, f"{abi}/{library.name}.dbg")
    print(f"{symbols.name}: debug sections of {len(libraries)} native libraries.")
    return [mapping, symbols]


def build_full(version: str, args) -> list[Path]:
    kotlin("build", "-m", "androidApp", "-p", "android", "-v", "release")
    apk = only_file(f"{RELEASE_BUILD}/*.apk")
    # Read the symbols now: the exoOnly build writes into the same folders.
    symbols = write_symbols(OUTPUT / "symbols")
    kotlin("package", "-m", "androidApp", "-p", "android", "-v", "release", "-f", "aab")
    aab = only_file("build/tasks/_androidApp_*/**/*.aab")
    suffix = "-unsigned" if UNSIGNED else ""
    out_apk = OUTPUT / f"synkplay-{version}-full-universal{suffix}.apk"
    out_aab = OUTPUT / f"synkplay-{version}-full{suffix}.aab"
    rewrite_apk(apk, out_apk, compress_libraries)
    shutil.copyfile(aab, out_aab)
    check_compressed_libraries(out_apk)
    check_full_decoders(out_apk)
    check_api_level(out_apk, OUTPUT / "symbols" / "mapping.txt", args.allow_unverified)
    verify(out_apk, version, FULL_APPLICATION_ID, args.allow_unverified)
    return [out_apk, out_aab, *symbols]


def build_exo_only(version: str, args) -> list[Path]:
    original = APP_MODULE.read_text(encoding="utf-8")
    line = f"    applicationId: {FULL_APPLICATION_ID}\n"
    if original.count(line) != 1:
        sys.exit(f"android_release: androidApp/module.yaml must have exactly one line `{line.strip()}`")
    try:
        # The exoOnly app has its own application ID, and KiteBuildConfig.EXOPLAYER_ONLY is true.
        APP_MODULE.write_text(original.replace(line, f"    applicationId: {EXO_APPLICATION_ID}\n"), encoding="utf-8")
        kotlin("build", "-m", "androidApp", "-p", "android", "-v", "release", env={"SYNKPLAY_EXO_ONLY": "true"})
        apk = only_file(f"{RELEASE_BUILD}/*.apk")
        mapping = only_file(f"{RELEASE_BUILD}/**/outputs/mapping/release/mapping.txt")
        suffix = "-unsigned" if UNSIGNED else ""
        out_apk = OUTPUT / f"syncplay-{version}-exo-only{suffix}.apk"
        # The exoOnly APK keeps its few native libraries uncompressed. Compression would depend on
        # the zlib of the machine, and others rebuild this APK byte for byte.
        rewrite_apk(apk, out_apk, strip_libraries)
    finally:
        APP_MODULE.write_text(original, encoding="utf-8")
    check_exo_only(out_apk)
    check_api_level(out_apk, mapping, args.allow_unverified)
    verify(out_apk, version, EXO_APPLICATION_ID, args.allow_unverified)
    return [out_apk]


def main() -> None:
    parser = argparse.ArgumentParser(description="Builds the Android release files into AndroidAppOutput/.")
    which = parser.add_mutually_exclusive_group()
    which.add_argument("--exo-only", action="store_true", help="build only the exoOnly APK")
    which.add_argument("--full-only", action="store_true", help="build only the full APK and the AAB")
    parser.add_argument("--allow-unverified", action="store_true", help="skip the apksigner and aapt2 checks when the SDK tools are missing")
    parser.add_argument("--output", type=Path, help="the output folder, AndroidAppOutput/ when not set. The script deletes it first.")
    args = parser.parse_args()
    global OUTPUT
    if args.output:
        OUTPUT = args.output.resolve()

    if not UNSIGNED and not KEYSTORE_PROPERTIES.is_file():
        sys.exit("android_release: keystore.properties is missing at the project root. "
                 "Create it, or set SYNKPLAY_UNSIGNED_RELEASE=true for unsigned files.")
    version = release_identity()["VERSION"]
    # Start from an empty folder, so no file of an older version gets uploaded.
    shutil.rmtree(OUTPUT, ignore_errors=True)
    OUTPUT.mkdir()
    produced = []
    if not args.exo_only:
        produced += build_full(version, args)
    if not args.full_only:
        produced += build_exo_only(version, args)
    print(f"android_release: {len(produced)} file(s) in {OUTPUT}:")
    for path in produced:
        print(f"    {path.relative_to(OUTPUT)}  ({path.stat().st_size // 1_000_000} MB)")


if __name__ == "__main__":
    main()
