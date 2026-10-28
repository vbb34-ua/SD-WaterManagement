@echo off
chcp 65001 > nul
echo ==============================================================
echo   ARRANCANDO ENGINE DE ESTACION (WM_WS_E)
echo ==============================================================

REM --- CONFIGURACION KAFKA Y MONITOR LOCAL ---
set KAFKA_BROKER=%1
set MONITOR_PORT=%2

if "%KAFKA_BROKER%"=="" set KAFKA_BROKER=iriguchi.proxy.rlwy.net:38689
if "%MONITOR_PORT%"=="" set MONITOR_PORT=7000

echo.
echo Broker Kafka:        %KAFKA_BROKER%
echo Monitor Local:       localhost:%MONITOR_PORT%
echo ==============================================================

java WM_WS_E %KAFKA_BROKER% localhost %MONITOR_PORT%
pause
