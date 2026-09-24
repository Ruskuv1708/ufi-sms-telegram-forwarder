# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path
import sys

root = Path(SPEC).resolve().parents[1]
hidden = []
try:
    import sounddevice  # noqa: F401
    hidden.append("sounddevice")
except Exception:
    pass

a = Analysis(
    [str(root / "desktop" / "ufi_phone_desktop.py")],
    pathex=[str(root)],
    binaries=[],
    datas=[
        (str(root / "hardware-profiles.json"), "."),
        (str(root / "assets" / "ufi-phone.png"), "assets"),
    ],
    hiddenimports=hidden,
    hookspath=[],
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)
exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="UFI-Phone",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    icon=str(root / "assets" / ("ufi-phone.ico" if sys.platform == "win32" else "ufi-phone.png")),
)
