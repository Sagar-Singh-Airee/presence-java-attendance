@echo off
REM Purpose: Starts the packaged SmartVision application from Windows Command Prompt or PowerShell.
REM Why it is used: It checks for Java and the application package before opening the local website.
setlocal
set "SMARTVISION_JAVA="

where java >nul 2>nul
if not errorlevel 1 set "SMARTVISION_JAVA=java"

rem A portable Java runtime can be placed here so first-time users can run the
rem app even when Java has not yet been installed system-wide.
if not defined SMARTVISION_JAVA (
  for /d %%D in ("build-tools\runtime-21\*") do (
    if exist "%%~fD\bin\java.exe" set "SMARTVISION_JAVA=%%~fD\bin\java.exe"
  )
)

if not defined SMARTVISION_JAVA (
  echo Java 21 is not available.
  echo Install a 64-bit Java 21 JDK, reopen Command Prompt, then run this file again.
  pause
  exit /b 1
)

set "SMARTVISION_JAR=target\presence-camera-fix-v2.jar"
if not exist "%SMARTVISION_JAR%" set "SMARTVISION_JAR=target\smartvision-ui-v4.jar"
if not exist "%SMARTVISION_JAR%" set "SMARTVISION_JAR=target\presence-camera-ui-v3.jar"
if not exist "%SMARTVISION_JAR%" set "SMARTVISION_JAR=target\smartvision-1.0.0.jar"
if not exist "%SMARTVISION_JAR%" set "SMARTVISION_JAR=target\presence-1.0.0.jar"

if not exist "%SMARTVISION_JAR%" (
  echo The packaged app was not found.
  echo In this folder, run: mvn clean package
  echo Then start this file again.
  pause
  exit /b 1
)

echo Starting SmartVision at http://127.0.0.1:8081
echo Your browser will open automatically in 4 seconds...
echo Keep this window open while using SmartVision. Press Ctrl+C to stop it safely.
start "" cmd /c "timeout /t 4 /nobreak >nul & start http://127.0.0.1:8081"
"%SMARTVISION_JAVA%" -jar "%SMARTVISION_JAR%" --server.port=8081 %*
endlocal
