@echo off
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" (
    start "Pahal License Manager" "%JAVA_HOME%\bin\javaw.exe" -jar "pahal-license-manager.jar"
    exit /b
)
where javaw >nul 2>nul
if errorlevel 1 (
    echo Java 17 or newer is required on the application owner's computer.
    pause
    exit /b 1
)
start "Pahal License Manager" javaw -jar "pahal-license-manager.jar"
