@echo off
setlocal
cd /d "%~dp0"

echo ==========================================================
echo   SmartVision — One-Click Launcher for Windows
echo ==========================================================

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Java 21 was not found in your PATH.
    echo Please install Java 21 and configure PATH / JAVA_HOME.
    pause
    exit /b 1
)

set SMARTVISION_JAR=target\smartvision-1.0.0.jar
if not exist "%SMARTVISION_JAR%" (
    if exist "target\presence-camera-fix-v2.jar" (
        set SMARTVISION_JAR=target\presence-camera-fix-v2.jar
    ) else (
        echo [INFO] Building SmartVision with Maven...
        where mvn >nul 2>nul
        if %errorlevel% neq 0 (
            echo [ERROR] Maven is not installed or not in PATH.
            pause
            exit /b 1
        )
        call mvn clean package -DskipTests
        if %errorlevel% neq 0 (
            echo [ERROR] Maven build failed.
            pause
            exit /b 1
        )
    )
)

echo Starting SmartVision on http://127.0.0.1:8081 ...
start "" cmd /c "timeout /t 4 /nobreak >nul && start http://127.0.0.1:8081"
java -jar "%SMARTVISION_JAR%" --server.port=8081 --presence.camera-index=0 %*
