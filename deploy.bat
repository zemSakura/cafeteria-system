@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1" %*
if errorlevel 1 (
    echo.
    echo Deployment failed. Check the error message above.
    pause
    exit /b 1
)

echo.
echo Deployment finished. Start the system with:
echo   deploy-output\run.bat
pause
