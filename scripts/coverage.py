#!/usr/bin/env python3
"""Runs the desktop (JVM) tests of the shared module with coverage, and checks the coverage floor.

The coverage covers only the two packages where a gap is a real risk: app.protocol (the Syncplay
protocol) and app.server (the built-in server). The script:

1. downloads the Kover JVM agent and the Kover command line tool from Maven Central once, into
   build/coverage/tools, and checks their SHA-256,
2. runs `./kotlin test -m shared -p jvm` with the agent,
3. writes an XML and an HTML report into build/coverage,
4. fails when the line coverage is below COVERAGE_FLOOR.

Usage:
  python3 scripts/coverage.py                 run the tests, then check the floor
  python3 scripts/coverage.py --report-only   check the floor of the last run again
  python3 scripts/coverage.py -- <arguments>  pass more arguments to `./kotlin test`

The Kotlin Toolchain has no coverage plugin, so this script does what the Kover Gradle plugin did.
"""

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "coverage"
TOOLS = OUT / "tools"
BINARY_REPORT = OUT / "shared-jvm.ic"

# The floor for app.protocol and app.server, set just under what a clean full run measures (24.9
# percent). Raise it when new tests add coverage. Never lower it to make a build pass.
COVERAGE_FLOOR = 24

INCLUDES = ["app.protocol.*", "app.server.*"]
# Generated classes: serializers, Compose singletons and factories.
EXCLUDES = ["*$$serializer", "*ComposableSingletons*", "*_Factory*"]

KOVER_VERSION = "0.9.11"
# The agent jar keeps its Maven file name: its manifest loads itself by that name (Boot-Class-Path).
TOOL_JARS = {
    "kover-jvm-agent": "f1f861d50ccffd207ad6b4472da038d27961a396c5674ba7f850c01a15287bc0",
    "kover-cli": "6889035c98fd91484f0d37e799d73a58322a64bdcba446a2cc3a6cdaf7ff6d56",
}

CLASSES = ROOT / "build" / "artifacts" / "CompiledJvmArtifact" / "sharedjvm" / "kotlin-output"
SOURCES = [ROOT / "shared" / name for name in ("src", "src@nonWeb", "src@jvmShared", "src@jvm")]


def tool(name: str) -> Path:
    """The jar of [name], downloaded once and checked against its pinned SHA-256."""
    jar = TOOLS / f"{name}-{KOVER_VERSION}.jar"
    if not jar.is_file():
        TOOLS.mkdir(parents=True, exist_ok=True)
        url = f"https://repo1.maven.org/maven2/org/jetbrains/kotlinx/{name}/{KOVER_VERSION}/{jar.name}"
        print(f"coverage: downloading {url}", flush=True)
        partial = jar.with_suffix(".part")
        with urllib.request.urlopen(url) as response, open(partial, "wb") as out:
            shutil.copyfileobj(response, out)
        partial.rename(jar)
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != TOOL_JARS[name]:
        jar.unlink()
        sys.exit(f"coverage: {jar.name} has the SHA-256 {digest}, not {TOOL_JARS[name]}. The file was deleted.")
    return jar


def java() -> str:
    """A java launcher: JAVA_HOME, then the PATH, then a JDK that the toolchain downloaded."""
    exe = "java.exe" if os.name == "nt" else "java"
    if os.environ.get("JAVA_HOME") and (Path(os.environ["JAVA_HOME"]) / "bin" / exe).is_file():
        return str(Path(os.environ["JAVA_HOME"]) / "bin" / exe)
    if shutil.which("java"):
        return shutil.which("java")
    home = Path.home()
    for cache in (home / "Library/Caches/JetBrains/Kotlin", home / ".cache/JetBrains/Kotlin",
                  Path(os.environ.get("LOCALAPPDATA", home)) / "JetBrains/Kotlin"):
        found = sorted((cache / "extract.cache").glob(f"*/*/**/bin/{exe}")) if (cache / "extract.cache").is_dir() else []
        if found:
            return str(found[0])
    sys.exit("coverage: no java found. Set JAVA_HOME, or run ./kotlin build once.")


def agent_argument(agent: Path) -> str:
    """The -javaagent option, with the filters in a file next to the binary report."""
    settings = OUT / "agent.args"
    lines = [f"report.file={BINARY_REPORT}", "report.append=true"]
    lines += [f"include={pattern}" for pattern in INCLUDES]
    lines += [f"exclude={pattern}" for pattern in EXCLUDES]
    settings.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return f"-javaagent:{agent}=file:{settings}"


def run_tests(extra: list[str]) -> None:
    agent = tool("kover-jvm-agent")
    OUT.mkdir(parents=True, exist_ok=True)
    BINARY_REPORT.unlink(missing_ok=True)
    wrapper = str(ROOT / ("kotlin.bat" if os.name == "nt" else "kotlin"))
    command = [wrapper, "test", "-m", "shared", "-p", "jvm", f"--jvm-args={agent_argument(agent)}", *extra]
    print("> " + " ".join(command[1:]), flush=True)
    result = subprocess.run(command, cwd=ROOT, env={**os.environ, "KOTLIN_CLI_NO_WELCOME_BANNER": "1"})
    if result.returncode != 0:
        sys.exit(f"coverage: the tests failed with exit code {result.returncode}")


def write_report() -> Path:
    if not BINARY_REPORT.is_file():
        sys.exit(f"coverage: {BINARY_REPORT} does not exist. Run the script without --report-only first.")
    if not CLASSES.is_dir():
        sys.exit(f"coverage: {CLASSES} does not exist. Build the shared module for jvm first.")
    xml_report = OUT / "report.xml"
    command = [java(), "-jar", str(tool("kover-cli")), "report", str(BINARY_REPORT),
               "--classfiles", str(CLASSES), "--xml", str(xml_report), "--html", str(OUT / "html"),
               "--title", "Synkplay protocol and server"]
    for source in SOURCES:
        command += ["--src", str(source)]
    for pattern in INCLUDES:
        command += ["--include", pattern]
    for pattern in EXCLUDES:
        command += ["--exclude", pattern]
    subprocess.run(command, check=True)
    return xml_report


def line_coverage(xml_report: Path) -> tuple[int, int]:
    """The covered and the missed lines of the whole report."""
    root = ET.parse(xml_report).getroot()
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            return int(counter.get("covered")), int(counter.get("missed"))
    sys.exit(f"coverage: {xml_report} has no LINE counter")


def main() -> None:
    parser = argparse.ArgumentParser(description="Runs the JVM tests with coverage and checks the floor.")
    parser.add_argument("--report-only", action="store_true", help="skip the test run and check the last binary report")
    parser.add_argument("--floor", type=float, default=COVERAGE_FLOOR, help=f"the minimum line coverage in percent (default {COVERAGE_FLOOR})")
    parser.add_argument("extra", nargs="*", help="more arguments for ./kotlin test, after --")
    args = parser.parse_args()
    if not args.report_only:
        run_tests(args.extra)
    xml_report = write_report()
    covered, missed = line_coverage(xml_report)
    total = covered + missed
    percent = 100.0 * covered / total if total else 0.0
    print(f"coverage: {covered} of {total} lines in {', '.join(INCLUDES)} ({percent:.2f} percent). "
          f"Report: {OUT / 'html' / 'index.html'}")
    if percent < args.floor:
        sys.exit(f"coverage: {percent:.2f} percent is below the floor of {args.floor:g} percent.")
    print(f"coverage: the floor of {args.floor:g} percent holds.")


if __name__ == "__main__":
    main()
