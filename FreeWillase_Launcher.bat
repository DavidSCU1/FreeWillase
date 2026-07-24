@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

:: FreeWillase - Integrated Launcher
:: "Free Will Restored - Environment Auto-Setup"

title FreeWillase Launcher
color 0B

echo.
echo  ==========================================================
echo            FreeWillase Integrated Enzyme Platform
echo  ==========================================================
echo.

:: 1. Docker Check & Start
echo [1/5] Checking Docker Infrastructure...
docker-compose up -d
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker failed to start. Please ensure Docker Desktop is running.
    pause
    exit /b %ERRORLEVEL%
)

:: 2. MiniFold API Setup
echo [2/5] Setting up MiniFold API Environment...
cd /d %~dp0MiniFold
where python >nul 2>nul
set PYTHON_CMD=python
if %ERRORLEVEL% NEQ 0 (
    where python3 >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        set PYTHON_CMD=python3
    ) else (
        echo [ERROR] Python not found. Please install Python.
        pause
        exit /b 1
    )
)

echo Installing MiniFold dependencies...
%PYTHON_CMD% -m pip install -r requirements.txt --quiet
start "MiniFold-API" cmd /k "cd /d %~dp0MiniFold && %PYTHON_CMD% minifold_api.py"

:: 3. Java Backend Setup (Maven)
echo [3/5] Starting FreeWillase Backend (Maven Auto-Sync)...
echo  - MiniFold API: http://localhost:9001
timeout /t 3 /nobreak >nul
start "FreeWillase-Backend" cmd /k "cd /d %~dp0 && mvnw.cmd spring-boot:run"

:: 4. Frontend Dependencies (NPM Install)
echo [4/5] Checking Frontend Dependencies (npm install)...
cd /d %~dp0frontend
if not exist node_modules (
    echo node_modules not found, running npm install...
    call npm install
) else (
    echo node_modules exists, skipping npm install...
)

:: 5. Frontend Start
echo [5/5] Launching Frontend UI (Port 5173)...
echo.
echo ----------------------------------------------------------
echo  System initializing, please wait...
echo  - Frontend: http://localhost:5173
echo  - Backend API: http://localhost:8081/api
echo  - MiniFold API: http://localhost:9001
echo ----------------------------------------------------------
echo.

npm run dev

pause
