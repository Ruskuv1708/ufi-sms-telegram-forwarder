#!/usr/bin/env python3
"""UFI Phone desktop app for Linux and Windows."""

from __future__ import annotations

import argparse
import base64
import json
import os
import platform
import socket
import sys
import threading
from datetime import datetime
from pathlib import Path
from typing import Any

PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

from desktop.model import CallHistory, group_conversations, normalize_number
from ufi_setup import PAIRING_KIND, PAIRING_VERSION, SetupError, write_pairing_file
from ufi_voice import (
    CONFIG_PATH,
    DOWNLINK_PORT,
    UPLINK_PORT,
    AudioSession,
    VoiceError,
    authenticate_socket,
    control_request,
    gateway_status,
    load_config,
    read_line,
    save_config,
    validate_config,
)

BACKGROUND = "#F7F9FE"
SURFACE = "#FFFFFF"
TEXT = "#101B35"
MUTED = "#627087"
BLUE = "#0B67D1"
BLUE_SOFT = "#E7F0FF"
GREEN = "#159455"
GREEN_SOFT = "#E5F6EC"
AMBER = "#A15C00"
AMBER_SOFT = "#FFF1D8"
RED = "#D92D20"
RED_SOFT = "#FFE9E7"
DIVIDER = "#E3E8F1"
MAX_PAIRING_BYTES = 16 * 1024


def bundled_asset(relative: str) -> Path:
    root = Path(getattr(sys, "_MEIPASS", PROJECT_DIR))
    return root / relative


def encode_command_value(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii")


def sms_messages(config: dict[str, Any]) -> list[dict[str, Any]]:
    payload = json.loads(control_request(config, "SMS_LIST"))
    messages = payload.get("messages", [])
    return messages if isinstance(messages, list) else []


def read_pairing_file(path: Path) -> dict[str, str]:
    if path.stat().st_size > MAX_PAIRING_BYTES:
        raise VoiceError("The selected pairing file is too large")
    document = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(document, dict):
        raise VoiceError("The selected file is not a UFI Phone pairing file")
    if document.get("kind") != PAIRING_KIND or document.get("version") != PAIRING_VERSION:
        raise VoiceError("This pairing file format is not supported")
    config = {"host": document.get("host"), "token": document.get("token")}
    validate_config(config)
    return {"host": str(config["host"]), "token": str(config["token"])}


def friendly_connection_error(error: object) -> str:
    detail = str(error)
    lowered = detail.lower()
    if "not configured" in lowered:
        return "No modem pairing is saved on this computer yet."
    if "non-private configuration" in lowered:
        return "The saved pairing is not private. Import it again to repair local permissions."
    if "invalid token" in lowered or "invalid host" in lowered:
        return "The saved pairing is invalid. Import a fresh pairing file."
    if "incompatible gateway" in lowered or "protocol version" in lowered:
        return "The modem software and this app need to be updated together."
    return "The modem did not respond. Join its network, then retry the saved pairing."


class SoundDeviceAudioSession:
    """Portable PortAudio bridge used by packaged Windows builds."""

    def __init__(self, config: dict[str, Any]) -> None:
        import sounddevice  # type: ignore[import-not-found]

        self.sd = sounddevice
        self.config = config
        self.stop_event = threading.Event()
        self.sockets: list[socket.socket] = []

    def start(self) -> None:
        threading.Thread(target=self._downlink, daemon=True).start()
        threading.Thread(target=self._uplink, daemon=True).start()

    def stop(self) -> None:
        self.stop_event.set()
        for connection in self.sockets:
            try:
                connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            connection.close()
        self.sockets.clear()

    def _downlink(self) -> None:
        connection, stream = authenticate_socket(self.config, DOWNLINK_PORT)
        self.sockets.append(connection)
        try:
            response = read_line(stream).decode("ascii", "replace")
            if not response.startswith("OK 8000 1 S16LE"):
                raise VoiceError(response)
            connection.settimeout(None)
            with self.sd.RawOutputStream(samplerate=8000, channels=1, dtype="int16") as output:
                while not self.stop_event.is_set():
                    chunk = stream.read(2048)
                    if not chunk:
                        break
                    output.write(chunk)
        finally:
            stream.close()
            connection.close()

    def _uplink(self) -> None:
        connection, stream = authenticate_socket(self.config, UPLINK_PORT)
        self.sockets.append(connection)
        try:
            response = read_line(stream).decode("ascii", "replace")
            if not response.startswith("OK 48000 1 S16LE"):
                raise VoiceError(response)
            connection.settimeout(None)
            with self.sd.RawInputStream(samplerate=48000, channels=1, dtype="int16") as source:
                while not self.stop_event.is_set():
                    chunk, _overflowed = source.read(1920)
                    if not chunk:
                        break
                    stream.write(bytes(chunk))
        finally:
            stream.close()
            connection.close()


def portable_audio(config: dict[str, Any]) -> Any:
    try:
        return SoundDeviceAudioSession(config)
    except (ImportError, OSError):
        if platform.system() == "Linux":
            return AudioSession(config)
        raise VoiceError("No audio backend is installed; reinstall the full desktop package")


class UfiPhoneApp:
    def __init__(
        self,
        config: dict[str, Any] | None,
        startup_detail: str = "",
        initial_page: str = "calls",
    ) -> None:
        import tkinter as tk
        from tkinter import filedialog, messagebox, ttk

        self.tk = tk
        self.ttk = ttk
        self.filedialog = filedialog
        self.messagebox = messagebox
        self.config = config
        self.startup_detail = startup_detail
        self.root = tk.Tk()
        self.root.title("UFI Phone")
        self.root.geometry("1040x700")
        self.root.minsize(820, 580)
        self.root.configure(bg=BACKGROUND)
        try:
            self.window_icon = tk.PhotoImage(file=str(bundled_asset("assets/ufi-phone.png")))
            self.root.iconphoto(True, self.window_icon)
        except tk.TclError:
            self.window_icon = None
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.audio: Any = None
        self.command_lock = threading.Lock()
        self.pending_commands: set[str] = set()
        self.polling = False
        self.poll_started = False
        self.current_page = ""
        self.last_state = ""
        self.status: dict[str, Any] = {}
        self.messages: list[dict[str, Any]] = []
        self.conversations: list[dict[str, Any]] = []
        self.selected_address = ""
        self.history = CallHistory(CONFIG_PATH.parent / "desktop-call-history.json")
        self.nav_buttons: dict[str, Any] = {}
        self._configure_styles()
        self._build_shell()
        if config is None:
            self.show_page("setup")
        else:
            self.show_page(initial_page)
            self.start_polling()

    def _configure_styles(self) -> None:
        style = self.ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except self.tk.TclError:
            pass
        style.configure("TFrame", background=BACKGROUND)
        style.configure("Surface.TFrame", background=SURFACE)
        style.configure("Card.TFrame", background=SURFACE)
        style.configure("Status.TFrame", background="#F3F7FD")
        style.configure("TLabel", background=BACKGROUND, foreground=TEXT, font=("Segoe UI", 11))
        style.configure(
            "Surface.TLabel", background=SURFACE, foreground=TEXT, font=("Segoe UI", 11)
        )
        style.configure("Title.TLabel", font=("Segoe UI", 24, "bold"), foreground=TEXT)
        style.configure(
            "Heading.TLabel",
            background=SURFACE,
            font=("Segoe UI", 20, "bold"),
            foreground=TEXT,
        )
        style.configure(
            "Section.TLabel",
            background=SURFACE,
            font=("Segoe UI", 14, "bold"),
            foreground=TEXT,
        )
        style.configure(
            "CardTitle.TLabel",
            background=SURFACE,
            font=("Segoe UI", 16, "bold"),
            foreground=TEXT,
        )
        style.configure(
            "StatusTitle.TLabel",
            background="#F3F7FD",
            font=("Segoe UI", 16, "bold"),
            foreground=TEXT,
        )
        style.configure(
            "StatusMuted.TLabel",
            background="#F3F7FD",
            foreground=MUTED,
            font=("Segoe UI", 10),
        )
        style.configure("Muted.TLabel", foreground=MUTED)
        style.configure(
            "SurfaceMuted.TLabel", background=SURFACE, foreground=MUTED, font=("Segoe UI", 10)
        )
        style.configure(
            "Eyebrow.TLabel",
            background=SURFACE,
            foreground=MUTED,
            font=("Segoe UI", 9, "bold"),
        )
        style.configure(
            "Nav.TButton",
            background=SURFACE,
            foreground=MUTED,
            borderwidth=0,
            relief="flat",
            focusthickness=0,
            font=("Segoe UI", 11),
            padding=(18, 14),
            anchor="w",
        )
        style.map("Nav.TButton", background=[("active", "#F0F4FA")], foreground=[("active", TEXT)])
        style.configure(
            "NavActive.TButton",
            background=BLUE_SOFT,
            foreground=BLUE,
            borderwidth=0,
            relief="flat",
            focusthickness=0,
            font=("Segoe UI", 11, "bold"),
            padding=(18, 14),
            anchor="w",
        )
        style.map("NavActive.TButton", background=[("active", "#DCE9FC")])
        style.configure(
            "Primary.TButton",
            background=BLUE,
            foreground="white",
            borderwidth=0,
            relief="flat",
            focusthickness=1,
            font=("Segoe UI", 10, "bold"),
            padding=(16, 11),
        )
        style.map("Primary.TButton", background=[("active", "#095AB8"), ("disabled", "#A9C6EA")])
        style.configure(
            "Secondary.TButton",
            background="#EEF3FB",
            foreground=TEXT,
            borderwidth=0,
            relief="flat",
            focusthickness=1,
            font=("Segoe UI", 10),
            padding=(14, 10),
        )
        style.map("Secondary.TButton", background=[("active", "#DFE8F5")])
        style.configure(
            "Dial.TButton",
            background="#EEF3FB",
            foreground=TEXT,
            borderwidth=0,
            relief="flat",
            focusthickness=1,
            font=("Segoe UI", 17),
            padding=(20, 15),
        )
        style.map("Dial.TButton", background=[("active", "#DCE7F5")])
        style.configure(
            "Danger.TButton",
            background=RED,
            foreground="white",
            borderwidth=0,
            relief="flat",
            focusthickness=1,
            font=("Segoe UI", 10, "bold"),
            padding=(16, 11),
        )
        style.map("Danger.TButton", background=[("active", "#B42318"), ("disabled", "#E5AAA5")])
        style.configure(
            "TEntry",
            fieldbackground="#F3F6FB",
            foreground=TEXT,
            bordercolor=DIVIDER,
            lightcolor=DIVIDER,
            darkcolor=DIVIDER,
            padding=(10, 9),
        )
        style.configure(
            "Treeview",
            rowheight=42,
            font=("Segoe UI", 10),
            background=SURFACE,
            fieldbackground=SURFACE,
            borderwidth=0,
            relief="flat",
        )
        style.map("Treeview", background=[("selected", BLUE_SOFT)], foreground=[("selected", TEXT)])
        style.configure(
            "Treeview.Heading",
            background="#F1F5FA",
            foreground=MUTED,
            borderwidth=0,
            relief="flat",
            font=("Segoe UI", 9, "bold"),
            padding=(8, 9),
        )

    def _build_shell(self) -> None:
        header = self.ttk.Frame(self.root, padding=(28, 16, 28, 10))
        header.pack(fill="x")
        if self.window_icon is not None:
            self.header_icon = self.window_icon.subsample(16, 16)
            self.ttk.Label(header, image=self.header_icon).pack(side="left", padx=(0, 12))
        self.ttk.Label(header, text="UFI Phone", style="Title.TLabel").pack(side="left")
        self.connection_button = self.ttk.Button(
            header,
            text="Connection",
            style="Secondary.TButton",
            command=lambda: self.show_page("setup"),
        )
        self.connection_button.pack(side="right", padx=(12, 0))
        self.connection_label = self.tk.Label(
            header,
            text="Connecting…" if self.config else "Setup needed",
            bg=GREEN_SOFT if self.config else RED_SOFT,
            fg=GREEN if self.config else RED,
            font=("Segoe UI", 10, "bold"),
            padx=14,
            pady=8,
            borderwidth=0,
        )
        self.connection_label.pack(side="right")

        body = self.ttk.Frame(self.root)
        body.pack(fill="both", expand=True, padx=22, pady=(0, 22))
        navigation = self.ttk.Frame(body, style="Surface.TFrame", width=184, padding=(10, 18))
        navigation.pack(side="left", fill="y", padx=(0, 16))
        navigation.pack_propagate(False)
        self.ttk.Label(navigation, text="COMMUNICATION", style="Eyebrow.TLabel").pack(
            anchor="w", padx=10, pady=(0, 9)
        )
        for label, page in (("Calls", "calls"), ("Keypad", "keypad"), ("Messages", "messages")):
            button = self.ttk.Button(
                navigation,
                text=label,
                style="Nav.TButton",
                command=lambda value=page: self.show_page(value),
            )
            button.pack(fill="x", pady=3)
            self.nav_buttons[page] = button
            if self.config is None:
                button.configure(state="disabled")
        self.ttk.Label(
            navigation,
            text="Private modem LAN\nNo cloud required",
            style="SurfaceMuted.TLabel",
            justify="left",
        ).pack(side="bottom", anchor="w", padx=10, pady=(0, 6))
        self.page_host = self.ttk.Frame(body, style="Surface.TFrame", padding=(28, 20))
        self.page_host.pack(side="left", fill="both", expand=True)

    def show_page(self, page: str) -> None:
        self.current_page = page
        for name, button in self.nav_buttons.items():
            button.configure(style="NavActive.TButton" if name == page else "Nav.TButton")
        self.connection_button.configure(
            style="NavActive.TButton" if page == "setup" else "Secondary.TButton"
        )
        for child in self.page_host.winfo_children():
            child.destroy()
        if page == "calls":
            self._build_calls()
        elif page == "keypad":
            self._build_keypad()
        elif page == "messages":
            self._build_messages()
        else:
            self._build_setup()

    def _build_setup(self) -> None:
        self.ttk.Label(self.page_host, text="GET STARTED", style="Eyebrow.TLabel").pack(
            anchor="w"
        )
        self.ttk.Label(
            self.page_host, text="Connect your modem", style="Heading.TLabel"
        ).pack(anchor="w", pady=(5, 4))
        self.ttk.Label(
            self.page_host,
            text="Pair once. Calls and messages stay on the modem's private network.",
            style="SurfaceMuted.TLabel",
            wraplength=480,
            justify="left",
        ).pack(anchor="w", pady=(0, 14))

        status_card = self.ttk.Frame(self.page_host, style="Status.TFrame", padding=(20, 14))
        status_card.pack(fill="x")
        title = "Pairing saved on this computer" if self.config else "One-time setup required"
        detail = (
            "UFI Phone will reconnect automatically whenever this computer joins the modem network."
            if self.config
            else "Run setup on the USB-connected computer, or import its pairing file here."
        )
        self.ttk.Label(status_card, text=title, style="StatusTitle.TLabel").pack(anchor="w")
        self.setup_detail_label = self.ttk.Label(
            status_card,
            text=self.startup_detail or detail,
            style="StatusMuted.TLabel",
            wraplength=480,
            justify="left",
        )
        self.setup_detail_label.pack(anchor="w", pady=(4, 10))
        actions = self.ttk.Frame(status_card, style="Status.TFrame")
        actions.pack(anchor="w")
        self.ttk.Button(
            actions,
            text="Import pairing file",
            style="Primary.TButton",
            command=self.import_pairing,
        ).pack(side="left", padx=(0, 10))
        if self.config:
            self.ttk.Button(
                actions,
                text="Export pairing file",
                style="Secondary.TButton",
                command=self.export_pairing,
            ).pack(side="left", padx=(0, 10))
            self.ttk.Button(
                actions,
                text="Open phone",
                style="Secondary.TButton",
                command=lambda: self.show_page("calls"),
            ).pack(side="left")
        else:
            self.ttk.Button(
                actions,
                text="Retry saved pairing",
                style="Secondary.TButton",
                command=self.retry_saved_pairing,
            ).pack(side="left")

        steps = self.ttk.Frame(self.page_host, style="Surface.TFrame")
        steps.pack(fill="x", pady=(12, 0))
        self.ttk.Label(steps, text="New modem setup", style="Section.TLabel").pack(anchor="w")
        instructions = (
            ("1", "Connect", "Plug the modem and optional Android tablet in by USB."),
            ("2", "Run one command", "From the project folder, run ./setup.sh."),
            ("3", "Use anywhere", "Open UFI Phone, or import a private pairing file on another desktop."),
        )
        for number, heading, copy in instructions:
            row = self.ttk.Frame(steps, style="Surface.TFrame")
            row.pack(fill="x", pady=(5, 0))
            marker = self.tk.Label(
                row,
                text=number,
                bg=BLUE_SOFT,
                fg=BLUE,
                font=("Segoe UI", 10, "bold"),
                width=3,
                pady=3,
                borderwidth=0,
            )
            marker.pack(side="left", anchor="n", padx=(0, 12))
            text_group = self.ttk.Frame(row, style="Surface.TFrame")
            text_group.pack(side="left", fill="x", expand=True)
            self.ttk.Label(text_group, text=heading, style="Surface.TLabel").pack(anchor="w")
            self.ttk.Label(
                text_group,
                text=copy,
                style="SurfaceMuted.TLabel",
                wraplength=470,
                justify="left",
            ).pack(anchor="w")


    def import_pairing(self) -> None:
        selected = self.filedialog.askopenfilename(
            title="Import UFI Phone pairing",
            filetypes=(("UFI Phone pairing", "*.ufi-phone"), ("JSON files", "*.json")),
        )
        if not selected:
            return
        try:
            config = read_pairing_file(Path(selected))
            save_config(config)
        except (OSError, ValueError, json.JSONDecodeError, SetupError, VoiceError) as error:
            self.messagebox.showerror("UFI Phone", str(error))
            return
        self.config = config
        self._set_navigation_enabled(True)
        self.startup_detail = "Pairing imported. Connecting to the modem…"
        self.connection_label.configure(text="Connecting…", bg=GREEN_SOFT, foreground=GREEN)
        self.show_page("calls")
        self.start_polling()

    def retry_saved_pairing(self) -> None:
        try:
            self.config = load_config()
        except (OSError, ValueError, json.JSONDecodeError, VoiceError) as error:
            self.startup_detail = friendly_connection_error(error)
            self.setup_detail_label.configure(text=self.startup_detail)
            return
        self._set_navigation_enabled(True)
        self.startup_detail = "Pairing loaded. Connecting to the modem…"
        self.connection_label.configure(text="Connecting…", bg=GREEN_SOFT, foreground=GREEN)
        self.show_page("calls")
        self.start_polling()

    def export_pairing(self) -> None:
        if self.config is None:
            return
        selected = self.filedialog.asksaveasfilename(
            title="Export UFI Phone pairing",
            defaultextension=".ufi-phone",
            initialfile="my-modem.ufi-phone",
            filetypes=(("UFI Phone pairing", "*.ufi-phone"),),
        )
        if not selected:
            return
        try:
            exported = write_pairing_file(Path(selected), self.config, overwrite=True)
        except (OSError, ValueError, json.JSONDecodeError, SetupError, VoiceError) as error:
            self.messagebox.showerror("UFI Phone", str(error))
            return
        self.messagebox.showinfo(
            "Pairing file created",
            f"Saved to {exported}\n\nKeep this file private and delete transferred copies after import.",
        )

    def start_polling(self) -> None:
        if self.poll_started:
            return
        self.poll_started = True
        self.root.after(100, self.poll)

    def _set_navigation_enabled(self, enabled: bool) -> None:
        state = "normal" if enabled else "disabled"
        for button in self.nav_buttons.values():
            button.configure(state=state)

    def _widget_exists(self, attribute: str) -> bool:
        widget = getattr(self, attribute, None)
        if widget is None:
            return False
        try:
            return bool(widget.winfo_exists())
        except self.tk.TclError:
            return False

    def _build_calls(self) -> None:
        self.ttk.Label(self.page_host, text="Calls", style="Heading.TLabel").pack(anchor="w")
        self.ttk.Label(
            self.page_host,
            text="Your modem line and recent activity",
            style="SurfaceMuted.TLabel",
        ).pack(anchor="w", pady=(2, 18))
        card = self.ttk.Frame(self.page_host, style="Status.TFrame", padding=(22, 18))
        card.pack(fill="x")
        self.call_state_label = self.ttk.Label(
            card, text="Connecting…", style="StatusTitle.TLabel"
        )
        self.call_state_label.pack(anchor="w")
        self.call_detail_label = self.ttk.Label(
            card, text="", style="StatusMuted.TLabel", wraplength=650
        )
        self.call_detail_label.pack(anchor="w", pady=(4, 14))
        controls = self.ttk.Frame(card, style="Status.TFrame")
        controls.pack(anchor="w")
        self.answer_button = self.ttk.Button(
            controls, text="Answer", style="Primary.TButton", command=lambda: self.command("ANSWER")
        )
        self.answer_button.pack(side="left", padx=(0, 8))
        self.hangup_button = self.ttk.Button(
            controls, text="Hang up", style="Danger.TButton", command=lambda: self.command("HANGUP")
        )
        self.hangup_button.pack(side="left", padx=(0, 8))
        self.audio_button = self.ttk.Button(
            controls, text="Connect audio", style="Secondary.TButton", command=self.toggle_audio
        )
        self.audio_button.pack(side="left")
        self.ttk.Label(self.page_host, text="Recent calls", style="Section.TLabel").pack(
            anchor="w", pady=(24, 10)
        )
        table = self.ttk.Frame(self.page_host, style="Surface.TFrame")
        table.pack(fill="both", expand=True)
        self.call_tree = self.ttk.Treeview(
            table,
            columns=("number", "direction", "outcome", "time"),
            show="headings",
            selectmode="browse",
        )
        for column, label, width in (
            ("number", "Number", 210),
            ("direction", "Direction", 100),
            ("outcome", "Result", 130),
            ("time", "Time", 150),
        ):
            self.call_tree.heading(column, text=label)
            self.call_tree.column(column, width=width, anchor="w")
        self.call_tree.pack(side="left", fill="both", expand=True)
        scrollbar = self.ttk.Scrollbar(table, orient="vertical", command=self.call_tree.yview)
        scrollbar.pack(side="right", fill="y")
        self.call_tree.configure(yscrollcommand=scrollbar.set)
        self._render_call_status()
        self._render_history()

    def _build_keypad(self) -> None:
        self.ttk.Label(self.page_host, text="Keypad", style="Heading.TLabel").pack(anchor="w")
        self.ttk.Label(
            self.page_host,
            text="Place a regular carrier call through the modem SIM",
            style="SurfaceMuted.TLabel",
        ).pack(anchor="w", pady=(2, 12))
        self.dial_value = self.tk.StringVar()
        entry = self.ttk.Entry(
            self.page_host, textvariable=self.dial_value, justify="center", font=("Segoe UI", 22)
        )
        entry.pack(fill="x", padx=100, pady=(16, 16))
        pad = self.ttk.Frame(self.page_host, style="Surface.TFrame")
        pad.pack()
        for row, values in enumerate((("1", "2", "3"), ("4", "5", "6"), ("7", "8", "9"), ("*", "0", "#"))):
            for column, value in enumerate(values):
                self.ttk.Button(
                    pad,
                    text=value,
                    style="Dial.TButton",
                    width=7,
                    command=lambda digit=value: self.dial_value.set(self.dial_value.get() + digit),
                ).grid(row=row, column=column, padx=7, pady=6)
        self.ttk.Button(
            self.page_host, text="Call", style="Primary.TButton", command=self.dial
        ).pack(pady=(16, 7))
        self.ttk.Label(
            self.page_host,
            text="Emergency numbers and service codes are intentionally blocked.",
            style="SurfaceMuted.TLabel",
        ).pack()

    def _build_messages(self) -> None:
        top = self.ttk.Frame(self.page_host, style="Surface.TFrame")
        top.pack(fill="x", pady=(0, 10))
        self.ttk.Label(top, text="Messages", style="Heading.TLabel").pack(side="left")
        self.ttk.Button(
            top, text="New message", style="Secondary.TButton", command=self.new_message
        ).pack(side="right")
        pane = self.tk.PanedWindow(
            self.page_host,
            orient="horizontal",
            background=DIVIDER,
            borderwidth=0,
            relief="flat",
            sashwidth=1,
            showhandle=False,
        )
        pane.pack(fill="both", expand=True)
        left = self.ttk.Frame(pane, style="Surface.TFrame", padding=(0, 0, 12, 0))
        right = self.ttk.Frame(pane, style="Surface.TFrame")
        pane.add(left, minsize=190, width=210)
        pane.add(right, minsize=360)
        self.conversation_list = self.tk.Listbox(
            left,
            borderwidth=0,
            highlightthickness=0,
            bg=SURFACE,
            fg=TEXT,
            selectbackground=BLUE_SOFT,
            selectforeground=TEXT,
            font=("Segoe UI", 11),
        )
        self.conversation_list.pack(fill="both", expand=True)
        self.conversation_list.bind("<<ListboxSelect>>", self.select_conversation)
        recipient_row = self.ttk.Frame(right, style="Surface.TFrame")
        recipient_row.pack(fill="x")
        self.ttk.Label(recipient_row, text="To").pack(side="left", padx=(0, 8))
        self.recipient_value = self.tk.StringVar(value=self.selected_address)
        self.recipient_entry = self.ttk.Entry(recipient_row, textvariable=self.recipient_value)
        self.recipient_entry.pack(side="left", fill="x", expand=True)
        self.thread_text = self.tk.Text(
            right,
            wrap="word",
            state="disabled",
            relief="flat",
            bg="#F6F8FC",
            fg=TEXT,
            padx=14,
            pady=14,
            font=("Segoe UI", 11),
        )
        self.thread_text.tag_configure(
            "incoming_meta", foreground=MUTED, font=("Segoe UI", 9), spacing1=7
        )
        self.thread_text.tag_configure(
            "incoming_body",
            background="#EDF1F6",
            foreground=TEXT,
            font=("Segoe UI", 11),
            lmargin1=10,
            lmargin2=10,
            rmargin=90,
            spacing1=3,
            spacing3=8,
        )
        self.thread_text.tag_configure(
            "outgoing_meta",
            foreground=MUTED,
            font=("Segoe UI", 9),
            justify="right",
            spacing1=7,
        )
        self.thread_text.tag_configure(
            "outgoing_body",
            background=BLUE_SOFT,
            foreground=TEXT,
            font=("Segoe UI", 11),
            justify="right",
            lmargin1=90,
            lmargin2=90,
            rmargin=10,
            spacing1=3,
            spacing3=8,
        )
        self.thread_text.pack(fill="both", expand=True, pady=10)
        composer = self.ttk.Frame(right, style="Surface.TFrame")
        composer.pack(fill="x")
        self.message_value = self.tk.StringVar()
        self.ttk.Entry(composer, textvariable=self.message_value).pack(
            side="left", fill="x", expand=True, padx=(0, 8)
        )
        self.ttk.Button(
            composer, text="Send", style="Primary.TButton", command=self.send_sms
        ).pack(side="right")
        self._render_conversations()

    def poll(self) -> None:
        if self.config is None:
            self.poll_started = False
            return
        if self.polling:
            self.root.after(1000, self.poll)
            return
        self.polling = True

        def worker() -> None:
            try:
                status = gateway_status(self.config)
                messages = sms_messages(self.config)
                self.root.after(0, lambda: self.apply_snapshot(status, messages))
            except Exception as error:
                detail = str(error)
                self.root.after(0, lambda value=detail: self.show_offline(value))

        threading.Thread(target=worker, daemon=True).start()
        self.root.after(2000, self.poll)

    def apply_snapshot(self, status: dict[str, Any], messages: list[dict[str, Any]]) -> None:
        self.polling = False
        previous = self.last_state
        self.status = status
        self.last_state = str(status.get("state", "UNKNOWN"))
        self.messages = messages
        self.conversations = group_conversations(messages)
        ready = bool(status.get("callReady", False))
        network = str(status.get("network", "")).strip() or "Modem"
        if ready:
            self.connection_label.configure(
                text=f"{network} · Ready", background=GREEN_SOFT, foreground=GREEN
            )
        else:
            self.connection_label.configure(
                text="Repairing call mode", background=AMBER_SOFT, foreground=AMBER
            )
        if self.history.observe(status):
            self._render_history()
        self._render_call_status()
        self._render_conversations()
        if self.last_state == "RINGING" and previous != "RINGING":
            self.root.deiconify()
            self.root.lift()
            self.root.bell()

    def show_offline(self, detail: str) -> None:
        self.polling = False
        self.connection_label.configure(text="Modem offline", background=RED_SOFT, foreground=RED)
        if self._widget_exists("call_state_label"):
            self.call_state_label.configure(text="Offline")
            self.call_detail_label.configure(
                text="Join the modem Wi-Fi, then open Connection if pairing needs to be restored."
            )
        self.startup_detail = friendly_connection_error(detail)
        if self._widget_exists("setup_detail_label"):
            self.setup_detail_label.configure(text=self.startup_detail)

    def _render_call_status(self) -> None:
        if not self._widget_exists("call_state_label"):
            return
        state = str(self.status.get("state", "Connecting"))
        caller = str(self.status.get("caller", ""))
        labels = {"IDLE": "Ready for calls", "RINGING": "Incoming call", "DIALING": "Calling", "ACTIVE": "Call in progress"}
        self.call_state_label.configure(text=labels.get(state, state.title()))
        recoveries = int(self.status.get("modeRecoveries", 0))
        if caller:
            detail = caller
        elif recoveries:
            detail = f"Connected privately · call mode recovered {recoveries}× since boot"
        else:
            detail = "Calls and SMS stay on the modem's local network"
        self.call_detail_label.configure(text=detail)
        self.answer_button.configure(state="normal" if state == "RINGING" else "disabled")
        self.hangup_button.configure(state="normal" if state in ("RINGING", "DIALING", "ACTIVE") else "disabled")
        self.audio_button.configure(state="normal" if state == "ACTIVE" else "disabled")
        if state != "ACTIVE" and self.audio is not None:
            self.audio.stop()
            self.audio = None
            self.audio_button.configure(text="Connect audio")

    def _render_history(self) -> None:
        if not self._widget_exists("call_tree"):
            return
        for item in self.call_tree.get_children():
            self.call_tree.delete(item)
        for entry in self.history.entries:
            timestamp = int(entry.get("startedAt", 0)) / 1000
            when = datetime.fromtimestamp(timestamp).strftime("%Y-%m-%d %H:%M") if timestamp else ""
            self.call_tree.insert(
                "",
                "end",
                values=(entry.get("number") or "Unknown", entry.get("direction", "").title(), entry.get("outcome", ""), when),
            )

    def _render_conversations(self) -> None:
        if not self._widget_exists("conversation_list"):
            return
        current = self.selected_address
        self.conversation_list.delete(0, "end")
        selected_index = -1
        for index, conversation in enumerate(self.conversations):
            unread = int(conversation["unread"])
            suffix = f"  ({unread} new)" if unread else ""
            self.conversation_list.insert("end", f"{conversation['address'] or 'Unknown'}{suffix}")
            if conversation["address"] == current:
                selected_index = index
        if selected_index >= 0:
            self.conversation_list.selection_set(selected_index)
            self._render_thread()

    def select_conversation(self, _event: Any = None) -> None:
        selection = self.conversation_list.curselection()
        if not selection:
            return
        conversation = self.conversations[int(selection[0])]
        self.selected_address = str(conversation["address"])
        self.recipient_value.set(self.selected_address)
        self._render_thread()
        self.command("SMS_READ " + encode_command_value(self.selected_address), quiet=True)

    def _render_thread(self) -> None:
        if not self._widget_exists("thread_text"):
            return
        self.thread_text.configure(state="normal")
        self.thread_text.delete("1.0", "end")
        matching = [m for m in reversed(self.messages) if str(m.get("address", "")) == self.selected_address]
        for message in matching:
            incoming = int(message.get("type", 0)) == 1
            prefix = "From" if incoming else "You"
            timestamp = int(message.get("date", 0)) / 1000
            when = datetime.fromtimestamp(timestamp).strftime("%b %d, %H:%M") if timestamp else ""
            side = "incoming" if incoming else "outgoing"
            self.thread_text.insert("end", f"{prefix} · {when}\n", f"{side}_meta")
            self.thread_text.insert("end", f"{message.get('body', '')}\n", f"{side}_body")
            self.thread_text.insert("end", "\n")
        self.thread_text.configure(state="disabled")
        self.thread_text.see("end")

    def new_message(self) -> None:
        self.selected_address = ""
        self.recipient_value.set("")
        self.thread_text.configure(state="normal")
        self.thread_text.delete("1.0", "end")
        self.thread_text.insert("end", "Send a direct SMS through the modem.\n")
        self.thread_text.configure(state="disabled")
        self.recipient_entry.focus_set()

    def dial(self) -> None:
        number = normalize_number(self.dial_value.get(), minimum_digits=6)
        if not number:
            self.messagebox.showwarning("UFI Phone", "Enter a normal number with 6 to 20 digits.")
            return
        self.command("DIAL " + number)

    def send_sms(self) -> None:
        address = normalize_number(self.recipient_value.get())
        body = self.message_value.get().strip()
        if not address or not body or len(body) > 2000:
            self.messagebox.showwarning("UFI Phone", "Enter a valid number and a message up to 2000 characters.")
            return
        command = "SMS_SEND " + encode_command_value(address) + " " + encode_command_value(body)

        def success() -> None:
            self.selected_address = address
            self.message_value.set("")

        self.command(command, on_success=success)

    def command(self, command: str, quiet: bool = False, on_success: Any = None) -> None:
        with self.command_lock:
            if command in self.pending_commands:
                return
            self.pending_commands.add(command)

        def worker() -> None:
            try:
                control_request(self.config, command)
                if on_success:
                    self.root.after(0, on_success)
            except Exception as error:
                if not quiet:
                    detail = str(error)
                    self.root.after(
                        0,
                        lambda value=detail: self.messagebox.showerror("UFI Phone", value),
                    )
            finally:
                with self.command_lock:
                    self.pending_commands.discard(command)

        threading.Thread(target=worker, daemon=True).start()

    def toggle_audio(self) -> None:
        if self.audio is not None:
            self.audio.stop()
            self.audio = None
            self.audio_button.configure(text="Connect audio")
            return
        try:
            self.audio = portable_audio(self.config)
            self.audio.start()
            self.audio_button.configure(text="Disconnect audio")
        except Exception as error:
            self.audio = None
            self.messagebox.showerror("UFI Phone", str(error))

    def close(self) -> None:
        if self.audio is not None:
            self.audio.stop()
        self.root.destroy()

    def run(self) -> None:
        self.root.mainloop()


def main() -> int:
    if os.environ.get("UFI_PHONE_PACKAGE_SELF_TEST") == "1":
        import tkinter

        interpreter = tkinter.Tcl()
        if not interpreter.eval("info patchlevel"):
            raise RuntimeError("Tcl/Tk runtime did not initialize")
        return 0
    parser = argparse.ArgumentParser(
        description="Calls and SMS through a paired UFI modem"
    )
    parser.add_argument(
        "pairing_file",
        nargs="?",
        type=Path,
        help="optional .ufi-phone pairing file to import",
    )
    args = parser.parse_args()
    config: dict[str, Any] | None = None
    startup_detail = ""
    try:
        if args.pairing_file:
            config = read_pairing_file(args.pairing_file)
            save_config(config)
            startup_detail = "Pairing imported. Connecting to the modem…"
        else:
            config = load_config()
    except (OSError, ValueError, json.JSONDecodeError, VoiceError) as error:
        startup_detail = friendly_connection_error(error)
    UfiPhoneApp(config, startup_detail).run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
