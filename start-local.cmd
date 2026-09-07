@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is required. Install Node.js and try again.
  pause
  exit /b 1
)
node scripts\start-local.cjs %*
if errorlevel 1 (
  echo Startup failed. See the message above and target\local-*.log.
  pause
  exit /b 1
)
endlocal
