@echo off
REM =====================================================================
REM  antAI - build the app + install on every USB phone + open the USB
REM          tunnels, in ONE step. Run this on Windows (double-click or
REM          from a terminal) from the app\ folder.
REM
REM  What it does:
REM    1. Builds the debug APK      (gradlew.bat assembleDebug)
REM    2. Installs it on every phone that is plugged in with USB debugging
REM    3. Sets up the USB tunnels   (adb reverse 18765=app, 3478=media)
REM
REM  Recommended order:
REM    A) Start the server first, in its own terminal:
REM         cd ..\server  ^&  python run_dev.py
REM    B) Run this script.
REM    C) Open antAI on each phone.
REM
REM  Requires (already configured in this repo):
REM    - Android SDK   -> app\local.properties  (sdk.dir)
REM    - JDK 17+ (21)  -> app\gradle.properties  (org.gradle.java.home)
REM    - USB debugging enabled on each phone + a data-capable USB cable.
REM =====================================================================
setlocal enabledelayedexpansion
pushd "%~dp0"

set "SERVER_PORT=8765"
set "PHONE_PORT=18765"
set "TURN_PORT=3478"
set "PKG=com.antai.app"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"

REM ---- locate adb -----------------------------------------------------
set "ADB="
where adb >nul 2>nul && set "ADB=adb"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" set "ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%ProgramFiles%\Android\android-sdk\platform-tools\adb.exe" set "ADB=%ProgramFiles%\Android\android-sdk\platform-tools\adb.exe"
if not defined ADB (
    echo [ERROR] adb was not found. Install Android platform-tools and add it to
    echo         PATH, or keep the SDK at %%LOCALAPPDATA%%\Android\Sdk.
    echo         https://developer.android.com/tools/releases/platform-tools
    popd
    exit /b 1
)
echo Using adb: %ADB%

REM ---- 1) BUILD -------------------------------------------------------
echo.
echo === Building debug APK (gradlew assembleDebug) ===
echo     ^(first run downloads Gradle 8.9 + dependencies - can take several minutes^)
call "%~dp0gradlew.bat" assembleDebug
if errorlevel 1 (
    echo.
    echo [ERROR] Gradle build failed. Common causes:
    echo   - Wrong JDK path: edit app\gradle.properties "org.gradle.java.home" to your
    echo     JDK 17+ install, or delete that line to use JAVA_HOME.
    echo   - No internet to download dependencies on the first build.
    echo   Scroll up for the exact compile error.
    popd
    exit /b 1
)
if not exist "%APK%" (
    echo [ERROR] Build succeeded but the APK is not where expected:
    echo         %APK%
    popd
    exit /b 1
)
echo Built OK: %APK%

REM ---- 2) INSTALL + 3) TUNNEL, per connected phone -------------------
"%ADB%" start-server >nul 2>nul
set "COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set /a COUNT+=1
        echo.
        echo [device !COUNT!] %%A
        echo     installing app ...
        "%ADB%" -s %%A install -r -d "%APK%" >nul 2>nul
        if errorlevel 1 (
            echo     [warn] reinstall failed - likely a different signature is installed.
            echo            run once to wipe the old copy, then re-run this script:
            echo                "%ADB%" -s %%A uninstall %PKG%
        ) else (
            echo     installed OK
        )
        "%ADB%" -s %%A reverse tcp:%PHONE_PORT% tcp:%SERVER_PORT% >nul && echo     tunnel app  : phone %PHONE_PORT% -^> PC %SERVER_PORT%   OK
        "%ADB%" -s %%A reverse tcp:%TURN_PORT% tcp:%TURN_PORT%    >nul && echo     tunnel media: phone %TURN_PORT%  -^> PC %TURN_PORT%    OK
    ) else (
        if not "%%A"=="" echo [skip] %%A is "%%B"  ^(unlock the phone / tap Allow USB debugging^)
    )
)

echo.
if "%COUNT%"=="0" (
    echo [ERROR] No authorized devices found.
    echo         - Plug each phone in with a data-capable USB cable.
    echo         - Enable Settings ^> Developer options ^> USB debugging.
    echo         - Tap "Allow" on the phone, then re-run this script.
    popd
    exit /b 1
)

echo ============================================================
echo  Done: built + installed + tunneled on %COUNT% phone(s).
echo.
echo  Next steps:
echo    1) Make sure the server is running:  cd ..\server  ^&  python run_dev.py
echo    2) Open antAI on each phone. It should read: server: http://127.0.0.1:18765
echo    3) Messaging and calls now work over USB.
echo       For live voice/video MEDIA with NO Wi-Fi, also start the local TURN
echo       relay - see server\USB_SETUP.md (step 5). If both phones share Wi-Fi,
echo       media just works with no extra step.
echo.
echo    * After you replug a phone or reboot adb, the tunnels drop. You do NOT
echo      need to rebuild - just re-run server\usb_setup.bat to restore them.
echo ============================================================
popd
endlocal
