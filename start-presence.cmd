@echo off
REM Purpose: Starts the packaged Presence application from Windows Command Prompt or PowerShell.
REM Why it is used: It checks for Java and the application package before opening the local website.
setlocal
set "PRESENCE_JAVA="

where java >nul 2>nul
if not errorlevel 1 set "PRESENCE_JAVA=java"

rem A portable Java runtime can be placed here so first-time users can run the
rem app even when Java has not yet been installed system-wide.
if not defined PRESENCE_JAVA (
  for /d %%D in ("build-tools\runtime-21\*") do (
    if exist "%%~fD\bin\java.exe" set "PRESENCE_JAVA=%%~fD\bin\java.exe"
  )
)

if not defined PRESENCE_JAVA (
  echo Java 21 is not available.
  echo Install a 64-bit Java 21 JDK, reopen Command Prompt, then run this file again.
  pause
  exit /b 1
)

set "PRESENCE_JAR=target\presence-camera-fix-v2.jar"
if not exist "%PRESENCE_JAR%" set "PRESENCE_JAR=target\presence-1.0.0.jar"

if not exist "%PRESENCE_JAR%" (
  echo The packaged app was not found.
  echo In this folder, run: mvn clean package
  echo Then start this file again.
  pause
  exit /b 1
)

echo Starting Presence at http://127.0.0.1:8081
echo Keep this window open while using Presence. Press Ctrl+C to stop it safely.
"%PRESENCE_JAVA%" -jar "%PRESENCE_JAR%" --server.port=8081 %*
endlocal
