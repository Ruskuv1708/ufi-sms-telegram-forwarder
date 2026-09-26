#!/usr/bin/env python3
"""Probe, build, and safely install UFI Phone on supported modem hardware."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

PROJECT_DIR = Path(__file__).resolve().parent
PROFILES_PATH = PROJECT_DIR / "hardware-profiles.json"
PAIRING_KIND = "ufi-phone-pairing"
PAIRING_VERSION = 1
SAFE_PROPERTIES = {
    "productDevice": "ro.product.device",
    "productModel": "ro.product.model",
    "manufacturer": "ro.product.manufacturer",
    "androidSdk": "ro.build.version.sdk",
    "androidRelease": "ro.build.version.release",
    "baseband": "gsm.version.baseband",
    "lanHost": "persist.cpe.gw.ip",
    "operator": "gsm.operator.alpha",
    "simState": "gsm.sim.state",
}


class SetupError(RuntimeError):
    pass


def run(arguments: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        arguments,
        check=check,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def adb(serial: str, *arguments: str, check: bool = True) -> str:
    result = run(["adb", "-s", serial, *arguments], check=check)
    return result.stdout.strip()


def load_profiles() -> list[dict[str, Any]]:
    data = json.loads(PROFILES_PATH.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1 or not isinstance(data.get("profiles"), list):
        raise SetupError("Unsupported hardware profile schema")
    profiles = list(data["profiles"])
    for profile in profiles:
        if not isinstance(profile, dict) or not isinstance(profile.get("id"), str):
            raise SetupError("Hardware profile is missing a stable ID")
        if profile.get("support") not in ("tested", "experimental", "unverified"):
            raise SetupError(f"Hardware profile {profile['id']} has an invalid support level")
        if not isinstance(profile.get("match"), dict) or not isinstance(
                profile.get("capabilities"), dict):
            raise SetupError(f"Hardware profile {profile['id']} is incomplete")
    return profiles


def connected_devices() -> list[dict[str, str]]:
    if not shutil.which("adb"):
        raise SetupError("ADB was not found in PATH")
    result = run(["adb", "devices", "-l"])
    devices: list[dict[str, str]] = []
    for line in result.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) < 2 or fields[1] != "device":
            continue
        item = {"serial": fields[0]}
        for field in fields[2:]:
            if ":" in field:
                key, value = field.split(":", 1)
                item[key] = value
        devices.append(item)
    return devices


def usb_id(device: dict[str, str]) -> str:
    location = device.get("usb", "")
    if not re.fullmatch(r"[0-9]+-[0-9]+(?:\.[0-9]+)*", location):
        return ""
    root = Path("/sys/bus/usb/devices") / location
    try:
        vendor = (root / "idVendor").read_text(encoding="ascii").strip().lower()
        product = (root / "idProduct").read_text(encoding="ascii").strip().lower()
    except OSError:
        return ""
    if not re.fullmatch(r"[0-9a-f]{4}", vendor) or not re.fullmatch(
        r"[0-9a-f]{4}", product
    ):
        return ""
    return f"{vendor}:{product}"


def probe_device(device: dict[str, str]) -> dict[str, Any]:
    serial = device["serial"]
    report: dict[str, Any] = {"serial": serial, "usbId": usb_id(device)}
    for output_name, property_name in SAFE_PROPERTIES.items():
        report[output_name] = adb(serial, "shell", "getprop", property_name, check=False)
    try:
        report["androidSdk"] = int(str(report["androidSdk"]))
    except ValueError:
        report["androidSdk"] = 0
    features = adb(serial, "shell", "pm", "list", "features", check=False).splitlines()
    report["telephony"] = "feature:android.hardware.telephony" in features
    report["microphone"] = "feature:android.hardware.microphone" in features
    report["preferredNetworkMode"] = adb(
        serial,
        "shell",
        "settings",
        "get",
        "global",
        "preferred_network_mode",
        check=False,
    )
    report["gatewayInstalled"] = bool(
        adb(serial, "shell", "pm", "path", "com.ufi.voicegateway", check=False)
    )
    report["networkGuardInstalled"] = bool(
        adb(serial, "shell", "pm", "path", "com.ufi.networkguard", check=False)
    )
    profile, reasons = match_profile(report, load_profiles())
    report["profile"] = profile.get("id", "") if profile else ""
    report["support"] = profile.get("support", "unverified") if profile else "unverified"
    report["matchNotes"] = reasons
    return report


def match_profile(
    report: dict[str, Any], profiles: list[dict[str, Any]]
) -> tuple[dict[str, Any] | None, list[str]]:
    best_reasons: list[str] = []
    for profile in profiles:
        match = profile.get("match", {})
        reasons: list[str] = []
        if report.get("productDevice") != match.get("productDevice"):
            reasons.append("product device differs")
        if int(report.get("androidSdk") or 0) != int(match.get("sdk") or 0):
            reasons.append("Android SDK differs")
        prefix = str(match.get("basebandPrefix", ""))
        if prefix and not str(report.get("baseband", "")).startswith(prefix):
            reasons.append("baseband family differs")
        known_usb = match.get("usbIds", [])
        if report.get("usbId") and known_usb and report["usbId"] not in known_usb:
            reasons.append("USB ID differs")
        expected_host = str(match.get("lanHost", ""))
        if expected_host and report.get("lanHost") not in ("", expected_host):
            reasons.append("LAN address differs")
        capabilities = profile.get("capabilities", {})
        requires_telephony = any(
            bool(capabilities.get(name))
            for name in (
                "smsRead",
                "smsSend",
                "incomingCalls",
                "outgoingCalls",
                "twoWayCallAudio",
            )
        )
        if requires_telephony and report.get("telephony") is not True:
            reasons.append("telephony capability is unavailable")
        if capabilities.get("twoWayCallAudio") and report.get("microphone") is not True:
            reasons.append("microphone capability is unavailable")
        if not reasons:
            return profile, ["exact tested hardware profile"]
        if not best_reasons or len(reasons) < len(best_reasons):
            best_reasons = reasons
    return None, best_reasons or ["no hardware profiles are installed"]


def build_readiness() -> dict[str, bool]:
    toolchain = Path(
        os.environ.get(
            "UFI_ANDROID_TOOLCHAIN",
            Path.home() / ".cache" / "ufi-sms-android" / "toolchain",
        )
    )
    java_home = Path(os.environ.get("JAVA_HOME", toolchain / "jdk"))
    sdk_root = Path(
        os.environ.get("ANDROID_SDK_ROOT")
        or os.environ.get("ANDROID_HOME")
        or toolchain / "sdk"
    )
    platform_keys = Path(
        os.environ.get(
            "UFI_PLATFORM_KEY_DIR",
            Path.home() / ".cache" / "ufi-sms-android" / "aosp-platform",
        )
    )
    platform_key = platform_keys / "platform.pk8"
    platform_cert = platform_keys / "platform.x509.pem"
    expected_fingerprints = {
        str(profile.get("installation", {}).get(
            "expectedPlatformCertificateSha256", ""
        )).upper()
        for profile in load_profiles()
        if profile.get("support") == "tested"
    }
    actual_fingerprint = ""
    if platform_cert.exists() and shutil.which("openssl"):
        result = run(
            [
                "openssl",
                "x509",
                "-in",
                str(platform_cert),
                "-noout",
                "-fingerprint",
                "-sha256",
            ],
            check=False,
        )
        if result.returncode == 0 and "=" in result.stdout:
            actual_fingerprint = result.stdout.strip().split("=", 1)[1].upper()
    key_is_private = platform_key.exists()
    if os.name != "nt" and key_is_private:
        key_is_private = (platform_key.stat().st_mode & 0o077) == 0
    return {
        "adb": bool(shutil.which("adb")),
        "jdk": (java_home / "bin" / "javac").exists(),
        "androidPlatform19": (
            sdk_root / "platforms" / "android-19" / "android.jar"
        ).exists(),
        "androidPlatform36": (
            sdk_root / "platforms" / "android-36" / "android.jar"
        ).exists(),
        "buildTools35": (
            sdk_root / "build-tools" / "35.0.0" / "aapt"
        ).exists(),
        "buildTools36": (
            sdk_root / "build-tools" / "36.0.0" / "aapt"
        ).exists(),
        "testedPlatformKey": (
            key_is_private
            and platform_cert.exists()
            and actual_fingerprint in expected_fingerprints
        ),
    }


def full_report(serial: str | None = None) -> dict[str, Any]:
    devices = connected_devices()
    if serial:
        devices = [device for device in devices if device["serial"] == serial]
        if not devices:
            raise SetupError(f"ADB device not found: {serial}")
    return {
        "schemaVersion": 1,
        "devices": [probe_device(device) for device in devices],
        "buildReadiness": build_readiness(),
    }


def print_report(report: dict[str, Any]) -> None:
    print("UFI Phone device doctor")
    print("=======================")
    devices = report["devices"]
    if not devices:
        print("No authorized ADB devices found.")
    for device in devices:
        print(f"\n{device['serial']}  {device.get('productModel') or 'Unknown model'}")
        print(f"  Android: {device.get('androidRelease') or '?'} (SDK {device['androidSdk']})")
        print(f"  Hardware: {device.get('productDevice') or '?'} / {device.get('usbId') or 'USB unknown'}")
        print(f"  Baseband: {device.get('baseband') or '?'}")
        print(f"  SIM/network: {device.get('simState') or '?'} / {device.get('operator') or 'unknown'}")
        print(f"  LAN: {device.get('lanHost') or 'not exposed'}")
        if device["profile"]:
            print(f"  Support: {device['support']} ({device['profile']})")
        else:
            print("  Support: unverified — " + ", ".join(device["matchNotes"]))
        print(
            "  Installed: gateway={} network-guard={}".format(
                "yes" if device["gatewayInstalled"] else "no",
                "yes" if device["networkGuardInstalled"] else "no",
            )
        )
    readiness = report["buildReadiness"]
    ready = all(readiness.values())
    print(f"\nLocal build environment: {'ready' if ready else 'incomplete'}")
    for name, present in readiness.items():
        print(f"  {'OK' if present else '--'} {name}")

    supported = [device for device in devices if device.get("support") == "tested"]
    tablets = [
        device
        for device in devices
        if device.get("support") != "tested" and int(device.get("androidSdk") or 0) >= 26
    ]
    print("\nNext step")
    if len(supported) == 1:
        suffix = " with the connected tablet" if len(tablets) == 1 else ""
        print(f"  Run ./setup.sh to install UFI Phone{suffix}.")
    elif not devices:
        print("  Connect the modem by USB, enable ADB, then run ./setup.sh again.")
    elif not supported:
        print("  No exact tested modem profile was found; installation remains locked.")
    else:
        print("  More than one supported modem is connected; choose one with --modem-serial.")


def run_script(path: Path, *arguments: str) -> None:
    result = subprocess.run([str(path), *arguments], cwd=PROJECT_DIR, check=False)
    if result.returncode:
        raise SetupError(f"Command failed ({result.returncode}): {path.name}")


def build_all() -> None:
    for relative in (
        "android-network-guard/build.sh",
        "android-voice-gateway/build.sh",
        "android-tablet-client/build.sh",
    ):
        print(f"Building {relative}…")
        run_script(PROJECT_DIR / relative)


def device_label(device: dict[str, Any]) -> str:
    model = str(device.get("productModel") or "Unknown Android device")
    release = str(device.get("androidRelease") or "?")
    return f"{model} · Android {release} · {device['serial']}"


def select_install_targets(
    devices: list[dict[str, Any]],
    *,
    modem_serial: str | None = None,
    tablet_serial: str | None = None,
    desktop_only: bool = False,
) -> tuple[dict[str, Any], dict[str, Any] | None]:
    """Resolve one exact modem and at most one compatible Android client."""
    by_serial = {str(device.get("serial", "")): device for device in devices}
    if modem_serial:
        modem = by_serial.get(modem_serial)
        if modem is None:
            raise SetupError(f"ADB device not found: {modem_serial}")
        if modem.get("support") != "tested":
            raise SetupError(
                "Refusing privileged installation on unverified hardware. Run doctor --json "
                "and add a reviewed hardware profile with the correct platform signing key."
            )
    else:
        modem_candidates = [
            device for device in devices if device.get("support") == "tested"
        ]
        if not modem_candidates:
            raise SetupError(
                "No exact tested UFI modem was detected. Connect it over USB, authorize ADB, "
                "and run ./ufi_setup.py doctor."
            )
        if len(modem_candidates) > 1:
            serials = ", ".join(str(device["serial"]) for device in modem_candidates)
            raise SetupError(
                f"More than one supported modem is connected ({serials}); "
                "choose one with --modem-serial."
            )
        modem = modem_candidates[0]

    if desktop_only:
        if tablet_serial:
            raise SetupError("--tablet-serial cannot be combined with --desktop-only")
        return modem, None

    profile = next(
        (profile for profile in load_profiles() if profile["id"] == modem.get("profile")),
        None,
    )
    if profile is None:
        raise SetupError("The selected modem profile is no longer available")
    minimum_sdk = int(profile.get("installation", {}).get("tabletMinSdk", 26))
    if tablet_serial:
        tablet = by_serial.get(tablet_serial)
        if tablet is None:
            raise SetupError(f"ADB device not found: {tablet_serial}")
        if tablet is modem:
            raise SetupError("The modem cannot also be selected as the tablet")
        if int(tablet.get("androidSdk") or 0) < minimum_sdk:
            raise SetupError(f"The tablet must run Android API {minimum_sdk} or newer")
        return modem, tablet

    tablet_candidates = [
        device
        for device in devices
        if device is not modem and int(device.get("androidSdk") or 0) >= minimum_sdk
    ]
    if len(tablet_candidates) > 1:
        serials = ", ".join(str(device["serial"]) for device in tablet_candidates)
        raise SetupError(
            f"More than one Android tablet is connected ({serials}); choose one with "
            "--tablet-serial or use --desktop-only."
        )
    return modem, tablet_candidates[0] if tablet_candidates else None


def write_pairing_file(
    destination: Path,
    config: dict[str, Any],
    *,
    overwrite: bool = False,
) -> Path:
    """Write a portable pairing document without exposing its secret on stdout."""
    from ufi_voice import validate_config

    validate_config(config)
    destination = destination.expanduser().resolve()
    if destination.exists() and not overwrite:
        raise SetupError(f"Pairing file already exists: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    document = {
        "kind": PAIRING_KIND,
        "version": PAIRING_VERSION,
        "host": str(config["host"]),
        "token": str(config["token"]),
    }
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{destination.name}.", suffix=".tmp", dir=destination.parent
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        temporary.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
        if os.name != "nt":
            temporary.chmod(0o600)
        os.replace(temporary, destination)
        if os.name != "nt":
            destination.chmod(0o600)
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
    return destination


def export_pairing(destination: Path, *, overwrite: bool = False) -> Path:
    from ufi_voice import load_config

    return write_pairing_file(destination, load_config(), overwrite=overwrite)


def print_setup_plan(
    modem: dict[str, Any], tablet: dict[str, Any] | None, *, skip_build: bool
) -> None:
    print("UFI Phone guided setup")
    print("======================")
    print(f"Modem:  {device_label(modem)}")
    print(f"Tablet: {device_label(tablet) if tablet else 'not selected (desktop only)'}")
    print("\nPlan")
    step = 1
    if not skip_build:
        print(f"  {step}. Build the signed modem services and Android companion")
        step += 1
    print(f"  {step}. Install and pair the guarded modem gateway")
    step += 1
    if tablet:
        print(f"  {step}. Install, pair, and open UFI Phone on the tablet")
        step += 1
    print(f"  {step}. Verify the private-LAN connection")


def install(
    modem_serial: str | None,
    tablet_serial: str | None,
    skip_build: bool,
    *,
    desktop_only: bool = False,
    dry_run: bool = False,
    pairing_file: Path | None = None,
    force: bool = False,
) -> None:
    report = full_report()
    modem, tablet = select_install_targets(
        report["devices"],
        modem_serial=modem_serial,
        tablet_serial=tablet_serial,
        desktop_only=desktop_only,
    )
    print_setup_plan(modem, tablet, skip_build=skip_build)
    missing = [
        name for name, present in report["buildReadiness"].items() if not present
    ]
    if not skip_build and missing:
        print("\nMissing build prerequisites: " + ", ".join(missing))
    if dry_run:
        print("\nDry run complete; no files or devices were changed.")
        return
    if pairing_file and pairing_file.expanduser().resolve().exists() and not force:
        raise SetupError(f"Pairing file already exists: {pairing_file.expanduser().resolve()}")
    if not skip_build and missing:
        raise SetupError(
            "Build environment is incomplete. Run ./ufi_setup.py doctor, repair the "
            "listed prerequisites, or use --skip-build only with APKs you built earlier."
        )
    if not skip_build:
        build_all()
    run_script(
        PROJECT_DIR / "ufi_voice.py",
        "setup",
        "--modem-serial",
        str(modem["serial"]),
    )
    if tablet:
        run_script(
            PROJECT_DIR / "ufi_voice.py",
            "setup-tablet",
            "--tablet-serial",
            str(tablet["serial"]),
        )
    if pairing_file:
        exported = export_pairing(pairing_file, overwrite=force)
        print(f"Pairing file: {exported}")
        print("Keep it private, import it on the other computer, then delete the transferred copy.")
    print("\nSetup complete")
    print("  Desktop: open UFI Phone on this computer; pairing is already saved.")
    if tablet:
        print("  Tablet: UFI Phone is installed, paired, and open.")
    else:
        print("  Tablet: connect one by USB later and rerun ./setup.sh --tablet-serial SERIAL.")
    print("  Privacy: calls and SMS stay on the modem LAN; Telegram remains disabled.")


def add_setup_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--modem-serial",
        help="choose a modem only when more than one supported modem is connected",
    )
    parser.add_argument(
        "--tablet-serial",
        help="choose a tablet only when more than one Android client is connected",
    )
    parser.add_argument(
        "--desktop-only",
        action="store_true",
        help="provision the modem and this desktop without installing a tablet app",
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="reuse the APKs already present in the project build directories",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="show selected devices and planned actions without changing anything",
    )
    parser.add_argument(
        "--pairing-file",
        type=Path,
        help="also export a private .ufi-phone file for another desktop",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="replace an existing --pairing-file destination",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    doctor = subparsers.add_parser("doctor", help="read-only device and toolchain report")
    doctor.add_argument("--serial")
    doctor.add_argument("--json", action="store_true")
    subparsers.add_parser("profiles", help="show supported hardware profiles")
    subparsers.add_parser("build", help="build the modem and Android clients")
    setup = subparsers.add_parser(
        "setup", help="auto-detect, install, pair, and verify supported devices"
    )
    add_setup_arguments(setup)
    installer = subparsers.add_parser(
        "install", help="legacy alias for the guarded guided setup"
    )
    add_setup_arguments(installer)
    pairing = subparsers.add_parser(
        "pairing", help="export a private pairing file for another desktop"
    )
    pairing.add_argument("--output", type=Path, required=True)
    pairing.add_argument("--force", action="store_true")
    args = parser.parse_args()

    if args.command == "doctor":
        report = full_report(args.serial)
        if args.json:
            print(json.dumps(report, indent=2, ensure_ascii=False))
        else:
            print_report(report)
    elif args.command == "profiles":
        print(json.dumps(load_profiles(), indent=2, ensure_ascii=False))
    elif args.command == "build":
        build_all()
    elif args.command in ("setup", "install"):
        install(
            args.modem_serial,
            args.tablet_serial,
            args.skip_build,
            desktop_only=args.desktop_only,
            dry_run=args.dry_run,
            pairing_file=args.pairing_file,
            force=args.force,
        )
    elif args.command == "pairing":
        exported = export_pairing(args.output, overwrite=args.force)
        print(f"Pairing file created: {exported}")
        print("Keep it private and delete the transferred copy after importing it.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, SetupError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
