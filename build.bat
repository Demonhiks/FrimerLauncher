@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo Не найден javac. Установи JDK 17 или новее и добавь его в PATH.
    pause
    exit /b 1
)

if exist build rmdir /s /q build
mkdir build\classes

echo Компилирую исходники...
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 -nowarn -d build\classes @build\sources.txt
if errorlevel 1 (
    echo.
    echo Ошибка компиляции.
    pause
    exit /b 1
)

echo Собираю jar...
jar cfe build\FrimerLauncher.jar com.frimer.launcher.Main -C build\classes .
if errorlevel 1 (
    echo Ошибка сборки jar.
    pause
    exit /b 1
)

echo.
echo Готово: build\FrimerLauncher.jar
pause
