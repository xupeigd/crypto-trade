@echo off
setlocal enabledelayedexpansion

REM =====================================================
REM Crypto-Trade Application Startup Script (Windows)
REM =====================================================

set "PROJECT_ROOT=%~dp0"
set "BACKEND_DIR=%PROJECT_ROOT%backend"
set "JAR_FILE=%BACKEND_DIR%\target\crypto-trade-backend-1.0.0.jar"
set "ENV_FILE=%BACKEND_DIR%\.env"

set "JAVA_OPTS=-XX:ReservedCodeCacheSize=256m -Xmx2g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

REM Proxy settings
set "PROXY=127.0.0.1:7890"
set "http_proxy=%PROXY%"
set "https_proxy=%PROXY%"
set "HTTP_PROXY=%PROXY%"
set "HTTPS_PROXY=%PROXY%"

set HTTP_PROXY=%PROXY%
set HTTPS_PROXY=%PROXY%
set FTP_PROXY=%PROXY%

echo ========================================
echo   Crypto-Trade Application Startup
echo ========================================
echo.

REM 1. Load environment variables
if exist "%ENV_FILE%" (
    echo ========================================
    echo Loading Environment Variables
    echo ========================================
    echo.
    for /f "delims=" %%a in ('type "%ENV_FILE%" ^| findstr /v /c:"^#" ^| findstr /v /c:"^ $"') do (
        set "line=%%a"
        for /f "tokens=1* delims==" %%b in ("!line!") do (
            if /i "%%b"=="CRYPTO_TRADE_PASSWD" (
                echo  [OK] %%b=***
            ) else (
                echo  [OK] %%b=%%c
            )
            set "%%b=%%c"
        )
    )
    echo.
    echo [OK] Environment variables loaded
    echo.
) else (
    echo [WARNING] .env file not found (%ENV_FILE%)
    echo [WARNING] Using default configuration
    echo.
)

echo ========================================
echo   Crypto-Trade Application Startup
echo ========================================
echo.

REM 2. Check jar file exists
if not exist "%JAR_FILE%" (
    echo [ERROR] Jar file not found
    echo  Expected: %JAR_FILE%
    echo.
    echo Please build first:
    echo   ./build-all.sh
    echo.
    exit /b 1
)

echo [OK] Jar file found
echo  %JAR_FILE%
echo.

REM 3. Check Java
where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Please install JDK 17+
    exit /b 1
)

for /f "tokens=2 delims==" %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    echo [OK] Java version: %%i
)
echo.

REM 4. Check port 8080
netstat -ano | findstr :8080 | findstr LISTENING >nul 2>&1
if not errorlevel 1 (
    echo [WARNING] Port 8080 is in use
    echo.
    for /f "tokens=5" %%p in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
        echo  Process PID: %%p
        set "KILL_PID=%%p"
    )
    echo.

    set /p "answer=Terminate the process? (y/N): "
    if /i "!answer!"=="y" (
        echo Terminating process...
        taskkill /F /PID !KILL_PID! >nul 2>&1
        timeout /t 1 >nul
        echo [OK] Port released
    ) else (
        echo [ERROR] Startup cancelled
        exit /b 1
    )
)
echo.

REM 5. Start application
echo ========================================
echo Starting Application...
echo ========================================
echo.
echo [INFO] JVM opts: %JAVA_OPTS%
echo.
echo [INFO] Access URLs:
echo   Frontend:  http://localhost:8080/
echo   Backend:   http://localhost:8080/api/health
echo.
echo [INFO] Press Ctrl+C to stop
echo.

cd /d "%BACKEND_DIR%"
java %JAVA_OPTS% -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -jar "%JAR_FILE%"

echo.
echo ========================================
echo Application Stopped
echo ========================================
endlocal
pause
