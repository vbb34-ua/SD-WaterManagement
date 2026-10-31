@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo ==============================================================
echo   COMPILANDO MONITOR (MODO PORTABLE)...
echo ==============================================================
javac -encoding UTF-8 WM_WS_M.java
if errorlevel 1 (
    echo Error al compilar WM_WS_M.java
    pause
    exit /b %errorlevel%
)

REM --- CONFIGURACION DE CONEXION A CENTRAL ---
set CENTRAL_HOST=%1
set CENTRAL_PORT=%2
set WS_ID=%3

if "%CENTRAL_HOST%"=="" set /p CENTRAL_HOST="Introduce el HOST del TCP Proxy de Central (ej. proxy.rlwy.net): "
if "%CENTRAL_PORT%"=="" set /p CENTRAL_PORT="Introduce el PUERTO del TCP Proxy de Central (ej. 12345): "
if "%WS_ID%"=="" set WS_ID=WS_01

echo.
echo ==============================================================
echo   ARRANCANDO MONITOR (WM_WS_M) - %WS_ID%
echo ==============================================================
echo Conectando a Central: %CENTRAL_HOST%:%CENTRAL_PORT%
echo Escuchando a Engine en puerto local: 7000
echo ==============================================================

java -cp . WM_WS_M 7000 %CENTRAL_HOST% %CENTRAL_PORT% %WS_ID%
pause