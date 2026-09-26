#!/usr/bin/env python3
"""Probe, build, and safely install UFI Phone on supported modem hardware."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Any


PROJECT_DIR = Path(__file__).resolve().parent
PROFILES_PATH = PROJECT_DIR / "hardware-profiles.json"
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
        "jdk": (toolchain / "jdk" / "bin" / "javac").exists(),
        "androidPlatform19": (
            toolchain / "sdk" / "platforms" / "android-19" / "android.jar"
        ).exists(),
        "androidPlatform36": (
            toolchain / "sdk" / "platforms" / "android-36" / "android.jar"
        ).exists(),
        "buildTools35": (
            toolchain / "sdk" / "build-tools" / "35.0.0" / "aapt"
        ).exists(),
        "buildTools36": (
            toolchain / "sdk" / "build-tools" / "36.0.0" / "aapt"
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


def install(modem_serial: str, tablet_serial: str | None, skip_build: bool) -> None:
    report = full_report(modem_serial)
    modem = report["devices"][0]
    if modem["support"] != "tested":
        raise SetupError(
            "Refusing privileged installation on unverified hardware. Run doctor --json "
            "and add a reviewed hardware profile with the correct platform signing key."
        )
    if not skip_build:
        build_all()
    run_script(PROJECT_DIR / "ufi_voice.py", "setup", "--modem-serial", modem_serial)
    if tablet_serial:
        run_script(
            PROJECT_DIR / "ufi_voice.py",
            "setup-tablet",
            "--tablet-serial",
            tablet_serial,
        )
    print("Installation complete. No cloud or Telegram connection was enabled.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    doctor = subparsers.add_parser("doctor", help="read-only device and toolchain report")
    doctor.add_argument("--serial")
    doctor.add_argument("--json", action="store_true")
    subparsers.add_parser("profiles", help="show supported hardware profiles")
    subparsers.add_parser("build", help="build the modem and Android clients")
    installer = subparsers.add_parser("install", help="install on an exact tested profile")
    installer.add_argument("--modem-serial", required=True)
    installer.add_argument("--tablet-serial")
    installer.add_argument("--skip-build", action="store_true")
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
    elif args.command == "install":
        install(args.modem_serial, args.tablet_serial, args.skip_build)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, SetupError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
