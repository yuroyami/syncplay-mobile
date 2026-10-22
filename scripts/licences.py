#!/usr/bin/env python3
"""Writes shared/composeResources/files/aboutlibraries.json, the data of the licences screen.

The file lists every Maven library that the shared module ships, per platform, with its version,
name, website and licences. The licences screen reads it with the AboutLibraries library.

Usage:
  python3 scripts/licences.py           write the file
  python3 scripts/licences.py --check   fail when the file is out of date

How it works:
  1. `./kotlin show dependencies` lists the runtime libraries of each platform.
  2. The POM file of each library, from the toolchain's download cache, gives its name, website and
     licences. A POM without them takes them from its parent POM. A POM that the toolchain did not
     download comes from Maven Central or another repository of the app.
  3. A platform artifact (for example kotlinx-coroutines-core-jvm) counts as its library
     (kotlinx-coroutines-core), as its Gradle module file says.
  4. shared/config/aboutlibraries/ holds the licence texts and the per-library fixes.

Run it after a dependency change, and commit the file with that change.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "shared" / "composeResources" / "files" / "aboutlibraries.json"
CONFIG = ROOT / "shared" / "config" / "aboutlibraries"
# POM files that the toolchain did not download (often a parent POM) come from these repositories.
# The script keeps them in DOWNLOADS, so each file is downloaded once.
DOWNLOADS = ROOT / "build" / "licences" / "poms"
REPOSITORIES = [
    "https://repo.maven.apache.org/maven2",
    "https://dl.google.com/dl/android/maven2",
    "https://yuroyami.github.io/maven",
    "https://jitpack.io",
]

# Target name in the file -> the platforms of `./kotlin show dependencies -m shared -p <platform>`.
# "metadata" is the code that the iOS app links: the common code and the iOS code. The iOS screen
# reads it.
TARGETS = {"android": ["android"], "desktop": ["jvm"], "wasmJs": ["wasmJs"], "metadata": ["common", "iosArm64"]}
# The toolchain prints an empty runtime graph for a native platform, so iOS uses its compile graph.
COMPILE_GRAPH = {"iosArm64"}

# A library whose licence text is not one of the known licences fails the run. Add the text to
# shared/config/aboutlibraries/licenses/, or a fix to shared/config/aboutlibraries/libraries/.
SPDX_RULES = [
    ("Apache-2.0", [r"apache"]),
    ("MIT", [r"\bmit\b", r"licenses/mit"]),
    ("BSD-3-Clause", [r"bsd[ -]?3", r"3-clause", r"new bsd", r"revised bsd", r"edl-v10", r"eclipse distribution license"]),
    ("BSD-2-Clause", [r"bsd[ -]?2", r"2-clause", r"simplified bsd"]),
    ("MPL-2.0", [r"mozilla public license,? (version )?2", r"mpl[ -]?2"]),
    ("LGPL-2.1-or-later", [r"lesser general public license.*2\.1", r"lgpl[ -]?(v)?2\.1"]),
    ("GPL-3.0-or-later", [r"(?<!lesser )general public license.*\b3\b", r"\bgpl[ -]?(v)?3"]),
    ("GPL-2.0-or-later", [r"(?<!lesser )general public license.*\b2\b", r"\bgpl[ -]?(v)?2"]),
    ("ISC", [r"\bisc\b"]),
    ("Zlib", [r"\bzlib\b"]),
]

MAVEN_NS = "{http://maven.apache.org/POM/4.0.0}"


def kotlin(*args: str) -> str:
    command = [str(ROOT / ("kotlin.bat" if os.name == "nt" else "kotlin")), *args]
    process = subprocess.run(
        command, cwd=ROOT, env={**os.environ, "KOTLIN_CLI_NO_WELCOME_BANNER": "1"},
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
    )
    if process.returncode != 0:
        sys.exit(f"licences: `./kotlin {' '.join(args)}` failed:\n{process.stdout}")
    return process.stdout


def maven_cache() -> Path:
    candidates = [
        Path.home() / "Library/Caches/JetBrains/Kotlin/.m2.cache",
        Path.home() / ".cache/JetBrains/Kotlin/.m2.cache",
    ]
    if os.environ.get("LOCALAPPDATA"):
        candidates.append(Path(os.environ["LOCALAPPDATA"]) / "JetBrains/Kotlin/.m2.cache")
    for candidate in candidates:
        if candidate.is_dir():
            return candidate
    sys.exit("licences: the toolchain's download cache was not found. Build the app once first.")


# One node of the printed graph: `├─── group:artifact:version -> resolved (*)`. A module root has more
# parts (`shared:android:group:artifact:version`), and `(c)` marks a version constraint.
NODE = re.compile(r"\u2500\u2500\u2500 (\S+)(?: -> (\S+))?(?: \((c|\*)\))?")


def parse_libraries(out: str) -> set[tuple[str, str, str]]:
    found = set()
    for line in out.splitlines():
        match = NODE.search(line)
        if not match or match.group(3) == "c":
            continue
        parts = match.group(1).split(":")
        if len(parts) == 3:
            found.add((parts[0], parts[1], match.group(2) or parts[2]))
    return found


def runtime_libraries(platform: str) -> set[tuple[str, str, str]]:
    """(group, artifact, version) of every Maven library in the runtime graph of [platform]."""
    scope = "compile" if platform in COMPILE_GRAPH else "runtime"
    return parse_libraries(kotlin("show", "dependencies", "-m", "shared", "-p", platform, "--scope", scope))


class Poms:
    def __init__(self, cache: Path):
        self.cache = cache
        self.parsed: dict[tuple[str, str, str], ET.Element | None] = {}

    def path(self, group: str, artifact: str, version: str, extension: str) -> Path:
        return self.cache / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.{extension}"

    def pom(self, group: str, artifact: str, version: str) -> ET.Element | None:
        key = (group, artifact, version)
        if key not in self.parsed:
            file = self.path(group, artifact, version, "pom")
            if not file.is_file():
                file = download_pom(group, artifact, version)
            self.parsed[key] = ET.parse(file).getroot() if file else None
        return self.parsed[key]

    def root_component(self, group: str, artifact: str, version: str) -> tuple[str, str, str]:
        """The library that a platform artifact belongs to, from its Gradle module file."""
        file = self.path(group, artifact, version, "module")
        if file.is_file():
            component = json.loads(file.read_text(encoding="utf-8")).get("component", {})
            if "url" in component and component.get("module"):
                return component["group"], component["module"], component.get("version", version)
        return group, artifact, version

    def field(self, group: str, artifact: str, version: str, read):
        """[read] of the POM, or of the nearest parent POM that has it."""
        seen = set()
        while (group, artifact, version) not in seen:
            seen.add((group, artifact, version))
            pom = self.pom(group, artifact, version)
            if pom is None:
                return None
            value = read(pom)
            if value:
                return value
            parent = pom.find(f"{MAVEN_NS}parent")
            if parent is None:
                return None
            group, artifact, version = (text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"))
        return None

    def website(self, group: str, artifact: str, version: str) -> str | None:
        """The project URL. Like Maven, an inherited URL gets the path of each child artifact appended."""
        suffix = ""
        seen = set()
        while (group, artifact, version) not in seen:
            seen.add((group, artifact, version))
            pom = self.pom(group, artifact, version)
            if pom is None:
                return None
            url = pom.find(f"{MAVEN_NS}url")
            if url is not None and url.text and url.text.strip():
                base = url.text.strip()
                if not suffix or url.get("child.project.url.inherit.append.path") == "false":
                    return base
                # Maven keeps the trailing slash of the parent URL.
                return base.rstrip("/") + suffix + ("/" if base.endswith("/") else "")
            parent = pom.find(f"{MAVEN_NS}parent")
            if parent is None:
                return None
            suffix = f"/{artifact}{suffix}"
            group, artifact, version = (text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"))
        return None


def download_pom(group: str, artifact: str, version: str) -> Path | None:
    relative = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    target = DOWNLOADS / relative
    if target.is_file():
        return target
    for repository in REPOSITORIES:
        try:
            with urllib.request.urlopen(f"{repository}/{relative}", timeout=30) as response:
                data = response.read()
        except OSError:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        return target
    return None


def interpolate(value: str | None, group: str, artifact: str, version: str) -> str | None:
    """Replaces the project properties that a POM can use in its name or URL, like Maven does."""
    if not value:
        return value
    for prefix in ("project.", "pom.", ""):
        value = (value.replace(f"${{{prefix}groupId}}", group).replace(f"${{{prefix}artifactId}}", artifact)
                 .replace(f"${{{prefix}version}}", version))
    return value


def text(element: ET.Element, tag: str) -> str | None:
    child = element.find(f"{MAVEN_NS}{tag}")
    return child.text.strip() if child is not None and child.text and child.text.strip() else None


def pom_licences(pom: ET.Element) -> list[tuple[str, str]]:
    licences = pom.find(f"{MAVEN_NS}licenses")
    if licences is None:
        return []
    return [(text(l, "name") or "", text(l, "url") or "") for l in licences.findall(f"{MAVEN_NS}license")]


def load_config() -> tuple[dict, dict]:
    licences = {}
    for file in sorted((CONFIG / "licenses").glob("*.json")):
        data = json.loads(file.read_text(encoding="utf-8"))
        licences[data["hash"]] = data
    fixes = {}
    for file in sorted((CONFIG / "libraries").glob("*.json")):
        data = json.loads(file.read_text(encoding="utf-8"))
        fixes[data["uniqueId"]] = data
    return licences, fixes


def licence_id(name: str, url: str, config: dict, known: dict) -> str | None:
    """The id of a POM licence: a configured licence, an SPDX id, a known hash, or None."""
    for key, entry in config.items():
        if entry.get("name") == name and entry.get("url") == url:
            return key
    if (name, url) in known:
        return known[(name, url)]
    probe = f"{name} {url}".lower()
    for spdx, patterns in SPDX_RULES:
        if any(re.search(p, probe) for p in patterns):
            return spdx
    return None


def build() -> str:
    config, fixes = load_config()
    previous = json.loads(OUTPUT.read_text(encoding="utf-8")) if OUTPUT.is_file() else {"licenses": {}}
    # Keep the ids of licences that only a POM names, so the file does not change without a reason.
    known = {(v.get("name"), v.get("url")): k for k, v in previous.get("licenses", {}).items() if k not in config}
    poms = Poms(maven_cache())

    libraries: dict[str, dict] = {}
    pom_only: dict[str, dict] = {}
    problems = []
    for target, platforms in TARGETS.items():
        found = sorted(set().union(*(runtime_libraries(p) for p in platforms)))
        # A platform artifact goes first, so its POM describes the library (see below).
        mapped = sorted(((poms.root_component(*c), c) for c in found), key=lambda m: (m[0] == m[1], m[1]))
        for (group, artifact, version), coordinates in mapped:
            unique_id = f"{group}:{artifact}"
            entry = libraries.get(unique_id)
            if entry is None:
                # The POM of the platform artifact (for example skiko-awt) gives the name, the website
                # and the licences. The POM of the library is the fallback.
                source = coordinates if poms.pom(*coordinates) is not None else (group, artifact, version)
                if poms.pom(*source) is None:
                    problems.append(f"{unique_id}:{version}: no POM in the toolchain cache or in a repository")
                    continue
                name = interpolate(poms.field(*source, lambda p: text(p, "name")), *source) or artifact
                website = interpolate(poms.website(*source), *source)
                ids = []
                if unique_id in fixes and fixes[unique_id].get("licenses"):
                    ids = list(fixes[unique_id]["licenses"])
                else:
                    for lname, lurl in poms.field(*source, pom_licences) or []:
                        found = licence_id(lname, lurl, config, known)
                        if found is None:
                            found = hashlib.md5(f"{lname}{lurl}".encode()).hexdigest()
                            if (lname, lurl) not in known:
                                problems.append(f"{unique_id}: unknown licence '{lname}' ({lurl})")
                        if found not in config:
                            pom_only.setdefault(found, {"name": lname, "url": lurl, "hash": found})
                        if found not in ids:
                            ids.append(found)
                if not ids:
                    problems.append(f"{unique_id}:{version}: no licence in its POM or in a parent POM")
                entry ={"uniqueId": unique_id, "artifactVersion": version, "name": name}
                if website:
                    entry["website"] = website
                entry["licenses"] = ids
                entry["targets"] = []
                libraries[unique_id] = entry
            if target not in entry["targets"]:
                entry["targets"].append(target)

    if problems:
        sys.exit("licences: fix these first:\n  " + "\n  ".join(problems))
    for entry in libraries.values():
        entry["targets"].sort()

    licences = {}
    for key, data in config.items():
        out = {"name": data["name"], "url": data.get("url", "")}
        if "content" in data:
            out["content"] = data["content"]
        if not re.fullmatch(r"[0-9a-f]{32}", key):
            out["internalHash"] = key
        if "spdxId" in data:
            out["spdxId"] = data["spdxId"]
        out["hash"] = key
        licences[key] = out
    licences.update({k: v for k, v in pom_only.items() if k not in licences})
    document = {
        "libraries": [libraries[k] for k in sorted(libraries)],
        "licenses": {k: licences[k] for k in sorted(licences)},
    }
    return json.dumps(document, indent=4, ensure_ascii=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Writes the data file of the licences screen.")
    parser.add_argument("--check", action="store_true", help="fail when the committed file is out of date")
    args = parser.parse_args()
    text_out = build()
    current = OUTPUT.read_text(encoding="utf-8") if OUTPUT.is_file() else ""
    if args.check:
        if current != text_out:
            sys.exit(f"licences: {OUTPUT.relative_to(ROOT)} is out of date. Run `python3 scripts/licences.py`.")
        print(f"licences: {OUTPUT.relative_to(ROOT)} is up to date.")
        return
    if current != text_out:
        OUTPUT.write_text(text_out, encoding="utf-8")
        print(f"licences: wrote {OUTPUT.relative_to(ROOT)}.")
    else:
        print(f"licences: {OUTPUT.relative_to(ROOT)} is already up to date.")


if __name__ == "__main__":
    main()
