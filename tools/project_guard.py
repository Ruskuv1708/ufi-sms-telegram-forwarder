#!/usr/bin/env python3
"""Fail CI when release metadata or repository safety invariants drift."""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
MAX_GITHUB_FILE_BYTES = 95 * 1024 * 1024
SECRET_PATTERNS = {
    "Telegram bot token": re.compile(rb"(?<![A-Za-z0-9_])[0-9]{8,12}:[A-Za-z0-9_-]{30,}"),
    "private key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "GitHub token": re.compile(rb"(?<![A-Za-z0-9_])gh[pousr]_[A-Za-z0-9]{30,}"),
    "Google API key": re.compile(rb"(?<![A-Za-z0-9_])AIza[0-9A-Za-z_-]{35}"),
}
SENSITIVE_FILENAMES = {
    "google-services.json",
    "id_ed25519",
    "id_rsa",
    "telegram.json",
}
SENSITIVE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pk8"}


class GuardError(RuntimeError):
    pass


def tracked_files() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
        check=True,
        stdout=subprocess.PIPE,
    )
    return [ROOT / item.decode("utf-8") for item in result.stdout.split(b"\0") if item]


def first_match(path: Path, pattern: re.Pattern[bytes]) -> int | None:
    try:
        content = path.read_bytes()
    except OSError as exc:
        raise GuardError(f"cannot read tracked file {path.relative_to(ROOT)}: {exc}") from exc
    match = pattern.search(content)
    return None if match is None else content.count(b"\n", 0, match.start()) + 1


def check_secrets_and_sizes(files: list[Path]) -> list[str]:
    failures: list[str] = []
    for path in files:
        relative = path.relative_to(ROOT)
        lower_name = path.name.lower()
        if lower_name in SENSITIVE_FILENAMES or path.suffix.lower() in SENSITIVE_SUFFIXES:
            failures.append(f"sensitive credential file must not be tracked: {relative}")
        if lower_name == ".env" or (
            lower_name.startswith(".env.") and not lower_name.endswith((".example", ".sample"))
        ):
            failures.append(f"environment secret file must not be tracked: {relative}")
        try:
            size = path.stat().st_size
        except OSError as exc:
            failures.append(f"cannot inspect {relative}: {exc}")
            continue
        if size > MAX_GITHUB_FILE_BYTES:
            failures.append(f"tracked file exceeds 95 MiB: {relative}")
        if size > 8 * 1024 * 1024:
            continue
        for label, pattern in SECRET_PATTERNS.items():
            line = first_match(path, pattern)
            if line is not None:
                failures.append(f"possible {label} in {relative}:{line}")
    return failures


def extract(pattern: str, relative: str) -> str:
    text = (ROOT / relative).read_text(encoding="utf-8")
    match = re.search(pattern, text, re.MULTILINE)
    if match is None:
        raise GuardError(f"cannot find release version in {relative}")
    return match.group(1)


def release_version() -> str:
    versions = {
        "CHANGELOG.md": extract(r"^## ([0-9]+\.[0-9]+\.[0-9]+)\b", "CHANGELOG.md"),
        "tablet Gradle": extract(r'^\s*versionName\s*=\s*"([^"]+)"', "android-tablet-client/build.gradle"),
        "tablet standalone build": extract(r"--version-name ([0-9]+\.[0-9]+\.[0-9]+)", "android-tablet-client/build.sh"),
        "voice gateway": extract(r'android:versionName="([^"]+)"', "android-voice-gateway/AndroidManifest.xml"),
        "Windows installer": extract(r'^#define MyAppVersion "([^"]+)"', "desktop/windows/UFI-Phone.iss"),
        "Linux metadata": extract(r'<release version="([^"]+)"', "desktop/linux/io.github.ruskuv1708.ufiphone.metainfo.xml"),
    }
    unique = set(versions.values())
    if len(unique) != 1:
        detail = ", ".join(f"{source}={version}" for source, version in versions.items())
        raise GuardError(f"release versions disagree: {detail}")
    return unique.pop()


def check_action_pins() -> list[str]:
    failures: list[str] = []
    workflow_dir = ROOT / ".github" / "workflows"
    for path in sorted(workflow_dir.glob("*.y*ml")):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            match = re.search(r"\buses:\s*([^\s#]+)", line)
            if match is None or match.group(1).startswith("./"):
                continue
            reference = match.group(1).rsplit("@", 1)[-1]
            if not re.fullmatch(r"[0-9a-f]{40}", reference):
                failures.append(
                    f"GitHub Action is not commit-pinned: {path.relative_to(ROOT)}:{line_number}"
                )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", help="Require this release tag to match the project version")
    args = parser.parse_args()
    try:
        version = release_version()
        failures = check_secrets_and_sizes(tracked_files()) + check_action_pins()
        if args.tag and args.tag != f"v{version}":
            failures.append(f"release tag {args.tag!r} must be v{version}")
        if failures:
            for failure in failures:
                print(f"guard: {failure}", file=sys.stderr)
            return 1
        print(f"Project guard passed for version {version}.")
        return 0
    except (GuardError, OSError, subprocess.SubprocessError) as error:
        print(f"guard: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
