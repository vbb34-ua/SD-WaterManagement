@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo ==============================================================
echo   COMPILANDO MONITOR CON MAVEN...
echo ==============================================================
call mvn package -B -DskipTests
if errorlevel 1 (
    echo Error al compilar WM_WS_M con Maven.
    pause
    exit /b %errorlevel%
)

echo.
echo ==============================================================
echo   ARRANCANDO MONITOR DE ESTACION (WM_WS_M)
echo ==============================================================

REM --- CONFIGURACION DE CONEXION A CENTRAL ---
set CENTRAL_HOST=%1
set CENTRAL_PORT=%2
set WS_ID=%3

if "%CENTRAL_HOST%"=="" set /p CENTRAL_HOST="Introduce el HOST del TCP Proxy de Central (ej. proxy.rlwy.net): "
if "%CENTRAL_PORT%"=="" set /p CENTRAL_PORT="Introduce el PUERTO del TCP Proxy de Central (ej. 12345): "
if "%WS_ID%"=="" set WS_ID=WS_01

echo.
echo Conectando a Central: %CENTRAL_HOST%:%CENTRAL_PORT% para la estacion [%WS_ID%]...
echo Escuchando en puerto local 7000 para el Engine...
echo ==============================================================

REM Ejecutamos desde el JAR compilado por Maven
java -cp "target\estaciones-operarios-1.0-SNAPSHOT.jar" WM_WS_M 7000 %CENTRAL_HOST% %CENTRAL_PORT% %WS_ID%
pause