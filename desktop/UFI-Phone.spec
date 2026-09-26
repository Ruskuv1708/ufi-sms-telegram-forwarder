# -*- mode: python ; coding: utf-8 -*-
from pathlib import Path
import sys

root = Path(SPEC).resolve().parents[1]
hidden = []
extra_binaries = []
try:
    import sounddevice  # noqa: F401
    hidden.append("sounddevice")
except Exception:
    pass

# Some standalone Python distributions keep Tcl/Tk beside libpython and rely
# on the interpreter's loader path. PyInstaller cannot always discover those
# libraries through ldd, so include them explicitly when present.
if sys.platform.startswith("linux"):
    try:
        import _tkinter

        python_lib = Path(_tkinter.__file__).resolve().parents[2]
        for pattern in ("libtcl*.so*", "libtk*.so*"):
            for library in sorted(python_lib.glob(pattern)):
                if library.is_file() and (str(library), ".") not in extra_binaries:
                    extra_binaries.append((str(library), "."))
    except Exception:
        pass

a = Analysis(
    [str(root / "desktop" / "ufi_phone_desktop.py")],
    pathex=[str(root)],
    binaries=extra_binaries,
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
    # UPX-packed executables are more likely to trigger antivirus false
    # positives and make release reproduction harder.
    upx=False,
    console=False,
    icon=str(root / "assets" / ("ufi-phone.ico" if sys.platform == "win32" else "ufi-phone.png")),
)
