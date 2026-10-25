@echo off
title SafeHer - Mobile & PC Server Runner (0.0.0.0:8000)
color 0B
echo ===============================================================
echo     SafeHer Emergency Response Platform - Mobile Server
echo ===============================================================
echo.

cd /d "%~dp0"

echo [1/3] Enabling USB Port Reverse (for USB-connected phones)...
where adb >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    adb reverse tcp:8000 tcp:8000
    echo     USB Port Reverse active: localhost:8000 is now accessible via USB!
) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
    echo     USB Port Reverse active: localhost:8000 is now accessible via USB!
) else (
    echo     (Note: adb not in PATH. If using USB cable, enable USB debugging and run adb reverse).
)

echo.
echo [2/3] Checking your PC Local Wi-Fi IP address...
for /f "tokens=4" %%a in ('route print ^| findstr 0.0.0.0 ^| findstr /v "Default"') do (
    set LOCAL_IP=%%a
    goto :found_ip
)
:found_ip
echo     Your PC Local IP: %LOCAL_IP%
echo.
echo ===============================================================
echo   ACCESS URLS FOR YOUR MOBILE PHONE (BROWSER & APP):
echo   - Via Wi-Fi / Hotspot: http://%LOCAL_IP%:8000/admin-panel/dashboard/
echo   - Via USB Cable:       http://localhost:8000/admin-panel/dashboard/
echo   - On this PC:          http://127.0.0.1:8000/admin-panel/dashboard/
echo ===============================================================
echo.
echo [3/3] Starting Django Server on 0.0.0.0:8000...
echo.

cd backend
..\venv\Scripts\python.exe manage.py runserver 0.0.0.0:8000
pause
