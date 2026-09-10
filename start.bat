@echo off
setlocal DisableDelayedExpansion

rem Sestavi a spusti tuto kopii Elektronickeho deniku na http://localhost:8080
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

rem Stara lokalni .env nema soubor known_hosts. Pro lokalni spusteni zachovame
rem puvodni chovani; produkce ma nadale pouzivat overeni host key.
if not defined SSH_KNOWN_HOSTS_FILE if not defined SSH_KNOWN_HOSTS (
    set "SSH_STRICT_HOST_KEY_CHECKING=no"
    echo Upozorneni: SSH host key se pri tomto lokalnim spusteni neoveruje.
)

echo.
echo Sestavuji aplikaci...
call mvnw.cmd clean package
if errorlevel 1 (
    echo Sestaveni aplikace selhalo.
    pause
    exit /b 1
)

echo Spoustim tuto kopii Elektronickeho deniku na http://localhost:8080
echo Aplikaci ukoncis klavesami Ctrl+C v tomto okne.
echo.

rem Otevri prohlizec az po uspesnem nabehnuti aplikace.
start "" powershell.exe -NoProfile -WindowStyle Hidden -Command "$deadline=(Get-Date).AddSeconds(60); while ((Get-Date) -lt $deadline) { try { $response=Invoke-WebRequest -Uri 'http://localhost:8080/login' -UseBasicParsing -TimeoutSec 2; if ($response.StatusCode -ge 200) { Start-Process 'http://localhost:8080/login'; exit } } catch {}; Start-Sleep -Seconds 1 }"

set "APP_JAR="
for %%J in ("target\*.jar") do set "APP_JAR=%%~fJ"
if not defined APP_JAR (
    echo V adresari target nebyl nalezen spustitelny JAR soubor.
    pause
    exit /b 1
)
java -jar "%APP_JAR%"

if errorlevel 1 pause
