@echo off
chcp 65001 >nul
cd /d "%~dp0"

if not exist build\FrimerLauncher.jar (
    echo Сначала собери проект: build.bat
    pause
    exit /b 1
)

start "" javaw -jar build\FrimerLauncher.jar
