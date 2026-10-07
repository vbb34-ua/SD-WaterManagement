@echo off
echo ==============================================
echo   COMPILANDO EL SERVIDOR CENTRAL...
echo ==============================================
javac -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-simple.jar" *.java

echo.
echo ==============================================
echo   ARRANCANDO WATER MANAGEMENT SYSTEM...
echo ==============================================
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-simple.jar" WM_Central 5000
pause
