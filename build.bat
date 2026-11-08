@echo off
REM Build HexTranscriber on Windows.
setlocal
cd /d "%~dp0"

set FAIL=0

where gradle >nul 2>nul
if errorlevel 1 (
    echo   - 'gradle' not found on PATH. Install Gradle and add it to PATH: https://gradle.org/install/
    set FAIL=1
)

where java >nul 2>nul
if errorlevel 1 (
    echo   - 'java' not found on PATH. Install a JDK 17 and add it to PATH ^(or set JAVA_HOME^).
    set FAIL=1
) else (
    java -version 2>&1 | findstr /c:"\"17." >nul
    if errorlevel 1 (
        echo   - 'java' on PATH is not version 17. The Android Gradle Plugin requires JDK 17.
        set FAIL=1
    )
)

set SDKDIR=%ANDROID_HOME%
if "%SDKDIR%"=="" set SDKDIR=%ANDROID_SDK_ROOT%
if "%SDKDIR%"=="" (
    echo   - Android SDK not found. Set ANDROID_HOME ^(or ANDROID_SDK_ROOT^) to your Android SDK install directory.
    set FAIL=1
) else (
    if not exist "%SDKDIR%\platforms" (
        echo   - Android SDK at "%SDKDIR%" is missing a platforms\ directory.
        set FAIL=1
    )
    if not exist "%SDKDIR%\build-tools" (
        echo   - Android SDK at "%SDKDIR%" is missing a build-tools\ directory.
        set FAIL=1
    )
)

if "%FAIL%"=="1" (
    echo.
    echo Cannot build: missing required dependencies ^(see above^).
    echo Required programs:
    echo   - JDK 17 ^(on PATH, or JAVA_HOME pointing at one^)
    echo   - Gradle ^(on PATH^)
    echo   - Android SDK ^(ANDROID_HOME or ANDROID_SDK_ROOT, with platforms\ and build-tools\^)
    exit /b 1
)

call gradle --no-daemon assembleDebug
if errorlevel 1 exit /b 1

if not exist builds mkdir builds
copy /y app\build\outputs\apk\debug\app-debug.apk builds\HexTranscriber-debug.apk >nul
echo APK written to builds\HexTranscriber-debug.apk
