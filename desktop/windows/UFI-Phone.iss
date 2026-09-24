#define MyAppName "UFI Phone"
#define MyAppVersion "0.5.0"
#define MyAppPublisher "Kuvatov Ruslan Baxtiyarovich"
#define MyAppExeName "UFI-Phone.exe"

[Setup]
AppId={{A281FA0B-E1F3-4B03-8735-A1EA085AAE36}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\UFI Phone
DefaultGroupName=UFI Phone
OutputDir=..\..\dist
OutputBaseFilename=UFI-Phone-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=..\..\assets\ufi-phone.ico
UninstallDisplayIcon={app}\{#MyAppExeName}

[Files]
Source: "..\..\dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\UFI Phone"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\UFI Phone"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch UFI Phone"; Flags: nowait postinstall skipifsilent
