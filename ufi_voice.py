#!/usr/bin/env python3
"""Configure and use the UFI LAN voice gateway from a Linux desktop."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import threading
import time
from typing import Any


PROJECT_DIR = Path(__file__).resolve().parent
CONFIG_PATH = Path.home() / ".config" / "ufi-voice-gateway" / "client.json"
DEFAULT_HOST = "192.168.100.1"
CONTROL_PORT = 8765
DOWNLINK_PORT = 8766
UPLINK_PORT = 8767


class VoiceError(RuntimeError):
    pass


def adb_devices() -> list[str]:
    result = subprocess.run(
        ["adb", "devices"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    devices: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "device":
            devices.append(fields[0])
    return devices


def find_modem_serial(explicit: str | None) -> str:
    if explicit:
        return explicit
    matches: list[str] = []
    for serial in adb_devices():
        result = subprocess.run(
            ["adb", "-s", serial, "shell", "getprop", "ro.product.device"],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        if result.stdout.strip() == "msm8916_32_512":
            matches.append(serial)
    if len(matches) != 1:
        raise VoiceError("Could not uniquely identify the UFI modem over ADB")
    return matches[0]


def find_tablet_serial(explicit: str | None) -> str:
    if explicit:
        return explicit
    matches: list[str] = []
    for serial in adb_devices():
        product = subprocess.run(
            ["adb", "-s", serial, "shell", "getprop", "ro.product.device"],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        ).stdout.strip()
        sdk_text = subprocess.run(
            ["adb", "-s", serial, "shell", "getprop", "ro.build.version.sdk"],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        ).stdout.strip()
        try:
            sdk = int(sdk_text)
        except ValueError:
            continue
        if product != "msm8916_32_512" and sdk >= 26:
            matches.append(serial)
    if len(matches) != 1:
        raise VoiceError("Could not uniquely identify the Android tablet over ADB")
    return matches[0]


def run_adb(serial: str, *arguments: str) -> None:
    result = subprocess.run(["adb", "-s", serial, *arguments], check=False)
    if result.returncode != 0:
        raise VoiceError("ADB command failed")


def save_config(config: dict[str, Any]) -> None:
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = CONFIG_PATH.with_suffix(".tmp")
    temporary.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    temporary.chmod(0o600)
    os.replace(temporary, CONFIG_PATH)
    CONFIG_PATH.chmod(0o600)


def load_config() -> dict[str, Any]:
    if not CONFIG_PATH.exists():
        raise VoiceError(f"Gateway is not configured; run {Path(__file__).name} setup")
    mode = CONFIG_PATH.stat().st_mode & 0o777
    if mode & 0o077:
        raise VoiceError(f"Refusing to use non-private configuration {CONFIG_PATH}")
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    token = str(config.get("token", ""))
    if len(token) != 64 or any(char not in "0123456789abcdef" for char in token):
        raise VoiceError("Gateway configuration contains an invalid token")
    return config


def setup_gateway(serial: str, install: bool) -> None:
    existing: dict[str, Any] = {}
    if CONFIG_PATH.exists():
        existing = load_config()
    token = str(existing.get("token") or secrets.token_hex(32))
    host = str(existing.get("host") or DEFAULT_HOST)

    if install:
        guard_apk = PROJECT_DIR / "android-network-guard" / "build" / "ufi-network-guard.apk"
        gateway_apk = PROJECT_DIR / "android-voice-gateway" / "build" / "ufi-voice-gateway.apk"
        for apk in (guard_apk, gateway_apk):
            if not apk.exists():
                raise VoiceError(f"Missing built APK: {apk}")
            run_adb(serial, "install", "-r", str(apk))

        # The guard shares the long-lived com.android.phone process. Android
        # 4.4 can keep its old service instance alive across an APK update, so
        # explicitly recreate it before applying the new build.
        subprocess.run(
            [
                "adb",
                "-s",
                serial,
                "shell",
                "am",
                "stopservice",
                "-n",
                "com.ufi.networkguard/.NetworkGuardService",
            ],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

    run_adb(
        serial,
        "shell",
        "am",
        "startservice",
        "-a",
        "com.ufi.networkguard.APPLY",
        "-n",
        "com.ufi.networkguard/.NetworkGuardService",
    )
    run_adb(
        serial,
        "shell",
        "am",
        "startservice",
        "-a",
        "com.ufi.voicegateway.CONFIGURE",
        "-n",
        "com.ufi.voicegateway/.GatewayControlService",
        "--es",
        "token",
        token,
        "--ez",
        "enabled",
        "true",
    )
    save_config({"host": host, "token": token, "modem_serial": serial})

    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        try:
            response = control_request({"host": host, "token": token}, "PING")
            if response == "PONG":
                print("Voice gateway is paired and reachable.")
                return
        except (OSError, VoiceError):
            time.sleep(1)
    raise VoiceError("Gateway was configured but did not become reachable")


def setup_tablet(serial: str, install: bool) -> None:
    config = load_config()
    package = "com.ufi.voiceclient"
    if install:
        apk = (
            PROJECT_DIR
            / "android-tablet-client"
            / "standalone-build"
            / "ufi-call-client.apk"
        )
        if not apk.exists():
            raise VoiceError(f"Missing built APK: {apk}")
        run_adb(serial, "install", "-r", str(apk))

    for permission in (
        "android.permission.RECORD_AUDIO",
        "android.permission.POST_NOTIFICATIONS",
    ):
        run_adb(serial, "shell", "pm", "grant", package, permission)

    # Android 14+ may gate full-screen incoming-call notifications behind an
    # app-op even for a declared USE_FULL_SCREEN_INTENT permission.
    subprocess.run(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "cmd",
            "appops",
            "set",
            package,
            "USE_FULL_SCREEN_INTENT",
            "allow",
        ],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.run(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "dumpsys",
            "deviceidle",
            "whitelist",
            f"+{package}",
        ],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    run_adb(
        serial,
        "shell",
        "am",
        "broadcast",
        "--receiver-foreground",
        "-a",
        "com.ufi.voiceclient.CONFIGURE",
        "-n",
        f"{package}/.ConfigReceiver",
        "--es",
        "host",
        str(config["host"]),
        "--es",
        "token",
        str(config["token"]),
        "--ez",
        "enabled",
        "true",
    )
    run_adb(
        serial,
        "shell",
        "am",
        "start",
        "-a",
        "android.intent.action.MAIN",
        "-c",
        "android.intent.category.LAUNCHER",
        "-n",
        f"{package}/.MainActivity",
    )
    print("Tablet call client is installed, paired, and running.")


def read_line(stream: Any, maximum: int = 8192) -> bytes:
    data = bytearray()
    while len(data) < maximum:
        value = stream.read(1)
        if not value:
            break
        if value == b"\n":
            break
        if value != b"\r":
            data.extend(value)
    if len(data) >= maximum:
        raise VoiceError("Gateway response was too long")
    return bytes(data)


def authenticate_socket(config: dict[str, Any], port: int) -> tuple[socket.socket, Any]:
    connection = socket.create_connection((str(config["host"]), port), timeout=5)
    connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    stream = connection.makefile("rwb", buffering=0)
    stream.write(f"TOKEN {config['token']}\n".encode("ascii"))
    return connection, stream


def control_request(config: dict[str, Any], command: str) -> str:
    connection, stream = authenticate_socket(config, CONTROL_PORT)
    try:
        stream.write((command + "\n").encode("ascii"))
        response = read_line(stream).decode("utf-8", "replace")
    finally:
        stream.close()
        connection.close()
    if response == "OK":
        return ""
    if response.startswith("OK "):
        return response[3:]
    raise VoiceError(response or "Gateway closed the connection")


def gateway_status(config: dict[str, Any]) -> dict[str, Any]:
    return json.loads(control_request(config, "STATUS"))


class AudioSession:
    def __init__(self, config: dict[str, Any]) -> None:
        self.config = config
        self.stop_event = threading.Event()
        self.sockets: list[socket.socket] = []
        self.processes: list[subprocess.Popen[bytes]] = []
        self.lock = threading.Lock()

    def start(self) -> None:
        if not shutil.which("parec") or not shutil.which("paplay"):
            raise VoiceError("Install PulseAudio/PipeWire parec and paplay tools")
        threading.Thread(target=self._downlink, name="UfiVoiceDownlink", daemon=True).start()
        threading.Thread(target=self._uplink, name="UfiVoiceUplink", daemon=True).start()

    def stop(self) -> None:
        self.stop_event.set()
        with self.lock:
            for connection in self.sockets:
                try:
                    connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                connection.close()
            self.sockets.clear()
            for process in self.processes:
                process.terminate()
            self.processes.clear()

    def _track_socket(self, connection: socket.socket) -> None:
        with self.lock:
            self.sockets.append(connection)

    def _track_process(self, process: subprocess.Popen[bytes]) -> None:
        with self.lock:
            self.processes.append(process)

    def _downlink(self) -> None:
        player: subprocess.Popen[bytes] | None = None
        connection: socket.socket | None = None
        stream: Any = None
        try:
            connection, stream = authenticate_socket(self.config, DOWNLINK_PORT)
            self._track_socket(connection)
            response = read_line(stream).decode("ascii", "replace")
            if not response.startswith("OK "):
                raise VoiceError(response)
            player = subprocess.Popen(
                [
                    "paplay",
                    "--raw",
                    "--format=s16le",
                    "--rate=8000",
                    "--channels=1",
                    "--latency-msec=80",
                ],
                stdin=subprocess.PIPE,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            self._track_process(player)
            assert player.stdin is not None
            while not self.stop_event.is_set():
                chunk = stream.read(2048)
                if not chunk:
                    break
                player.stdin.write(chunk)
                player.stdin.flush()
        except (OSError, VoiceError, BrokenPipeError):
            pass
        finally:
            if player is not None:
                player.terminate()
            if stream is not None:
                stream.close()
            if connection is not None:
                connection.close()

    def _uplink(self) -> None:
        recorder: subprocess.Popen[bytes] | None = None
        connection: socket.socket | None = None
        stream: Any = None
        try:
            connection, stream = authenticate_socket(self.config, UPLINK_PORT)
            self._track_socket(connection)
            response = read_line(stream).decode("ascii", "replace")
            if not response.startswith("OK "):
                raise VoiceError(response)
            recorder = subprocess.Popen(
                [
                    "parec",
                    "--raw",
                    "--format=s16le",
                    "--rate=48000",
                    "--channels=1",
                    "--latency-msec=80",
                ],
                stdout=subprocess.PIPE,
                stdin=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            self._track_process(recorder)
            assert recorder.stdout is not None
            while not self.stop_event.is_set():
                chunk = recorder.stdout.read(3840)
                if not chunk:
                    break
                stream.write(chunk)
        except (OSError, VoiceError, BrokenPipeError):
            pass
        finally:
            if recorder is not None:
                recorder.terminate()
            if stream is not None:
                stream.close()
            if connection is not None:
                connection.close()


class VoiceWindow:
    def __init__(self, config: dict[str, Any]) -> None:
        import tkinter as tk
        from tkinter import messagebox

        self.tk = tk
        self.messagebox = messagebox
        self.config = config
        self.audio: AudioSession | None = None
        self.last_state = ""
        self.last_caller = ""
        self.root = tk.Tk()
        self.root.title("UFI Call Gateway")
        self.root.geometry("430x300")
        self.root.minsize(390, 280)
        self.root.protocol("WM_DELETE_WINDOW", self.close)

        frame = tk.Frame(self.root, padx=24, pady=20)
        frame.pack(fill="both", expand=True)
        tk.Label(frame, text="UFI Call Gateway", font=("Sans", 20, "bold")).pack(anchor="w")
        self.state_label = tk.Label(frame, text="Connecting…", font=("Sans", 15))
        self.state_label.pack(anchor="w", pady=(18, 4))
        self.detail_label = tk.Label(frame, text="", font=("Sans", 11), fg="#555555")
        self.detail_label.pack(anchor="w")

        controls = tk.Frame(frame)
        controls.pack(fill="x", pady=(24, 10))
        self.answer_button = tk.Button(
            controls, text="Answer", width=12, command=self.answer, state="disabled"
        )
        self.answer_button.pack(side="left", padx=(0, 10))
        self.hangup_button = tk.Button(
            controls, text="Hang up", width=12, command=self.hangup, state="disabled"
        )
        self.hangup_button.pack(side="left")
        self.audio_button = tk.Button(
            frame, text="Connect audio", command=self.start_audio, state="disabled"
        )
        self.audio_button.pack(anchor="w")
        tk.Label(
            frame,
            text="Use headphones to prevent echo. Audio stays on the modem LAN.",
            fg="#666666",
            wraplength=370,
            justify="left",
        ).pack(anchor="w", pady=(18, 0))
        self.root.after(100, self.poll)

    def run(self) -> None:
        self.root.mainloop()

    def poll(self) -> None:
        def worker() -> None:
            try:
                status = gateway_status(self.config)
                self.root.after(0, lambda: self.apply_status(status))
            except Exception as error:
                self.root.after(0, lambda: self.show_offline(str(error)))

        threading.Thread(target=worker, daemon=True).start()
        self.root.after(1000, self.poll)

    def apply_status(self, status: dict[str, Any]) -> None:
        state = str(status.get("state", "UNKNOWN"))
        caller = str(status.get("caller", ""))
        network = str(status.get("network", "UNKNOWN"))
        call_ready = bool(status.get("callReady", True))
        preferred_mode = int(status.get("preferredNetworkMode", 9))
        recoveries = int(status.get("modeRecoveries", 0))
        self.state_label.config(text=state.title())
        if call_ready:
            readiness = "Calls: ready (LTE/3G automatic)"
            if recoveries:
                readiness += f" · LTE-only recovered {recoveries} time"
                if recoveries != 1:
                    readiness += "s"
        else:
            readiness = f"WARNING: LTE-only mode {preferred_mode}; calls may be busy"
        detail = f"Network: {network}   {readiness}"
        if caller:
            detail += f"   Caller: {caller}"
        self.detail_label.config(text=detail)
        self.answer_button.config(state="normal" if state == "RINGING" else "disabled")
        self.hangup_button.config(state="normal" if state == "ACTIVE" else "disabled")
        self.audio_button.config(state="normal" if state == "ACTIVE" else "disabled")
        if state == "RINGING" and self.last_state != "RINGING":
            self.root.deiconify()
            self.root.lift()
            if shutil.which("notify-send"):
                subprocess.Popen(
                    ["notify-send", "Incoming modem call", caller or "Unknown caller"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
        if state != "ACTIVE" and self.audio is not None:
            self.audio.stop()
            self.audio = None
            self.audio_button.config(text="Connect audio")
        self.last_state = state
        self.last_caller = caller

    def show_offline(self, detail: str) -> None:
        self.state_label.config(text="Offline")
        self.detail_label.config(text=detail)
        self.answer_button.config(state="disabled")
        self.hangup_button.config(state="disabled")
        self.audio_button.config(state="disabled")

    def answer(self) -> None:
        self._command("ANSWER")
        self.root.after(1200, self.start_audio)

    def hangup(self) -> None:
        self._command("HANGUP")

    def _command(self, command: str) -> None:
        def worker() -> None:
            try:
                control_request(self.config, command)
            except Exception as error:
                self.root.after(0, lambda: self.messagebox.showerror("UFI Call Gateway", str(error)))

        threading.Thread(target=worker, daemon=True).start()

    def start_audio(self) -> None:
        if self.audio is not None:
            return
        try:
            self.audio = AudioSession(self.config)
            self.audio.start()
            self.audio_button.config(text="Audio connected")
        except Exception as error:
            self.audio = None
            self.messagebox.showerror("UFI Call Gateway", str(error))

    def close(self) -> None:
        if self.audio is not None:
            self.audio.stop()
        self.root.destroy()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command",
        choices=("setup", "setup-tablet", "status", "answer", "hangup", "ui"),
    )
    parser.add_argument("--modem-serial")
    parser.add_argument("--tablet-serial")
    parser.add_argument("--no-install", action="store_true")
    args = parser.parse_args()

    if args.command == "setup":
        serial = find_modem_serial(args.modem_serial)
        setup_gateway(serial, install=not args.no_install)
        return 0

    if args.command == "setup-tablet":
        serial = find_tablet_serial(args.tablet_serial)
        setup_tablet(serial, install=not args.no_install)
        return 0

    config = load_config()
    if args.command == "status":
        status = gateway_status(config)
        print(json.dumps(status, indent=2))
    elif args.command == "answer":
        control_request(config, "ANSWER")
    elif args.command == "hangup":
        control_request(config, "HANGUP")
    elif args.command == "ui":
        from desktop.ufi_phone_desktop import UfiPhoneApp

        UfiPhoneApp(config).run()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, VoiceError) as error:
        print(f"error: {error}", file=__import__("sys").stderr)
        raise SystemExit(1)
