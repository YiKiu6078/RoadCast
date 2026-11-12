@echo off
cd /d "%~dp0"
call gradlew.bat :mobile:assembleDebug --console=plain %*
exit /b %errorlevel%
