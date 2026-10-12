@echo off
setlocal

cd /d "%~dp0"

if not exist "target\asset-allocation-portal-0.0.1-SNAPSHOT.jar" (
    echo ERROR: Application JAR not found.
    exit /b 1
)

start "AssetPortalApp" java "-Duser.timezone=UTC" -jar "%CD%\target\asset-allocation-portal-0.0.1-SNAPSHOT.jar" --server.port=8081

exit /b 0
