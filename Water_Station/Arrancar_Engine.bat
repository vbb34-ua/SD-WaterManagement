@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo ==============================================================
echo   COMPILANDO ENGINE CON MAVEN...
echo ==============================================================
call mvn package -B -DskipTests
if errorlevel 1 (
    echo Error al compilar WM_WS_E con Maven.
    pause
    exit /b %errorlevel%
)

echo.
echo ==============================================================
echo   ARRANCANDO ENGINE DE ESTACION (WM_WS_E)
echo ==============================================================

REM --- CONFIGURACION KAFKA Y MONITOR LOCAL ---
set KAFKA_BROKER=%1
set MONITOR_PORT=%2
set ID_WS=%3

if "%KAFKA_BROKER%"=="" set KAFKA_BROKER=iriguchi.proxy.rlwy.net:38689
if "%MONITOR_PORT%"=="" set MONITOR_PORT=7000
if "%ID_WS%"=="" set ID_WS=WS_01

echo.
echo Broker Kafka:        %KAFKA_BROKER%
echo Monitor Local:       localhost:%MONITOR_PORT%
echo Estacion ID:         %ID_WS%
echo ==============================================================

REM Ejecutamos desde el JAR compilado por Maven
java -cp "target\estaciones-operarios-1.0-SNAPSHOT.jar" WM_WS_E %KAFKA_BROKER% localhost %MONITOR_PORT% %ID_WS%
pause