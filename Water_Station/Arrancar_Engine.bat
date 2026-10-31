@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo ==============================================================
echo   COMPILANDO ENGINE (MODO PORTABLE)...
echo ==============================================================
javac -encoding UTF-8 -cp ".;lib\*" WM_WS_E.java
if errorlevel 1 (
    echo Error al compilar WM_WS_E.java
    pause
    exit /b %errorlevel%
)

REM --- CONFIGURACION KAFKA Y MONITOR LOCAL ---
set KAFKA_BROKER=%1
set MONITOR_PORT=%2
set ID_WS=%3

if "%KAFKA_BROKER%"=="" set KAFKA_BROKER=iriguchi.proxy.rlwy.net:38689
if "%MONITOR_PORT%"=="" set MONITOR_PORT=7000
if "%ID_WS%"=="" set ID_WS=WS_01

echo.
echo ==============================================================
echo   ARRANCANDO ENGINE (WM_WS_E) - %ID_WS%
echo ==============================================================
echo Broker Kafka:        %KAFKA_BROKER%
echo Monitor Local:       localhost:%MONITOR_PORT%
echo ==============================================================

java -cp ".;lib\*" WM_WS_E %KAFKA_BROKER% localhost %MONITOR_PORT% %ID_WS%
pause