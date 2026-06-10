@echo off
set DB_FILE=bloodbank.db

if exist %DB_FILE% (
    echo Resetting Database...
    echo Removing %DB_FILE%...
    del %DB_FILE%
    echo Database file removed. 
    echo.
    echo The next time you run build.bat and run.bat, a fresh empty database will be created.
    echo Default Admin: admin / admin123
) else (
    echo No database file found to reset.
)
pause
