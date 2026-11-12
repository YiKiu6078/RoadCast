@echo off
cd /d "%~dp0"
rem Configure ROADCAST_AUTH_ASSETS_DIR or local.properties before using this task.
call gradlew.bat :mobile:assembleStandaloneDebug --console=plain %*
exit /b %errorlevel%
