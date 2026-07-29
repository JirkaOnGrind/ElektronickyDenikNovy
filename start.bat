@echo off
setlocal DisableDelayedExpansion

rem Spusti tuto kopii Elektronickeho deniku na http://localhost:8080
cd /d "%~dp0"

set "JAVA_HOME="
for /d %%D in ("%~dp0jdk-*") do (
    set "JAVA_HOME=%%~fD"
    goto :java_found
)

:java_found
if not defined JAVA_HOME (
    where java >nul 2>nul
    if errorlevel 1 (
        echo Java 17 nebyla nalezena.
        pause
        exit /b 1
    )
) else (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if not exist ".env" (
    echo Chybi soubor .env s lokalni konfiguraci.
    pause
    exit /b 1
)

rem Nacte promenne ze souboru .env; prazdne radky a # komentare preskoci.
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"

echo.
echo Spoustim tuto kopii Elektronickeho deniku na http://localhost:8080
echo Aplikaci ukoncis klavesami Ctrl+C v tomto okne.
echo.

rem Otevri prohlizec az po uspesnem nabehnuti aplikace.
start "" powershell.exe -NoProfile -WindowStyle Hidden -Command "$deadline=(Get-Date).AddSeconds(60); while ((Get-Date) -lt $deadline) { try { $response=Invoke-WebRequest -Uri 'http://localhost:8080/login' -UseBasicParsing -TimeoutSec 2; if ($response.StatusCode -ge 200) { Start-Process 'http://localhost:8080/login'; exit } } catch {}; Start-Sleep -Seconds 1 }"

call mvnw.cmd spring-boot:run

if errorlevel 1 pause
