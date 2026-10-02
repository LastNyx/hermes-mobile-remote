@echo off
rem Pair a phone with the bridge. Double-click this, or run it from any prompt.
rem Shows a QR code (opened as a picture) for the Hermes Remote app to scan.
set BIN=%~dp0bridge\.venv\Scripts\hermes-remote-bridge.exe
if not exist "%BIN%" (
  echo The bridge is not installed yet. Run install.cmd first.
  echo.
  pause
  exit /b 1
)
set NAME=phone
set /p NAME=Name for this phone [phone]: 
if "%NAME%"=="" set NAME=phone
echo.
"%BIN%" pair "%NAME%"
set RC=%ERRORLEVEL%
if not "%RC%"=="0" (
  echo.
  echo Pairing failed. Run  "%BIN%" doctor  to see what is wrong.
)
echo.
pause
exit /b %RC%
