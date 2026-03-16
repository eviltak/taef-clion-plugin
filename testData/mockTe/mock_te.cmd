@echo off
REM Wrapper to invoke mock_te.ps1 via pwsh, so GeneralCommandLine can execute it as an .exe-like process.
pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0mock_te.ps1" %*
exit /b %ERRORLEVEL%
