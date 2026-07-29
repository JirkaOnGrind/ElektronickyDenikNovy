@echo off
setlocal

set "PROJECT_DIR=C:\Users\Utrh\Documents\New project\ElektronickyDenik-main"

if not exist "%PROJECT_DIR%" (
  echo Projektova slozka nebyla nalezena:
  echo %PROJECT_DIR%
  pause
  exit /b 1
)

set "JAVA_HOME="
for /d %%D in ("%PROJECT_DIR%\jdk-*") do (
  set "JAVA_HOME=%%~fD"
  goto :java_found
)

:java_found
if not defined JAVA_HOME (
  echo Nenasel jsem lokalni JDK ve slozce projektu.
  echo Ocekavam neco jako %PROJECT_DIR%\jdk-17...
  pause
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%PROJECT_DIR%\.env" (
  echo Soubor .env nebyl nalezen:
  echo %PROJECT_DIR%\.env
  pause
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%\.env") do (
  if not "%%A"=="" if not "%%A:~0,1"=="#" set "%%A=%%B"
)

cd /d "%PROJECT_DIR%"
call mvnw.cmd spring-boot:run

pause
