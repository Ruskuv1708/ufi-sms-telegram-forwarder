#!/usr/bin/env python3
"""UFI Phone desktop app for Linux and Windows."""

from __future__ import annotations

import base64
from datetime import datetime
import json
from pathlib import Path
import platform
import socket
import sys
import threading
from typing import Any


PROJECT_DIR = Path(__file__).resolve().parents[1]
if str(PROJECT_DIR) not in sys.path:
    sys.path.insert(0, str(PROJECT_DIR))

from desktop.model import CallHistory, group_conversations, normalize_number
from ufi_voice import (
    AudioSession,
    CONFIG_PATH,
    DOWNLINK_PORT,
    UPLINK_PORT,
    VoiceError,
    authenticate_socket,
    control_request,
    gateway_status,
    load_config,
    read_line,
)


BACKGROUND = "#F7F9FE"
SURFACE = "#FFFFFF"
TEXT = "#101B35"
MUTED = "#627087"
BLUE = "#0B67D1"
BLUE_SOFT = "#E7F0FF"
GREEN = "#159455"
RED = "#D92D20"


def bundled_asset(relative: str) -> Path:
    root = Path(getattr(sys, "_MEIPASS", PROJECT_DIR))
    return root / relative


def encode_command_value(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii")


def sms_messages(config: dict[str, Any]) -> list[dict[str, Any]]:
    payload = json.loads(control_request(config, "SMS_LIST"))
    messages = payload.get("messages", [])
    return messages if isinstance(messages, list) else []


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
    def __init__(self, config: dict[str, Any]) -> None:
        import tkinter as tk
        from tkinter import messagebox, ttk

        self.tk = tk
        self.ttk = ttk
        self.messagebox = messagebox
        self.config = config
        self.root = tk.Tk()
        self.root.title("UFI Phone")
        self.root.geometry("980x660")
        self.root.minsize(780, 540)
        self.root.configure(bg=BACKGROUND)
        try:
            self.window_icon = tk.PhotoImage(file=str(bundled_asset("assets/ufi-phone.png")))
            self.root.iconphoto(True, self.window_icon)
        except tk.TclError:
            self.window_icon = None
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.audio: Any = None
        self.polling = False
        self.last_state = ""
        self.status: dict[str, Any] = {}
        self.messages: list[dict[str, Any]] = []
        self.conversations: list[dict[str, Any]] = []
        self.selected_address = ""
        self.history = CallHistory(CONFIG_PATH.parent / "desktop-call-history.json")
        self._configure_styles()
        self._build_shell()
        self.show_page("calls")
        self.root.after(100, self.poll)

    def _configure_styles(self) -> None:
        style = self.ttk.Style(self.root)
        try:
            style.theme_use("clam")
        except self.tk.TclError:
            pass
        style.configure("TFrame", background=BACKGROUND)
        style.configure("Surface.TFrame", background=SURFACE)
        style.configure("TLabel", background=BACKGROUND, foreground=TEXT, font=("Segoe UI", 11))
        style.configure("Title.TLabel", font=("Segoe UI", 23, "bold"), foreground=TEXT)
        style.configure("Heading.TLabel", font=("Segoe UI", 17, "bold"), foreground=TEXT)
        style.configure("Muted.TLabel", foreground=MUTED)
        style.configure("Nav.TButton", font=("Segoe UI", 11), padding=(16, 12), anchor="w")
        style.configure("Primary.TButton", background=BLUE, foreground="white", padding=(14, 10))
        style.map("Primary.TButton", background=[("active", "#095AB8"), ("disabled", "#A9C6EA")])
        style.configure("Danger.TButton", background=RED, foreground="white", padding=(14, 10))
        style.configure("Treeview", rowheight=34, font=("Segoe UI", 10), background=SURFACE)
        style.configure("Treeview.Heading", font=("Segoe UI", 10, "bold"))

    def _build_shell(self) -> None:
        header = self.ttk.Frame(self.root, padding=(24, 18, 24, 12))
        header.pack(fill="x")
        self.ttk.Label(header, text="UFI Phone", style="Title.TLabel").pack(side="left")
        self.connection_label = self.ttk.Label(header, text="Connecting…", style="Muted.TLabel")
        self.connection_label.pack(side="right")

        body = self.ttk.Frame(self.root)
        body.pack(fill="both", expand=True, padx=18, pady=(0, 18))
        navigation = self.ttk.Frame(body, style="Surface.TFrame", padding=10)
        navigation.pack(side="left", fill="y", padx=(0, 14))
        for label, page in (("Calls", "calls"), ("Keypad", "keypad"), ("Messages", "messages")):
            self.ttk.Button(
                navigation,
                text=label,
                style="Nav.TButton",
                command=lambda value=page: self.show_page(value),
            ).pack(fill="x", pady=3)
        self.page_host = self.ttk.Frame(body, style="Surface.TFrame", padding=22)
        self.page_host.pack(side="left", fill="both", expand=True)

    def show_page(self, page: str) -> None:
        for child in self.page_host.winfo_children():
            child.destroy()
        if page == "calls":
            self._build_calls()
        elif page == "keypad":
            self._build_keypad()
        else:
            self._build_messages()

    def _build_calls(self) -> None:
        self.ttk.Label(self.page_host, text="Calls", style="Heading.TLabel").pack(anchor="w")
        card = self.ttk.Frame(self.page_host, padding=(0, 16))
        card.pack(fill="x")
        self.call_state_label = self.ttk.Label(card, text="Connecting…", style="Heading.TLabel")
        self.call_state_label.pack(anchor="w")
        self.call_detail_label = self.ttk.Label(card, text="", style="Muted.TLabel")
        self.call_detail_label.pack(anchor="w", pady=(3, 10))
        controls = self.ttk.Frame(card)
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
            controls, text="Connect audio", command=self.toggle_audio
        )
        self.audio_button.pack(side="left")
        self.ttk.Label(self.page_host, text="Recent calls", style="Heading.TLabel").pack(
            anchor="w", pady=(14, 8)
        )
        self.call_tree = self.ttk.Treeview(
            self.page_host,
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
        self.call_tree.pack(fill="both", expand=True)
        self._render_call_status()
        self._render_history()

    def _build_keypad(self) -> None:
        self.ttk.Label(self.page_host, text="Keypad", style="Heading.TLabel").pack(anchor="w")
        self.dial_value = self.tk.StringVar()
        entry = self.ttk.Entry(
            self.page_host, textvariable=self.dial_value, justify="center", font=("Segoe UI", 22)
        )
        entry.pack(fill="x", padx=80, pady=(22, 14))
        pad = self.ttk.Frame(self.page_host)
        pad.pack()
        for row, values in enumerate((("1", "2", "3"), ("4", "5", "6"), ("7", "8", "9"), ("*", "0", "#"))):
            for column, value in enumerate(values):
                self.ttk.Button(
                    pad,
                    text=value,
                    width=7,
                    command=lambda digit=value: self.dial_value.set(self.dial_value.get() + digit),
                ).grid(row=row, column=column, padx=6, pady=5, ipady=8)
        self.ttk.Button(
            self.page_host, text="Call", style="Primary.TButton", command=self.dial
        ).pack(pady=(14, 6))
        self.ttk.Label(
            self.page_host,
            text="Emergency numbers and service codes are intentionally blocked.",
            style="Muted.TLabel",
        ).pack()

    def _build_messages(self) -> None:
        top = self.ttk.Frame(self.page_host)
        top.pack(fill="x", pady=(0, 10))
        self.ttk.Label(top, text="Messages", style="Heading.TLabel").pack(side="left")
        self.ttk.Button(top, text="New message", command=self.new_message).pack(side="right")
        pane = self.ttk.Panedwindow(self.page_host, orient="horizontal")
        pane.pack(fill="both", expand=True)
        left = self.ttk.Frame(pane, padding=(0, 0, 12, 0))
        right = self.ttk.Frame(pane)
        pane.add(left, weight=1)
        pane.add(right, weight=3)
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
        recipient_row = self.ttk.Frame(right)
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
            bg=BACKGROUND,
            fg=TEXT,
            padx=14,
            pady=14,
            font=("Segoe UI", 11),
        )
        self.thread_text.pack(fill="both", expand=True, pady=10)
        composer = self.ttk.Frame(right)
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
                self.root.after(0, lambda: self.show_offline(str(error)))

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
        network = str(status.get("network", "Unknown"))
        recoveries = int(status.get("modeRecoveries", 0))
        if ready:
            suffix = f" · recovered {recoveries}×" if recoveries else ""
            self.connection_label.configure(text=f"●  Modem · {network} · Ready{suffix}", foreground=GREEN)
        else:
            self.connection_label.configure(text="●  Correcting call mode", foreground=RED)
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
        self.connection_label.configure(text="●  Modem offline", foreground=RED)
        if hasattr(self, "call_state_label"):
            self.call_state_label.configure(text="Offline")
            self.call_detail_label.configure(text=detail)

    def _render_call_status(self) -> None:
        if not hasattr(self, "call_state_label"):
            return
        state = str(self.status.get("state", "Connecting"))
        caller = str(self.status.get("caller", ""))
        labels = {"IDLE": "Ready for calls", "RINGING": "Incoming call", "DIALING": "Calling", "ACTIVE": "Call in progress"}
        self.call_state_label.configure(text=labels.get(state, state.title()))
        self.call_detail_label.configure(text=caller or "Calls and SMS stay on the modem's local network")
        self.answer_button.configure(state="normal" if state == "RINGING" else "disabled")
        self.hangup_button.configure(state="normal" if state in ("RINGING", "DIALING", "ACTIVE") else "disabled")
        self.audio_button.configure(state="normal" if state == "ACTIVE" else "disabled")
        if state != "ACTIVE" and self.audio is not None:
            self.audio.stop()
            self.audio = None
            self.audio_button.configure(text="Connect audio")

    def _render_history(self) -> None:
        if not hasattr(self, "call_tree"):
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
        if not hasattr(self, "conversation_list"):
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
        if not hasattr(self, "thread_text"):
            return
        self.thread_text.configure(state="normal")
        self.thread_text.delete("1.0", "end")
        matching = [m for m in reversed(self.messages) if str(m.get("address", "")) == self.selected_address]
        for message in matching:
            incoming = int(message.get("type", 0)) == 1
            prefix = "From" if incoming else "You"
            timestamp = int(message.get("date", 0)) / 1000
            when = datetime.fromtimestamp(timestamp).strftime("%b %d, %H:%M") if timestamp else ""
            self.thread_text.insert("end", f"{prefix} · {when}\n{message.get('body', '')}\n\n")
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
        def worker() -> None:
            try:
                control_request(self.config, command)
                if on_success:
                    self.root.after(0, on_success)
            except Exception as error:
                if not quiet:
                    self.root.after(0, lambda: self.messagebox.showerror("UFI Phone", str(error)))

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
    try:
        config = load_config()
        UfiPhoneApp(config).run()
    except (OSError, ValueError, json.JSONDecodeError, VoiceError) as error:
        try:
            from tkinter import messagebox

            messagebox.showerror("UFI Phone", str(error))
        except Exception:
            print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
