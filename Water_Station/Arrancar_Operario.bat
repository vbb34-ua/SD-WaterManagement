@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo ==============================================================
echo   COMPILANDO OPERARIO (MODO PORTABLE)...
echo ==============================================================
javac -encoding UTF-8 -cp ".;lib\*" WM_FO.java
if errorlevel 1 (
    echo Error al compilar WM_FO.java
    pause
    exit /b %errorlevel%
)

REM --- CONFIGURACION DE CONEXION ---
set KAFKA_BROKER=%1
set OP_ID=%2
set CENTRAL_HOST=%3
set CENTRAL_PORT=%4

if "%KAFKA_BROKER%"=="" set KAFKA_BROKER=iriguchi.proxy.rlwy.net:38689
if "%OP_ID%"=="" set OP_ID=FO_01
if "%CENTRAL_HOST%"=="" set /p CENTRAL_HOST="Introduce el HOST del TCP Proxy de Central (ej. proxy.rlwy.net): "
if "%CENTRAL_PORT%"=="" set /p CENTRAL_PORT="Introduce el PUERTO del TCP Proxy de Central (ej. 12345): "

echo.
echo ==============================================================
echo   ARRANCANDO FIELD OPERATOR (WM_FO) - %OP_ID%
echo ==============================================================
echo Broker Kafka:        %KAFKA_BROKER%
echo Central:             %CENTRAL_HOST%:%CENTRAL_PORT%
echo ==============================================================

java -cp ".;lib\*" WM_FO %KAFKA_BROKER% %OP_ID% %CENTRAL_HOST% %CENTRAL_PORT%
pause