@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT_DIR=%%~fI"

if "%BACKEND_PORT%"=="" set "BACKEND_PORT=8080"
if "%FRONTEND_PORT%"=="" set "FRONTEND_PORT=8091"
if "%WAIT_SECONDS%"=="" set "WAIT_SECONDS=90"
if "%LOG_DIR%"=="" set "LOG_DIR=%ROOT_DIR%\.codex-runtime\logs\start-local"

set "BACKEND_DIR=%ROOT_DIR%\penmate-backend"
set "FRONTEND_DIR=%ROOT_DIR%\penmate-frontend"
set "BACKEND_HEALTH_URL=http://localhost:%BACKEND_PORT%/actuator/health"
set "FRONTEND_URL=http://localhost:%FRONTEND_PORT%/"

if "%~1"=="-h" goto :usage
if "%~1"=="--help" goto :usage
if "%~1"=="/?" goto :usage

echo Starting PenMate local development services without Docker.
echo Assuming local PostgreSQL 18.4 and Redis are already running.

if not exist "%BACKEND_DIR%\" (
  echo Backend directory not found: %BACKEND_DIR%
  exit /b 1
)

if not exist "%FRONTEND_DIR%\" (
  echo Frontend directory not found: %FRONTEND_DIR%
  exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo Missing required command: mvn
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo Missing required command: npm
  exit /b 1
)

call :backend_healthy
if not errorlevel 1 (
  echo Backend is already healthy at %BACKEND_HEALTH_URL%; reusing it.
) else (
  call :port_in_use "%BACKEND_PORT%"
  if not errorlevel 1 (
    echo Port %BACKEND_PORT% is already in use, but %BACKEND_HEALTH_URL% is not healthy.
    echo Stop that process or set BACKEND_PORT before running this script.
    exit /b 1
  )

  call :start_backend
  call :wait_for_backend
  if errorlevel 1 exit /b 1
  echo Backend is healthy at %BACKEND_HEALTH_URL%.
)

call :frontend_reachable
if not errorlevel 1 (
  echo Frontend is already reachable at %FRONTEND_URL%; reusing it.
) else (
  call :port_in_use "%FRONTEND_PORT%"
  if not errorlevel 1 (
    echo Port %FRONTEND_PORT% is already in use, but %FRONTEND_URL% is not reachable.
    echo Stop that process or set FRONTEND_PORT before running this script.
    exit /b 1
  )

  call :start_frontend
  echo Frontend is starting at %FRONTEND_URL%.
)

echo Logs:
echo   Backend:  %LOG_DIR%\backend.log
echo   Frontend: %LOG_DIR%\frontend.log
echo URLs:
echo   Backend health: %BACKEND_HEALTH_URL%
echo   Frontend:       %FRONTEND_URL%
exit /b 0

:usage
echo Usage: scripts\start-local.bat
echo.
echo Starts PenMate local development services without Docker.
echo.
echo Environment overrides:
echo   BACKEND_PORT       default: 8080
echo   FRONTEND_PORT      default: 8091
echo   WAIT_SECONDS       default: 90
echo   LOG_DIR            default: .codex-runtime\logs\start-local
echo.
echo Prerequisites:
echo   PostgreSQL 18.4 and Redis are already running locally.
exit /b 0

:backend_healthy
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-RestMethod -Uri '%BACKEND_HEALTH_URL%' -TimeoutSec 3; if ($r.status -eq 'UP') { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>nul
exit /b %errorlevel%

:frontend_reachable
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -UseBasicParsing -Uri '%FRONTEND_URL%' -TimeoutSec 3 | Out-Null; exit 0 } catch { exit 1 }" >nul 2>nul
exit /b %errorlevel%

:port_in_use
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort %~1 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
exit /b %errorlevel%

:start_backend
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
echo Starting backend on port %BACKEND_PORT%...
start "PenMate Backend" cmd /k "cd /d ""%BACKEND_DIR%"" && mvn -Dspring-boot.run.profiles=local spring-boot:run 1^>^> ""%LOG_DIR%\backend.log"" 2^>^&1"
exit /b 0

:start_frontend
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
echo Starting frontend on port %FRONTEND_PORT%...
start "PenMate Frontend" cmd /k "cd /d ""%FRONTEND_DIR%"" && npm run dev 1^>^> ""%LOG_DIR%\frontend.log"" 2^>^&1"
exit /b 0

:wait_for_backend
set /a "WAITED=0"
:wait_backend_loop
call :backend_healthy
if not errorlevel 1 exit /b 0
if %WAITED% GEQ %WAIT_SECONDS% (
  echo Backend did not become healthy within %WAIT_SECONDS%s. See %LOG_DIR%\backend.log
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Sleep -Seconds 2" >nul 2>nul
set /a "WAITED+=2"
goto :wait_backend_loop
