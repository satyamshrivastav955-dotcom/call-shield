@echo off
REM ===================================================================
REM  antAI - USB connectivity setup (Windows)
REM
REM  Makes every USB-connected Android phone able to reach the antAI
REM  server that is running on THIS computer, with NO Wi-Fi / LAN.
REM
REM  How it works:
REM    The app talks to http://127.0.0.1:18765 (see AuthPrefs.DEFAULT_BASE).
REM    "adb reverse" tunnels the phone's localhost port to this PC over the
REM    USB cable:
REM        phone localhost:18765  --USB-->  this PC localhost:8765  (server)
REM        phone localhost:3478   --USB-->  this PC localhost:3478  (TURN, for
REM                                                        live call/video media)
REM
REM  Run this AFTER you start the server (python run_dev.py) and every time
REM  you replug a phone or restart the adb server.
REM
REM  Requires: Android Platform-Tools (adb) + USB debugging enabled on each
REM  phone (Settings > Developer options > USB debugging), and you must tap
REM  "Allow" on the phone the first time.
REM ===================================================================
setlocal enabledelayedexpansion

set "SERVER_PORT=8765"
set "PHONE_PORT=18765"
set "TURN_PORT=3478"

REM ---- locate adb -------------------------------------------------
set "ADB="
where adb >nul 2>nul && set "ADB=adb"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%ProgramFiles%\Android\android-sdk\platform-tools\adb.exe" set "ADB=%ProgramFiles%\Android\android-sdk\platform-tools\adb.exe"
if not defined ADB if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if not defined ADB (
    echo [ERROR] adb was not found.
    echo         Install Android Platform-Tools and either add it to PATH or
    echo         put it at %%LOCALAPPDATA%%\Android\Sdk\platform-tools\adb.exe
    echo         Download: https://developer.android.com/tools/releases/platform-tools
    exit /b 1
)
echo Using adb: %ADB%
echo.

REM ---- make sure the adb daemon is up -----------------------------
"%ADB%" start-server >nul 2>nul

REM ---- enumerate connected devices --------------------------------
set "COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set /a COUNT+=1
        echo [device !COUNT!] %%A
        "%ADB%" -s %%A reverse tcp:%PHONE_PORT% tcp:%SERVER_PORT%  >nul && echo     ^> app  : phone localhost:%PHONE_PORT% -^> PC localhost:%SERVER_PORT%   OK
        "%ADB%" -s %%A reverse tcp:%TURN_PORT% tcp:%TURN_PORT%     >nul && echo     ^> media: phone localhost:%TURN_PORT%  -^> PC localhost:%TURN_PORT%    OK
    ) else (
        if not "%%A"=="" echo [skip] %%A is "%%B" ^(unlock the phone / tap Allow USB debugging^)
    )
)

echo.
if "%COUNT%"=="0" (
    echo [ERROR] No authorized devices found.
    echo         - Plug the phone in with a data-capable USB cable.
    echo         - Enable Settings ^> Developer options ^> USB debugging.
    echo         - Tap "Allow" on the phone when prompted, then re-run this.
    exit /b 1
)

echo Done. Forwarded ports for %COUNT% device^(s^).
echo Active reverse tunnels per device:
"%ADB%" reverse --list
echo.
echo Keep the phones plugged in. If you replug or reboot adb, run this again.
endlocal
