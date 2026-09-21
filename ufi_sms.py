#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.10"
# dependencies = [
#   "pyusb==1.3.1",
# ]
# ///
"""Read and watch SMS messages on a Qualcomm UFI003 USB modem.

The modem exposes an unclaimed USB serial interface alongside RNDIS and ADB.
This utility talks to that interface directly with libusb, so it does not need
root privileges or a kernel serial driver.
"""

from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections.abc import Iterable
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

import usb.core
import usb.util

VID = 0x05C6
PID = 0x90B4
SERIAL_INTERFACE = 2
DEFAULT_ARCHIVE = Path.home() / ".local" / "share" / "ufi-sms" / "inbox.jsonl"
DEFAULT_TELEGRAM_CONFIG = Path.home() / ".config" / "ufi-sms" / "telegram.json"
DEFAULT_TELEGRAM_STATE = Path.home() / ".local" / "share" / "ufi-sms" / "telegram-sent.txt"
STATUS_NAMES = {
    0: "REC UNREAD",
    1: "REC READ",
    2: "STO UNSENT",
    3: "STO SENT",
    4: "ALL",
}


class ModemError(RuntimeError):
    pass


class TelegramError(ModemError):
    pass


@dataclass
class SmsMessage:
    storage: str
    index: int
    status: str
    sender: str
    timestamp: str
    body: str
    part: str = ""
    dcs: int | None = None
    raw_pdu: str = ""


@dataclass
class TelegramConfig:
    bot_token: str
    chat_id: int | str
    protect_content: bool = True


GSM7_DEFAULT = [
    "@", "£", "$", "¥", "è", "é", "ù", "ì", "ò", "Ç", "\n", "Ø", "ø", "\r", "Å", "å",
    "Δ", "_", "Φ", "Γ", "Λ", "Ω", "Π", "Ψ", "Σ", "Θ", "Ξ", "\x1b", "Æ", "æ", "ß", "É",
    " ", "!", '"', "#", "¤", "%", "&", "'", "(", ")", "*", "+", ",", "-", ".", "/",
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ":", ";", "<", "=", ">", "?",
    "¡", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O",
    "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "Ä", "Ö", "Ñ", "Ü", "§",
    "¿", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o",
    "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "ä", "ö", "ñ", "ü", "à",
]
GSM7_EXTENSION = {
    0x0A: "\f",
    0x14: "^",
    0x28: "{",
    0x29: "}",
    0x2F: "\\",
    0x3C: "[",
    0x3D: "~",
    0x3E: "]",
    0x40: "|",
    0x65: "€",
}


def unpack_septets(data: bytes, count: int, bit_offset: int = 0) -> list[int]:
    values: list[int] = []
    for number in range(max(0, count)):
        bit_position = bit_offset + number * 7
        byte_position = bit_position // 8
        shift = bit_position % 8
        if byte_position >= len(data):
            break
        value = data[byte_position] >> shift
        if shift > 1 and byte_position + 1 < len(data):
            value |= data[byte_position + 1] << (8 - shift)
        values.append(value & 0x7F)
    return values


def decode_gsm7(values: Iterable[int]) -> str:
    output: list[str] = []
    escaped = False
    for value in values:
        if escaped:
            output.append(GSM7_EXTENSION.get(value, "�"))
            escaped = False
        elif value == 0x1B:
            escaped = True
        else:
            output.append(GSM7_DEFAULT[value] if value < len(GSM7_DEFAULT) else "�")
    if escaped:
        output.append("�")
    return "".join(output)


def decode_digits(data: bytes, digit_count: int) -> str:
    digits: list[str] = []
    for byte in data:
        for nibble in (byte & 0x0F, byte >> 4):
            if len(digits) >= digit_count:
                break
            if nibble <= 9:
                digits.append(str(nibble))
            elif nibble == 0xA:
                digits.append("*")
            elif nibble == 0xB:
                digits.append("#")
            elif nibble == 0xC:
                digits.append("a")
            elif nibble == 0xD:
                digits.append("b")
            elif nibble == 0xE:
                digits.append("c")
    return "".join(digits)


def decode_address(data: bytes, length: int, toa: int) -> str:
    if (toa & 0x70) == 0x50:  # Alphanumeric sender.
        text = decode_gsm7(unpack_septets(data, length))
        return text
    address = decode_digits(data, length)
    if (toa & 0xF0) == 0x90 and address:
        address = "+" + address
    return address


def swapped_decimal(byte: int) -> int:
    return (byte & 0x0F) * 10 + ((byte >> 4) & 0x0F)


def decode_timestamp(raw: bytes) -> str:
    if len(raw) < 7:
        return ""
    try:
        year = swapped_decimal(raw[0])
        year += 2000 if year < 90 else 1900
        month, day, hour, minute, second = [swapped_decimal(value) for value in raw[1:6]]
        return datetime(year, month, day, hour, minute, second).isoformat(sep=" ")
    except (ValueError, OverflowError):
        return ""


def parse_udh(user_data: bytes) -> tuple[int, dict[str, int] | None]:
    if not user_data:
        return 0, None
    header_octets = user_data[0] + 1
    if header_octets > len(user_data):
        return 0, None
    concat: dict[str, int] | None = None
    position = 1
    while position + 1 < header_octets:
        identifier = user_data[position]
        length = user_data[position + 1]
        value = user_data[position + 2 : position + 2 + length]
        if identifier == 0x00 and len(value) == 3:
            concat = {"reference": value[0], "total": value[1], "sequence": value[2]}
        elif identifier == 0x08 and len(value) == 4:
            concat = {
                "reference": (value[0] << 8) | value[1],
                "total": value[2],
                "sequence": value[3],
            }
        position += 2 + length
    return header_octets, concat


def decode_user_data(data: bytes, dcs: int, udl: int, has_header: bool) -> tuple[str, dict[str, int] | None]:
    header_octets, concat = parse_udh(data) if has_header else (0, None)
    coding = dcs & 0x0C
    if coding == 0x08 or (dcs & 0xF0) == 0xE0:
        payload = data[header_octets:]
        if len(payload) % 2:
            payload = payload[:-1]
        return payload.decode("utf-16-be", "replace"), concat
    if coding == 0x04:
        return data[header_octets:udl].decode("latin-1", "replace"), concat

    if has_header:
        header_septets = (header_octets * 8 + 6) // 7
        text_count = max(0, udl - header_septets)
        bit_offset = header_septets * 7
    else:
        text_count = udl
        bit_offset = 0
    return decode_gsm7(unpack_septets(data, text_count, bit_offset)), concat


def decode_pdu(pdu_text: str) -> dict[str, object]:
    cleaned = re.sub(r"\s+", "", pdu_text)
    try:
        packet = bytes.fromhex(cleaned)
    except ValueError as exc:
        raise ModemError(f"Invalid SMS PDU: {exc}") from exc
    if len(packet) < 2:
        raise ModemError("SMS PDU is too short")

    smsc_length = packet[0]
    position = 1 + smsc_length
    if position >= len(packet):
        raise ModemError("SMS PDU has an invalid SMSC length")
    first_octet = packet[position]
    position += 1
    message_type = first_octet & 0x03
    has_header = bool(first_octet & 0x40)
    timestamp = ""

    if message_type == 0:  # SMS-DELIVER
        address_length = packet[position]
        position += 1
        toa = packet[position]
        position += 1
        if (toa & 0x70) == 0x50:
            address_bytes = (address_length * 7 + 7) // 8
        else:
            address_bytes = (address_length + 1) // 2
        sender = decode_address(packet[position : position + address_bytes], address_length, toa)
        position += address_bytes
        if position + 10 > len(packet):
            raise ModemError("Truncated SMS-DELIVER PDU")
        position += 1  # PID
        dcs = packet[position]
        position += 1
        timestamp = decode_timestamp(packet[position : position + 7])
        position += 7
    elif message_type == 1:  # SMS-SUBMIT, usually a stored outgoing message.
        position += 1  # Message reference
        address_length = packet[position]
        position += 1
        toa = packet[position]
        position += 1
        address_bytes = (address_length + 1) // 2
        sender = decode_address(packet[position : position + address_bytes], address_length, toa)
        position += address_bytes
        position += 1  # PID
        dcs = packet[position]
        position += 1
        validity_format = (first_octet >> 3) & 0x03
        if validity_format == 2:
            position += 1
        elif validity_format in (1, 3):
            position += 7
    else:
        return {
            "sender": "",
            "timestamp": "",
            "body": f"[Unsupported SMS PDU type {message_type}]",
            "dcs": None,
            "concat": None,
        }

    if position >= len(packet):
        raise ModemError("SMS PDU has no user-data length")
    udl = packet[position]
    position += 1
    body, concat = decode_user_data(packet[position:], dcs, udl, has_header)
    return {
        "sender": sender,
        "timestamp": timestamp,
        "body": body,
        "dcs": dcs,
        "concat": concat,
    }


class UfiModem:
    def __init__(self) -> None:
        self.device = None
        self.interface = None
        self.endpoint_in = None
        self.endpoint_out = None

    def __enter__(self) -> "UfiModem":
        device = usb.core.find(idVendor=VID, idProduct=PID)
        if device is None:
            raise ModemError("UFI003 modem (05c6:90b4) is not connected")
        configuration = device.get_active_configuration()
        interface = usb.util.find_descriptor(configuration, bInterfaceNumber=SERIAL_INTERFACE)
        if interface is None:
            raise ModemError("The modem's USB serial interface was not found")
        if device.is_kernel_driver_active(SERIAL_INTERFACE):
            raise ModemError("USB serial interface 2 is already in use by a kernel driver")
        try:
            usb.util.claim_interface(device, SERIAL_INTERFACE)
        except usb.core.USBError as exc:
            raise ModemError(f"Cannot claim the modem SMS interface: {exc}") from exc

        self.device = device
        self.interface = interface
        self.endpoint_out = usb.util.find_descriptor(
            interface,
            custom_match=lambda endpoint: (
                usb.util.endpoint_direction(endpoint.bEndpointAddress) == usb.util.ENDPOINT_OUT
                and usb.util.endpoint_type(endpoint.bmAttributes) == usb.util.ENDPOINT_TYPE_BULK
            ),
        )
        self.endpoint_in = usb.util.find_descriptor(
            interface,
            custom_match=lambda endpoint: (
                usb.util.endpoint_direction(endpoint.bEndpointAddress) == usb.util.ENDPOINT_IN
                and usb.util.endpoint_type(endpoint.bmAttributes) == usb.util.ENDPOINT_TYPE_BULK
            ),
        )
        if self.endpoint_out is None or self.endpoint_in is None:
            self.close()
            raise ModemError("The modem's USB bulk endpoints were not found")
        try:
            device.ctrl_transfer(
                0x21,
                0x20,
                0,
                SERIAL_INTERFACE,
                struct.pack("<IBBB", 115200, 0, 0, 8),
                timeout=1000,
            )
            device.ctrl_transfer(0x21, 0x22, 3, SERIAL_INTERFACE, None, timeout=1000)
        except usb.core.USBError:
            pass
        self.read_available(0.1)
        self.command("AT")
        self.command("ATE0")
        self.command("AT+CMEE=2")
        return self

    def __exit__(self, *_args: object) -> None:
        self.close()

    def close(self) -> None:
        if self.device is not None:
            try:
                usb.util.release_interface(self.device, SERIAL_INTERFACE)
            except usb.core.USBError:
                pass
            usb.util.dispose_resources(self.device)
        self.device = None

    def read_available(self, timeout: float = 0.2) -> bytes:
        if self.endpoint_in is None:
            return b""
        chunks: list[bytes] = []
        while True:
            try:
                chunks.append(
                    bytes(
                        self.endpoint_in.read(
                            self.endpoint_in.wMaxPacketSize,
                            timeout=max(1, int(timeout * 1000)),
                        )
                    )
                )
            except usb.core.USBTimeoutError:
                break
        return b"".join(chunks)

    def command(self, command: str, timeout: float = 3.0) -> str:
        if self.endpoint_out is None:
            raise ModemError("USB serial interface is closed")
        self.read_available(0.05)
        try:
            self.endpoint_out.write((command + "\r").encode("ascii"), timeout=1000)
        except usb.core.USBError as exc:
            raise ModemError(f"Failed to send {command!r}: {exc}") from exc

        deadline = time.monotonic() + timeout
        response = bytearray()
        while time.monotonic() < deadline:
            response.extend(self.read_available(min(0.2, max(0.01, deadline - time.monotonic()))))
            text = response.decode("utf-8", "replace").replace("\x00", "")
            if re.search(r"(?:^|\r?\n)(?:OK|ERROR|\+CM[ES] ERROR:.*)(?:\r?\n|$)", text):
                break
        text = response.decode("utf-8", "replace").replace("\x00", "")
        if not text.strip():
            raise ModemError(f"No response to {command!r}")
        if re.search(r"(?:^|\n)(?:ERROR|\+CM[ES] ERROR:.*)", text.replace("\r", "")):
            raise ModemError(f"Modem rejected {command!r}: {clean_response(text, command)}")
        return clean_response(text, command)

    def configure_sms(self, storage: str = "ME", notifications: bool = True) -> None:
        storage = storage.upper()
        if storage not in {"ME", "SM", "MT"}:
            raise ModemError("Storage must be ME, SM, or MT")
        self.command("AT+CMGF=0")
        receive_storage = "ME" if storage == "MT" else storage
        self.command(f'AT+CPMS="{storage}","{receive_storage}","{receive_storage}"')
        self.command("AT+CNMI=2,1,0,0,0" if notifications else "AT+CNMI=0,0,0,0,0")

    def select_sms_storage(self, storage: str) -> None:
        storage = storage.upper()
        if storage not in {"ME", "SM", "MT"}:
            raise ModemError("Storage must be ME, SM, or MT")
        self.command("AT+CMGF=0")
        # Supplying only mem1 changes the read/list storage while preserving
        # the configured receive storage (mem3) and notification mode.
        self.command(f'AT+CPMS="{storage}"')

    def list_messages(self, storage: str) -> list[SmsMessage]:
        self.select_sms_storage(storage)
        return parse_message_list(self.command("AT+CMGL=4", timeout=8.0), storage)

    def read_message(self, storage: str, index: int) -> SmsMessage:
        self.select_sms_storage(storage)
        response = self.command(f"AT+CMGR={index}", timeout=5.0)
        return parse_single_message(response, storage, index)


def clean_response(response: str, command: str = "") -> str:
    lines = [line.strip() for line in response.replace("\r", "\n").split("\n") if line.strip()]
    if lines and lines[0] == command:
        lines.pop(0)
    if lines and lines[-1] == "OK":
        lines.pop()
    return "\n".join(lines)


def parse_message_list(response: str, storage: str) -> list[SmsMessage]:
    lines = [line.strip() for line in response.splitlines() if line.strip()]
    messages: list[SmsMessage] = []
    position = 0
    while position < len(lines):
        match = re.match(r"^\+CMGL:\s*(\d+)\s*,\s*(\d+).*", lines[position])
        if not match:
            position += 1
            continue
        index, status_number = int(match.group(1)), int(match.group(2))
        position += 1
        if position >= len(lines) or not re.fullmatch(r"[0-9A-Fa-f]+", lines[position]):
            position += 1
            continue
        raw_pdu = lines[position]
        position += 1
        try:
            decoded = decode_pdu(raw_pdu)
            concat = decoded.get("concat")
            part = ""
            if isinstance(concat, dict):
                part = f"{concat['sequence']}/{concat['total']}"
            messages.append(
                SmsMessage(
                    storage=storage,
                    index=index,
                    status=STATUS_NAMES.get(status_number, str(status_number)),
                    sender=str(decoded.get("sender", "")),
                    timestamp=str(decoded.get("timestamp", "")),
                    body=str(decoded.get("body", "")),
                    part=part,
                    dcs=decoded.get("dcs") if isinstance(decoded.get("dcs"), int) else None,
                    raw_pdu=raw_pdu,
                )
            )
        except ModemError as exc:
            messages.append(
                SmsMessage(
                    storage=storage,
                    index=index,
                    status=STATUS_NAMES.get(status_number, str(status_number)),
                    sender="",
                    timestamp="",
                    body=f"[Could not decode message: {exc}]",
                    raw_pdu=raw_pdu,
                )
            )
    return messages


def parse_single_message(response: str, storage: str, index: int) -> SmsMessage:
    lines = [line.strip() for line in response.splitlines() if line.strip()]
    header_index = next((number for number, line in enumerate(lines) if line.startswith("+CMGR:")), -1)
    if header_index < 0 or header_index + 1 >= len(lines):
        raise ModemError(f"SMS {storage}:{index} was not found")
    status_match = re.match(r"^\+CMGR:\s*(\d+)", lines[header_index])
    status_number = int(status_match.group(1)) if status_match else -1
    raw_pdu = lines[header_index + 1]
    decoded = decode_pdu(raw_pdu)
    concat = decoded.get("concat")
    part = f"{concat['sequence']}/{concat['total']}" if isinstance(concat, dict) else ""
    return SmsMessage(
        storage=storage,
        index=index,
        status=STATUS_NAMES.get(status_number, str(status_number)),
        sender=str(decoded.get("sender", "")),
        timestamp=str(decoded.get("timestamp", "")),
        body=str(decoded.get("body", "")),
        part=part,
        dcs=decoded.get("dcs") if isinstance(decoded.get("dcs"), int) else None,
        raw_pdu=raw_pdu,
    )


def get_storages(value: str) -> list[str]:
    return ["ME", "SM"] if value.upper() == "ALL" else [value.upper()]


def print_messages(messages: list[SmsMessage], json_output: bool = False) -> None:
    if json_output:
        print(json.dumps([asdict(message) for message in messages], ensure_ascii=False, indent=2))
        return
    if not messages:
        print("No stored SMS messages.")
        return
    for message in messages:
        heading = f"[{message.storage}:{message.index}] {message.status}"
        if message.part:
            heading += f" part {message.part}"
        print(heading)
        print(f"From: {message.sender or '(unknown)'}")
        if message.timestamp:
            print(f"Time: {message.timestamp}")
        print(message.body)
        print()


def message_key(message: SmsMessage) -> tuple[str, int, str]:
    return message.storage, message.index, message.raw_pdu


def load_archive(path: Path) -> tuple[list[SmsMessage], set[tuple[str, int, str]]]:
    messages: list[SmsMessage] = []
    keys: set[tuple[str, int, str]] = set()
    if not path.exists():
        return messages, keys
    try:
        with path.open("r", encoding="utf-8") as archive:
            for line in archive:
                try:
                    record = json.loads(line)
                    message = SmsMessage(**record)
                except (json.JSONDecodeError, TypeError):
                    continue
                messages.append(message)
                keys.add(message_key(message))
    except OSError as exc:
        raise ModemError(f"Cannot read SMS archive {path}: {exc}") from exc
    return messages, keys


def append_archive(path: Path, message: SmsMessage) -> None:
    try:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(descriptor, "a", encoding="utf-8") as archive:
            archive.write(json.dumps(asdict(message), ensure_ascii=False, separators=(",", ":")) + "\n")
        os.chmod(path, 0o600)
    except OSError as exc:
        raise ModemError(f"Cannot write SMS archive {path}: {exc}") from exc


def message_fingerprint(message: SmsMessage) -> str:
    material = message.raw_pdu.strip().upper()
    if not material:
        material = json.dumps(
            {
                "sender": message.sender,
                "timestamp": message.timestamp,
                "body": message.body,
                "part": message.part,
            },
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
    return hashlib.sha256(material.encode("utf-8")).hexdigest()


def write_private_json(path: Path, value: dict[str, object]) -> None:
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    try:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(path.parent, 0o700)
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8") as output:
                json.dump(value, output, ensure_ascii=False, indent=2)
                output.write("\n")
        except BaseException:
            try:
                os.close(descriptor)
            except OSError:
                pass
            raise
        os.replace(temporary, path)
        os.chmod(path, 0o600)
    except OSError as exc:
        raise ModemError(f"Cannot write private configuration {path}: {exc}") from exc
    finally:
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass


def load_telegram_config(path: Path) -> TelegramConfig | None:
    if not path.exists():
        return None
    try:
        with path.open("r", encoding="utf-8") as source:
            value = json.load(source)
    except (OSError, json.JSONDecodeError) as exc:
        raise TelegramError(f"Cannot read Telegram configuration {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise TelegramError(f"Telegram configuration {path} is not a JSON object")
    if value.get("enabled", True) is False:
        return None
    token = value.get("bot_token")
    chat_id = value.get("chat_id")
    if not isinstance(token, str) or not re.fullmatch(r"[0-9]+:[A-Za-z0-9_-]+", token):
        raise TelegramError(f"Telegram configuration {path} has an invalid bot token")
    if not isinstance(chat_id, (int, str)) or isinstance(chat_id, bool) or str(chat_id).strip() == "":
        raise TelegramError(f"Telegram configuration {path} has an invalid chat ID")
    if isinstance(chat_id, str) and re.fullmatch(r"-?[0-9]+", chat_id.strip()):
        chat_id = int(chat_id)
    return TelegramConfig(
        bot_token=token,
        chat_id=chat_id,
        protect_content=bool(value.get("protect_content", True)),
    )


def telegram_api(token: str, method: str, payload: dict[str, object], timeout: float = 10.0) -> object:
    if not re.fullmatch(r"[0-9]+:[A-Za-z0-9_-]+", token):
        raise TelegramError("The Telegram bot token format is invalid")
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": "ufi-sms/1"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw_response = response.read(1_000_000)
    except urllib.error.HTTPError as exc:
        try:
            error_value = json.loads(exc.read(100_000).decode("utf-8", "replace"))
            description = str(error_value.get("description", "request rejected"))
        except (OSError, UnicodeError, json.JSONDecodeError, AttributeError):
            description = "request rejected"
        description = description.replace(token, "[redacted]")
        raise TelegramError(f"Telegram API {method} failed (HTTP {exc.code}): {description}") from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise TelegramError(f"Telegram API {method} could not be reached") from None
    try:
        value = json.loads(raw_response.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError):
        raise TelegramError(f"Telegram API {method} returned an invalid response") from None
    if not isinstance(value, dict) or value.get("ok") is not True:
        description = str(value.get("description", "request failed")) if isinstance(value, dict) else "request failed"
        description = description.replace(token, "[redacted]")
        raise TelegramError(f"Telegram API {method} failed: {description}")
    return value.get("result")


def telegram_message_text(message: SmsMessage) -> str:
    lines = ["📩 SMS", f"From: {message.sender or '(unknown)'}"]
    if message.timestamp:
        lines.append(f"Time: {message.timestamp}")
    if message.part:
        lines.append(f"Part: {message.part}")
    lines.extend(("", message.body))
    text = "\n".join(lines)
    return text if len(text) <= 4096 else text[:4095] + "…"


def send_telegram_text(config: TelegramConfig, text: str) -> None:
    telegram_api(
        config.bot_token,
        "sendMessage",
        {
            "chat_id": config.chat_id,
            "text": text if len(text) <= 4096 else text[:4095] + "…",
            "protect_content": config.protect_content,
            "link_preview_options": {"is_disabled": True},
        },
    )


def load_telegram_sent(path: Path) -> set[str]:
    if not path.exists():
        return set()
    try:
        with path.open("r", encoding="ascii") as source:
            return {line.strip() for line in source if re.fullmatch(r"[0-9a-f]{64}\n?", line)}
    except OSError as exc:
        raise TelegramError(f"Cannot read Telegram delivery state {path}: {exc}") from exc


def append_telegram_sent(path: Path, fingerprints: Iterable[str]) -> set[str]:
    values = list(dict.fromkeys(fingerprints))
    if not values:
        return set()
    try:
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(path.parent, 0o700)
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(descriptor, "a", encoding="ascii") as output:
            for fingerprint in values:
                output.write(fingerprint + "\n")
        os.chmod(path, 0o600)
    except OSError as exc:
        raise TelegramError(f"Cannot write Telegram delivery state {path}: {exc}") from exc
    return set(values)


class TelegramForwarder:
    def __init__(self, config_path: Path, state_path: Path):
        self.config_path = config_path
        self.state_path = state_path
        self.config: TelegramConfig | None = None
        self.sent = load_telegram_sent(state_path)
        self.last_warning = ""
        self.last_warning_time = 0.0
        self.refresh()

    def warn(self, error: Exception) -> None:
        text = str(error)
        now = time.monotonic()
        if text != self.last_warning or now - self.last_warning_time >= 300:
            print(f"Telegram warning: {text}", file=sys.stderr)
            self.last_warning = text
            self.last_warning_time = now

    def refresh(self) -> None:
        try:
            self.config = load_telegram_config(self.config_path)
        except TelegramError as exc:
            self.config = None
            self.warn(exc)

    def remember(self, message: SmsMessage) -> None:
        fingerprint = message_fingerprint(message)
        if fingerprint in self.sent:
            return
        try:
            self.sent.update(append_telegram_sent(self.state_path, [fingerprint]))
        except TelegramError as exc:
            self.warn(exc)

    def forward(self, message: SmsMessage) -> bool:
        fingerprint = message_fingerprint(message)
        if self.config is None or fingerprint in self.sent:
            return False
        try:
            send_telegram_text(self.config, telegram_message_text(message))
            self.sent.update(append_telegram_sent(self.state_path, [fingerprint]))
        except TelegramError as exc:
            self.warn(exc)
            return False
        return True

    def forward_pending(self, messages: Iterable[SmsMessage]) -> None:
        if self.config is None:
            return
        for message in messages:
            fingerprint = message_fingerprint(message)
            if fingerprint not in self.sent and not self.forward(message):
                break


def desktop_notify(message: SmsMessage) -> None:
    notify_send = shutil.which("notify-send")
    if notify_send is None:
        return
    sender = message.sender or "unknown sender"
    body = message.body if len(message.body) <= 500 else message.body[:497] + "..."
    try:
        subprocess.run(
            [notify_send, "--app-name=UFI SMS", f"SMS from {sender}", body],
            check=False,
            timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass


def telegram_chat_candidates(result: object) -> list[dict[str, object]]:
    if not isinstance(result, list):
        return []
    candidates: list[dict[str, object]] = []
    seen: set[str] = set()
    for update in reversed(result):
        if not isinstance(update, dict):
            continue
        messages = [update.get(name) for name in ("message", "edited_message", "channel_post")]
        callback = update.get("callback_query")
        if isinstance(callback, dict):
            messages.append(callback.get("message"))
        for message in messages:
            if not isinstance(message, dict) or not isinstance(message.get("chat"), dict):
                continue
            chat = message["chat"]
            chat_id = chat.get("id")
            if not isinstance(chat_id, int) or str(chat_id) in seen:
                continue
            seen.add(str(chat_id))
            candidates.append(chat)
    private = [chat for chat in candidates if chat.get("type") == "private"]
    return private or candidates


def telegram_chat_label(chat: dict[str, object]) -> str:
    name = " ".join(str(chat.get(key, "")).strip() for key in ("first_name", "last_name")).strip()
    username = str(chat.get("username", "")).strip()
    if username:
        name = f"{name} (@{username})" if name else f"@{username}"
    return name or str(chat.get("title", "")).strip() or "unnamed chat"


def command_telegram_setup(args: argparse.Namespace) -> int:
    token = getpass.getpass("Telegram bot token (hidden): ").strip()
    if not token:
        raise TelegramError("No bot token was entered")
    bot = telegram_api(token, "getMe", {})
    if not isinstance(bot, dict) or not isinstance(bot.get("username"), str):
        raise TelegramError("Telegram did not return a valid bot identity")
    username = bot["username"]
    print(f"Connected to @{username}.")

    if args.chat_id is not None:
        chat_id: int | str = int(args.chat_id) if re.fullmatch(r"-?[0-9]+", args.chat_id) else args.chat_id
    else:
        print(f"Open https://t.me/{username}, press Start, and send /start to the bot.")
        try:
            input("Press Enter here after the message has been sent: ")
        except EOFError:
            raise TelegramError("Interactive input ended before a Telegram chat could be selected") from None
        chats = telegram_chat_candidates(telegram_api(token, "getUpdates", {"limit": 100, "timeout": 0}))
        if not chats:
            raise TelegramError(
                "No bot chat was found. Send /start to the bot and run setup again, or use --chat-id."
            )
        chat = chats[0]
        chat_id = int(chat["id"])
        label = telegram_chat_label(chat)
        if not args.yes:
            try:
                answer = input(f"Use {label} (chat {chat_id})? [Y/n] ").strip().lower()
            except EOFError:
                raise TelegramError("Interactive input ended before the Telegram chat was confirmed") from None
            if answer not in ("", "y", "yes"):
                raise TelegramError("Telegram setup cancelled; rerun with --chat-id for a different chat")

    config = TelegramConfig(bot_token=token, chat_id=chat_id, protect_content=True)
    send_telegram_text(config, "✅ UFI SMS forwarding is connected. Future SMS messages will appear here.")

    state_path = Path(args.telegram_state).expanduser()
    if not args.forward_existing:
        messages, _keys = load_archive(Path(args.archive).expanduser())
        already_sent = load_telegram_sent(state_path)
        fingerprints = [message_fingerprint(message) for message in messages]
        append_telegram_sent(state_path, [value for value in fingerprints if value not in already_sent])

    config_path = Path(args.telegram_config).expanduser()
    write_private_json(
        config_path,
        {
            "version": 1,
            "enabled": True,
            "bot_token": token,
            "chat_id": chat_id,
            "protect_content": True,
        },
    )
    print(f"Telegram forwarding configured in {config_path} (private file mode 0600).")
    if not args.forward_existing:
        print("Existing archived SMS messages were marked as already handled; only new SMS messages will be forwarded.")
    if not args.no_restart and shutil.which("systemctl"):
        try:
            result = subprocess.run(
                ["systemctl", "--user", "try-restart", "ufi-sms.service"],
                check=False,
                timeout=15,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if result.returncode == 0:
                print("The background SMS receiver was restarted with Telegram forwarding enabled.")
            else:
                print("Restart the receiver with: systemctl --user restart ufi-sms.service")
        except (OSError, subprocess.TimeoutExpired):
            print("Restart the receiver with: systemctl --user restart ufi-sms.service")
    return 0


def command_telegram_status(args: argparse.Namespace) -> int:
    config_path = Path(args.telegram_config).expanduser()
    config = load_telegram_config(config_path)
    if config is None:
        print(f"Telegram forwarding is not configured at {config_path}.")
        return 0
    print(f"Telegram forwarding is configured for chat {config.chat_id}.")
    if args.check:
        bot = telegram_api(config.bot_token, "getMe", {})
        username = bot.get("username") if isinstance(bot, dict) else None
        print(f"Bot API check succeeded{f' for @{username}' if username else ''}.")
    return 0


def command_telegram_test(args: argparse.Namespace) -> int:
    config_path = Path(args.telegram_config).expanduser()
    config = load_telegram_config(config_path)
    if config is None:
        raise TelegramError("Telegram forwarding is not configured; run ufi-sms telegram-setup")
    send_telegram_text(config, "🧪 UFI SMS forwarding test succeeded.")
    print("Telegram test message sent.")
    return 0


def deliver_message(
    message: SmsMessage,
    args: argparse.Namespace,
    archived: set[tuple[str, int, str]],
    saved_messages: list[SmsMessage],
    telegram: TelegramForwarder | None,
) -> None:
    key = message_key(message)
    if args.archive and key not in archived:
        append_archive(Path(args.archive).expanduser(), message)
        archived.add(key)
        saved_messages.append(message)
    if not args.quiet:
        print_messages([message], args.json)
        sys.stdout.flush()
    if args.notify:
        desktop_notify(message)
    if telegram is not None:
        telegram.forward(message)


def command_status(args: argparse.Namespace) -> int:
    with UfiModem() as modem:
        queries = ["AT+CPIN?", "AT+COPS?", "AT+CSQ", "AT+CREG?", "AT+CEREG?", "AT+CPMS?", "AT+CNMI?"]
        for query in queries:
            print(query)
            print(modem.command(query))
    return 0


def command_setup(args: argparse.Namespace) -> int:
    with UfiModem() as modem:
        modem.configure_sms(args.storage, notifications=True)
        print(modem.command("AT+CPMS?"))
        print(modem.command("AT+CNMI?"))
    print("SMS storage and new-message notifications are enabled for this USB AT interface.")
    return 0


def command_inbox(args: argparse.Namespace) -> int:
    messages: list[SmsMessage] = []
    with UfiModem() as modem:
        for storage in get_storages(args.storage):
            messages.extend(modem.list_messages(storage))
    messages.sort(key=lambda item: (item.timestamp, item.storage, item.index))
    print_messages(messages, args.json)
    return 0


def command_read(args: argparse.Namespace) -> int:
    with UfiModem() as modem:
        message = modem.read_message(args.storage, args.index)
    print_messages([message], args.json)
    return 0


def command_delete(args: argparse.Namespace) -> int:
    if not args.yes:
        raise ModemError("Refusing to delete without --yes")
    with UfiModem() as modem:
        modem.configure_sms(args.storage)
        modem.command(f"AT+CMGD={args.index}")
    print(f"Deleted SMS {args.storage}:{args.index}.")
    return 0


def command_saved(args: argparse.Namespace) -> int:
    messages, _keys = load_archive(Path(args.archive).expanduser())
    if args.limit is not None:
        messages = messages[-args.limit :]
    print_messages(messages, args.json)
    return 0


def command_watch(args: argparse.Namespace) -> int:
    known: set[tuple[str, int, str]] = set()
    archived: set[tuple[str, int, str]] = set()
    saved_messages: list[SmsMessage] = []
    baseline_complete = False
    if args.archive:
        saved_messages, archived = load_archive(Path(args.archive).expanduser())
    telegram = (
        TelegramForwarder(Path(args.telegram_config).expanduser(), Path(args.telegram_state).expanduser())
        if args.telegram
        else None
    )
    if telegram is not None:
        telegram.forward_pending(saved_messages)
    if not args.quiet:
        print("Watching for SMS messages. Press Ctrl+C to stop.")
    while True:
        try:
            with UfiModem() as modem:
                for storage in ("ME", "SM"):
                    for message in modem.list_messages(storage):
                        key = message_key(message)
                        is_new = key not in known
                        known.add(key)
                        if not is_new:
                            continue
                        if baseline_complete:
                            # Messages first discovered after a reconnect are new,
                            # even though they are present during this connection's scan.
                            deliver_message(message, args, archived, saved_messages, telegram)
                        else:
                            if args.archive and key not in archived:
                                append_archive(Path(args.archive).expanduser(), message)
                                archived.add(key)
                                saved_messages.append(message)
                                if telegram is not None:
                                    # Do not forward messages that predate the first
                                    # successful watcher scan.
                                    telegram.remember(message)
                            if args.existing and not args.quiet:
                                print_messages([message], args.json)
                baseline_complete = True
                modem.configure_sms(args.storage, notifications=True)
                last_poll = 0.0
                last_telegram_retry = time.monotonic()
                while True:
                    incoming = modem.read_available(0.5).decode("utf-8", "replace").replace("\x00", "")
                    notification = re.search(r'\+CMTI:\s*"(ME|SM)"\s*,\s*(\d+)', incoming)
                    if notification:
                        storage, index = notification.group(1), int(notification.group(2))
                        try:
                            message = modem.read_message(storage, index)
                            key = message_key(message)
                            if key not in known:
                                known.add(key)
                                deliver_message(message, args, archived, saved_messages, telegram)
                        except ModemError as exc:
                            print(f"Warning: {exc}", file=sys.stderr)
                    now = time.monotonic()
                    if now - last_poll >= args.interval:
                        for storage in ("ME", "SM"):
                            for message in modem.list_messages(storage):
                                key = message_key(message)
                                if key not in known:
                                    known.add(key)
                                    deliver_message(message, args, archived, saved_messages, telegram)
                        last_poll = now
                    if telegram is not None and now - last_telegram_retry >= args.telegram_retry:
                        telegram.refresh()
                        telegram.forward_pending(saved_messages)
                        last_telegram_retry = now
        except KeyboardInterrupt:
            print("\nStopped.")
            return 0
        except (ModemError, usb.core.USBError) as exc:
            if not args.reconnect:
                raise ModemError(str(exc)) from exc
            print(f"Waiting for modem: {exc}", file=sys.stderr)
            time.sleep(3)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Receive and read SMS messages from a Qualcomm UFI003 USB modem")
    subparsers = parser.add_subparsers(dest="command", required=True)

    status_parser = subparsers.add_parser("status", help="Show SIM, network, signal, and SMS status")
    status_parser.set_defaults(handler=command_status)

    setup_parser = subparsers.add_parser("setup", help="Enable SMS storage and new-message indications")
    setup_parser.add_argument("--storage", choices=("ME", "SM"), default="ME", help="Receive into device or SIM memory")
    setup_parser.set_defaults(handler=command_setup)

    inbox_parser = subparsers.add_parser("inbox", help="List stored messages")
    inbox_parser.add_argument("--storage", choices=("ME", "SM", "all"), default="all")
    inbox_parser.add_argument("--json", action="store_true")
    inbox_parser.set_defaults(handler=command_inbox)

    read_parser = subparsers.add_parser("read", help="Read one stored message")
    read_parser.add_argument("index", type=int)
    read_parser.add_argument("--storage", choices=("ME", "SM"), default="ME")
    read_parser.add_argument("--json", action="store_true")
    read_parser.set_defaults(handler=command_read)

    delete_parser = subparsers.add_parser("delete", help="Delete one stored message")
    delete_parser.add_argument("index", type=int)
    delete_parser.add_argument("--storage", choices=("ME", "SM"), default="ME")
    delete_parser.add_argument("--yes", action="store_true", help="Confirm permanent deletion")
    delete_parser.set_defaults(handler=command_delete)

    saved_parser = subparsers.add_parser("saved", help="Show messages archived on this computer")
    saved_parser.add_argument("--archive", default=str(DEFAULT_ARCHIVE))
    saved_parser.add_argument("--limit", type=int)
    saved_parser.add_argument("--json", action="store_true")
    saved_parser.set_defaults(handler=command_saved)

    telegram_setup_parser = subparsers.add_parser(
        "telegram-setup", help="Privately connect a Telegram bot and enable SMS forwarding"
    )
    telegram_setup_parser.add_argument("--chat-id", help="Use a known Telegram chat ID instead of discovering it")
    telegram_setup_parser.add_argument("--yes", action="store_true", help="Accept the discovered chat without confirmation")
    telegram_setup_parser.add_argument(
        "--forward-existing", action="store_true", help="Also forward SMS messages already in the local archive"
    )
    telegram_setup_parser.add_argument("--archive", default=str(DEFAULT_ARCHIVE))
    telegram_setup_parser.add_argument("--telegram-config", default=str(DEFAULT_TELEGRAM_CONFIG))
    telegram_setup_parser.add_argument("--telegram-state", default=str(DEFAULT_TELEGRAM_STATE))
    telegram_setup_parser.add_argument("--no-restart", action="store_true", help="Do not restart the user service")
    telegram_setup_parser.set_defaults(handler=command_telegram_setup)

    telegram_status_parser = subparsers.add_parser("telegram-status", help="Show Telegram forwarding status")
    telegram_status_parser.add_argument("--telegram-config", default=str(DEFAULT_TELEGRAM_CONFIG))
    telegram_status_parser.add_argument("--check", action="store_true", help="Also verify the bot token with Telegram")
    telegram_status_parser.set_defaults(handler=command_telegram_status)

    telegram_test_parser = subparsers.add_parser("telegram-test", help="Send a Telegram test message")
    telegram_test_parser.add_argument("--telegram-config", default=str(DEFAULT_TELEGRAM_CONFIG))
    telegram_test_parser.set_defaults(handler=command_telegram_test)

    watch_parser = subparsers.add_parser("watch", help="Print new messages as they arrive")
    watch_parser.add_argument("--storage", choices=("ME", "SM"), default="ME")
    watch_parser.add_argument("--interval", type=float, default=5.0, help="Fallback polling interval in seconds")
    watch_parser.add_argument("--existing", action="store_true", help="Print messages already stored before watching")
    watch_parser.add_argument("--json", action="store_true")
    watch_parser.add_argument("--notify", action="store_true", help="Show a desktop notification for each new message")
    watch_parser.add_argument("--quiet", action="store_true", help="Do not print message bodies to standard output")
    watch_parser.add_argument("--archive", default=str(DEFAULT_ARCHIVE), help="Append received messages to this private JSONL file")
    watch_parser.add_argument("--no-archive", dest="archive", action="store_const", const=None)
    watch_parser.add_argument("--telegram-config", default=str(DEFAULT_TELEGRAM_CONFIG))
    watch_parser.add_argument("--telegram-state", default=str(DEFAULT_TELEGRAM_STATE))
    watch_parser.add_argument("--telegram-retry", type=float, default=30.0, help="Telegram retry interval in seconds")
    watch_parser.add_argument("--no-telegram", dest="telegram", action="store_false", help="Disable Telegram forwarding")
    watch_parser.add_argument("--no-reconnect", dest="reconnect", action="store_false", help="Exit if the modem disconnects")
    watch_parser.set_defaults(handler=command_watch, reconnect=True, telegram=True)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        return int(args.handler(args))
    except ModemError as exc:
        print(f"ufi-sms: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
