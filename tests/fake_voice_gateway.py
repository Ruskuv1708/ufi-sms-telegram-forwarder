#!/usr/bin/env python3
"""Short local integration fixture for the Android UFI call client."""

from __future__ import annotations

import argparse
import hmac
import json
import math
from pathlib import Path
import socketserver
import struct
import threading
import time
from typing import BinaryIO


CONFIG_PATH = Path.home() / ".config" / "ufi-voice-gateway" / "client.json"


class TestState:
    def __init__(self, token: str) -> None:
        self.token = token
        self.state = "IDLE"
        self.lock = threading.Lock()
        self.uplink_complete = threading.Event()
        self.uplink_result: tuple[int, int, int, float] | None = None

    def get(self) -> str:
        with self.lock:
            return self.state

    def set(self, value: str) -> None:
        with self.lock:
            self.state = value


def read_line(stream: BinaryIO, maximum: int = 8192) -> str:
    data = bytearray()
    while len(data) < maximum:
        value = stream.read(1)
        if not value or value == b"\n":
            break
        if value != b"\r":
            data.extend(value)
    if len(data) >= maximum:
        raise ValueError("line too long")
    return data.decode("utf-8", "replace")


class VoiceServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address: tuple[str, int], handler: type[socketserver.StreamRequestHandler],
                 state: TestState) -> None:
        self.test_state = state
        super().__init__(address, handler)


class AuthenticatedHandler(socketserver.StreamRequestHandler):
    @property
    def state(self) -> TestState:
        return self.server.test_state  # type: ignore[attr-defined, no-any-return]

    def authenticate(self) -> bool:
        line = read_line(self.rfile, 160)
        supplied = line[6:].strip() if line.startswith("TOKEN ") else ""
        return hmac.compare_digest(supplied, self.state.token)

    def send_line(self, line: str) -> None:
        self.wfile.write((line + "\n").encode())
        self.wfile.flush()


class ControlHandler(AuthenticatedHandler):
    def handle(self) -> None:
        if not self.authenticate():
            self.send_line("ERR AUTH")
            return
        command = read_line(self.rfile, 128).strip().upper()
        if command != "STATUS":
            print(f"CONTROL {command}", flush=True)
        state = self.state.get()
        if command == "PING":
            self.send_line("OK PONG")
        elif command == "STATUS":
            self.send_line("OK " + json.dumps({
                "state": state,
                "network": "TEST",
                "caller": "Integration test" if state == "RINGING" else "",
                "downlinkBusy": False,
                "uplinkBusy": False,
            }, separators=(",", ":")))
        elif command == "ANSWER" and state == "RINGING":
            self.state.set("ACTIVE")
            self.send_line("OK")
        elif command == "HANGUP":
            self.state.set("IDLE")
            self.send_line("OK")
        else:
            self.send_line("ERR NOT_RINGING")


class DownlinkHandler(AuthenticatedHandler):
    def handle(self) -> None:
        if not self.authenticate():
            self.send_line("ERR AUTH")
            return
        if self.state.get() != "ACTIVE":
            self.send_line("ERR NO_CALL")
            return
        self.send_line("OK 8000 1 S16LE")
        frame = bytearray()
        for index in range(160):
            sample = int(700 * math.sin(2 * math.pi * 440 * index / 8000))
            frame.extend(struct.pack("<h", sample))
        try:
            while self.state.get() == "ACTIVE":
                self.wfile.write(frame)
                self.wfile.flush()
                time.sleep(0.02)
        except (BrokenPipeError, ConnectionResetError):
            pass


class UplinkHandler(AuthenticatedHandler):
    def handle(self) -> None:
        if not self.authenticate():
            self.send_line("ERR AUTH")
            return
        if self.state.get() != "ACTIVE":
            self.send_line("ERR NO_CALL")
            return
        self.send_line("OK 48000 1 S16LE")
        deadline = time.monotonic() + 4
        samples = 0
        nonzero = 0
        peak = 0
        sum_squares = 0
        while time.monotonic() < deadline and self.state.get() == "ACTIVE":
            data = self.rfile.read(3840)
            if not data:
                break
            usable = len(data) - len(data) % 2
            for (value,) in struct.iter_unpack("<h", data[:usable]):
                samples += 1
                if value:
                    nonzero += 1
                peak = max(peak, abs(value))
                sum_squares += value * value
        rms = math.sqrt(sum_squares / samples) if samples else 0.0
        self.state.uplink_result = (samples, nonzero, peak, rms)
        self.state.uplink_complete.set()


def load_token() -> str:
    if CONFIG_PATH.stat().st_mode & 0o077:
        raise RuntimeError("voice gateway configuration is not private")
    token = str(json.loads(CONFIG_PATH.read_text(encoding="utf-8"))["token"])
    if len(token) != 64:
        raise RuntimeError("invalid voice gateway token")
    return token


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="192.168.100.7")
    parser.add_argument("--ring-delay", type=float, default=5.0)
    parser.add_argument("--ring-only", type=float, default=0.0)
    args = parser.parse_args()

    state = TestState(load_token())
    servers = [
        VoiceServer((args.bind, 8765), ControlHandler, state),
        VoiceServer((args.bind, 8766), DownlinkHandler, state),
        VoiceServer((args.bind, 8767), UplinkHandler, state),
    ]
    threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in servers]
    for thread in threads:
        thread.start()
    print("READY", flush=True)
    time.sleep(args.ring_delay)
    state.set("RINGING")
    print("RINGING", flush=True)
    if args.ring_only > 0:
        time.sleep(args.ring_only)
        final_state = state.get()
        for server in servers:
            server.shutdown()
            server.server_close()
        print(f"RESULT state={final_state}", flush=True)
        return 0 if final_state == "RINGING" else 1
    completed = state.uplink_complete.wait(45)
    state.set("IDLE")
    for server in servers:
        server.shutdown()
        server.server_close()
    if not completed or state.uplink_result is None:
        print("RESULT no uplink audio", flush=True)
        return 1
    samples, nonzero, peak, rms = state.uplink_result
    print(
        f"RESULT samples={samples} nonzero={nonzero} peak={peak} rms={rms:.2f}",
        flush=True,
    )
    return 0 if samples > 0 and nonzero > 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
