@echo off
if not exist bin mkdir bin
javac -cp "lib\*" -d bin src\org\bloodbank\*.java
if %errorlevel% equ 0 (
    echo Compilation successful.
) else (
    echo Compilation failed.
)
