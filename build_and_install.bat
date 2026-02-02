@echo off
setlocal EnableDelayedExpansion

:: ============================================
:: BLE Master - Build and Swift Install Script
:: ============================================

title BLE Master - Build and Install

:: Colors for output (using ANSI codes)
set "GREEN=[92m"
set "RED=[91m"
set "YELLOW=[93m"
set "CYAN=[96m"
set "RESET=[0m"

:: Paths
set "PROJECT_DIR=%~dp0"
set "ADB_PATH=%PROJECT_DIR%..\platform-tools\adb.exe"
set "APK_PATH=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk"

echo.
echo %CYAN%============================================%RESET%
echo %CYAN%       BLE Master - Build ^& Install        %RESET%
echo %CYAN%============================================%RESET%
echo.

:: Check for command line arguments
set "SKIP_BUILD=0"
set "AUTO_LAUNCH=0"
if "%1"=="--skip-build" set "SKIP_BUILD=1"
if "%1"=="-s" set "SKIP_BUILD=1"
if "%2"=="--launch" set "AUTO_LAUNCH=1"
if "%2"=="-l" set "AUTO_LAUNCH=1"

:: ============================================
:: Step 1: Build the App
:: ============================================
if %SKIP_BUILD%==1 (
    echo %YELLOW%[1/3] Skipping build ^(--skip-build flag^)%RESET%
) else (
    echo %YELLOW%[1/3] Building BLE Master...%RESET%
    echo.

    cd /d "%PROJECT_DIR%"
    call gradlew.bat assembleDebug --quiet

    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo %RED%[ERROR] Build FAILED! Aborting installation.%RESET%
        echo.
        pause
        exit /b 1
    )
    
    echo %GREEN%[SUCCESS] Build completed successfully!%RESET%
)

:: Verify APK exists
if not exist "%APK_PATH%" (
    echo.
    echo %RED%[ERROR] APK not found at: %APK_PATH%%RESET%
    echo.
    pause
    exit /b 1
)

echo.

:: ============================================
:: Step 2: Check for Connected Devices
:: ============================================
echo %YELLOW%[2/3] Checking for connected devices...%RESET%
echo.

:: Check if ADB exists
if not exist "%ADB_PATH%" (
    echo %RED%[ERROR] ADB not found at: %ADB_PATH%%RESET%
    echo Please ensure platform-tools is in the parent directory.
    echo.
    pause
    exit /b 1
)

:: Start ADB server if not running
"%ADB_PATH%" start-server >nul 2>&1

:: Get list of devices
set "DEVICE_COUNT=0"
set "DEVICE_ID="

for /f "skip=1 tokens=1,2" %%a in ('"%ADB_PATH%" devices 2^>nul') do (
    if "%%b"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_ID=%%a"
        echo     Found device: %%a
    ) else if "%%b"=="unauthorized" (
        echo %YELLOW%    [WARNING] Device %%a is unauthorized. Please accept USB debugging prompt.%RESET%
    )
)

if %DEVICE_COUNT% EQU 0 (
    echo.
    echo %RED%[ERROR] No devices connected!%RESET%
    echo.
    echo Please connect your device and ensure:
    echo   1. USB Debugging is enabled
    echo   2. USB cable is connected
    echo   3. You have authorized this computer on the device
    echo.
    pause
    exit /b 1
)

echo.
echo %GREEN%[SUCCESS] Found %DEVICE_COUNT% device(s)%RESET%
echo.

:: ============================================
:: Step 3: Install the APK
:: ============================================
echo %YELLOW%[3/3] Installing BLE Master...%RESET%
echo.

:: Get APK size for display
for %%A in ("%APK_PATH%") do set "APK_SIZE=%%~zA"
set /a APK_SIZE_MB=%APK_SIZE% / 1048576

echo     APK Size: ~%APK_SIZE_MB% MB
echo     Installing to: %DEVICE_ID%
echo.

:: Install with replace flag for faster reinstall
"%ADB_PATH%" -s %DEVICE_ID% install -r -t "%APK_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo %RED%[ERROR] Installation FAILED!%RESET%
    echo.
    echo Trying with uninstall first...
    "%ADB_PATH%" -s %DEVICE_ID% uninstall com.blemaster.app >nul 2>&1
    "%ADB_PATH%" -s %DEVICE_ID% install -t "%APK_PATH%"
    
    if %ERRORLEVEL% NEQ 0 (
        echo %RED%[ERROR] Installation still failed. Please check device storage and permissions.%RESET%
        pause
        exit /b 1
    )
)

echo.
echo %GREEN%============================================%RESET%
echo %GREEN%    BLE Master installed successfully!     %RESET%
echo %GREEN%============================================%RESET%
echo.

:: ============================================
:: Optional: Launch the App
:: ============================================
if %AUTO_LAUNCH%==1 (
    set "LAUNCH=Y"
) else (
    set /p LAUNCH="Launch the app now? (Y/N): "
)

if /i "%LAUNCH%"=="Y" (
    echo.
    echo Launching BLE Master...
    "%ADB_PATH%" -s %DEVICE_ID% shell am start -n com.blemaster.app/.MainActivity
    echo.
    echo %GREEN%App launched!%RESET%
)

echo.
echo %CYAN%============================================%RESET%
echo %CYAN%              TESTING TIPS                 %RESET%
echo %CYAN%============================================%RESET%
echo.
echo To test BLE broadcasting with 2 devices:
echo.
echo   Device 1 ^(Broadcaster^):
echo     - Enter a message and tap the broadcast button
echo.
echo   Device 2 ^(Scanner^):
echo     - Tap the search icon in the top bar
echo     - Tap "Scan" to detect nearby broadcasts
echo     - You should see the message from Device 1
echo.
echo Done.
pause
exit /b 0
