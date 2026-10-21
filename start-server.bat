@echo off
setlocal
cd /d "%~dp0"
title FartMC - Local Minecraft 26.2
echo Starting local Minecraft 26.2 with sounds. Java 25 is detected automatically.
echo Account verification is disabled. Connect to 127.0.0.1:25565.
echo Minecraft EULA: https://aka.ms/MinecraftEULA
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test-server\start.ps1" -Version 26.2 -Offline -AcceptEula -WithSounds %*
set "serverExit=%ERRORLEVEL%"
if not "%serverExit%"=="0" echo Server failed to start. See the error above.
if not defined FARTMC_NO_PAUSE pause
exit /b %serverExit%
