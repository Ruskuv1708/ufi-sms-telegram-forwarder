# UFI Phone desktop

The desktop client provides calls, a keypad, local call history, direct SMS,
and two-way call audio over the modem's authenticated private-LAN protocol.

Run from source:

```bash
python3 desktop/ufi_phone_desktop.py
```

Linux audio uses the existing PipeWire/PulseAudio `parec` and `paplay`
fallback. The Windows package includes the optional `sounddevice`/PortAudio
backend. Keep the desktop on the modem's local network and pair the gateway
once with `./ufi_setup.py install` or `./ufi_voice.py setup`.

Build a native executable with Python 3.12+ and PyInstaller:

```bash
python -m pip install pyinstaller
# Windows only:
python -m pip install -r desktop/requirements-windows.txt
python -m PyInstaller --clean --noconfirm desktop/UFI-Phone.spec
```

Release builds are produced for Linux and Windows by GitHub Actions. The
pairing token and message/call history are never embedded into the executable.

On Linux, install a built or downloaded binary for the current user with:

```bash
./desktop/install-linux.sh /path/to/UFI-Phone
```

The repository also includes `windows/UFI-Phone.iss` for producing a standard
Inno Setup installer from the Windows executable. The GitHub artifact remains
portable and can run without an installer.
