@echo off
chcp 65001 > nul
echo ==============================================================
echo   ARRANCANDO MONITOR DE ESTACION (WM_WS_M)
echo ==============================================================

REM --- CONFIGURACION DE CONEXION A CENTRAL ---
REM Sustituye estos valores por el host y puerto de tu TCP Proxy de Central en Railway
set CENTRAL_HOST=%1
set CENTRAL_PORT=%2
set WS_ID=%3

if "%CENTRAL_HOST%"=="" set /p CENTRAL_HOST="Introduce el HOST del TCP Proxy de Central (ej. autorack.proxy.rlwy.net): "
if "%CENTRAL_PORT%"=="" set /p CENTRAL_PORT="Introduce el PUERTO del TCP Proxy de Central (ej. 12345): "
if "%WS_ID%"=="" set WS_ID=WS_01

echo.
echo Conectando a Central: %CENTRAL_HOST%:%CENTRAL_PORT% para la estacion [%WS_ID%]...
echo Escuchando en puerto local 7000 para el Engine...
echo ==============================================================

java WM_WS_M 7000 %CENTRAL_HOST% %CENTRAL_PORT% %WS_ID%
pause
