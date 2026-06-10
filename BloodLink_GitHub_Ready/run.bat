@echo off
echo Compiling BloodLink...
if not exist bin mkdir bin
javac -cp "lib\*" -d bin src\org\bloodbank\*.java
if %errorlevel% neq 0 (
    echo Compilation failed! Please check for errors.
    pause
    exit /b %errorlevel%
)
echo Starting BloodLink Server...
java -cp "bin;lib\*" org.bloodbank.WebServer
pause
