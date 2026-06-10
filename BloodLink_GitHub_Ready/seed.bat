@echo off
echo Building project...
call build.bat
if %errorlevel% equ 0 (
    echo Running Database Seeder...
    java -cp "bin;lib\*" org.bloodbank.DatabaseSeeder
    echo Done.
) else (
    echo Build failed, cannot seed.
)
