@echo off
REM Thin shim so reinstall.ps1 can be double-clicked or run from cmd.exe
powershell -ExecutionPolicy Bypass -NoProfile -File "%~dp0reinstall.ps1" %*
