@echo off
echo ==============================================
echo   COMPILANDO WM_FO...
echo ==============================================
javac WM_FO.java

echo.
echo ==============================================
echo   ARRANCANDO FIELD OPERATOR (WM_FO)...
echo ==============================================
REM Parametros: <ip_broker:puerto> <id_operador> [ip_central] [puerto_central]
java WM_FO localhost:9092 FO_01 localhost 5000
pause
