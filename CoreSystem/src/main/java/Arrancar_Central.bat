@echo off
echo ==============================================
echo   COMPILANDO EL SERVIDOR CENTRAL...
echo ==============================================
set KAFKA_JAR="%USERPROFILE%\.m2\repository\org\apache\kafka\kafka-clients\3.7.0\kafka-clients-3.7.0.jar"
javac -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-simple.jar;%KAFKA_JAR%" *.java

echo.
echo ==============================================
echo   ARRANCANDO WATER MANAGEMENT SYSTEM...
echo ==============================================
java -cp ".;sqlite-jdbc.jar;slf4j-api.jar;slf4j-simple.jar;%KAFKA_JAR%" WM_Central 6000 localhost:9092
pause
