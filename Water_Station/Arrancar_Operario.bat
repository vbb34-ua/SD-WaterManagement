@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo ==============================================================
echo   COMPILANDO WM_FO CON MAVEN...
echo ==============================================================
call mvn package -B -DskipTests
if errorlevel 1 (
    echo Error al compilar WM_FO con Maven.
    pause
    exit /b %errorlevel%
)

echo.
echo ==============================================================
echo   ARRANCANDO FIELD OPERATOR (WM_FO)
echo ==============================================================

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
echo Broker Kafka:        %KAFKA_BROKER%
echo Operador ID:         %OP_ID%
echo Central:             %CENTRAL_HOST%:%CENTRAL_PORT%
echo ==============================================================

REM Ejecutamos desde el JAR compilado por Maven
java -cp "target\estaciones-operarios-1.0-SNAPSHOT.jar" WM_FO %KAFKA_BROKER% %OP_ID% %CENTRAL_HOST% %CENTRAL_PORT%
pause