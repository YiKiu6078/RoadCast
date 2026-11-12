@echo off
cd /d "%~dp0"
rem Source builds do not require private runtime identity assets.
call gradlew.bat :mobile:assembleDebug :companion:assembleDebug --console=plain %*
if errorlevel 1 exit /b %errorlevel%
if not exist "dist\companion" mkdir "dist\companion"
copy /y "mobile\build\outputs\apk\debug\mobile-debug.apk" "dist\companion\RoadCast-host.apk" >nul
if errorlevel 1 exit /b %errorlevel%
copy /y "companion\build\outputs\apk\debug\companion-debug.apk" "dist\companion\RoadCast-companion.apk" >nul
exit /b %errorlevel%
