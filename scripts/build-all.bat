@echo off
echo ==============================================
echo Building OmniDrop (Backend & Frontend)
echo ==============================================

echo [1/2] Compiling Java Backend with Maven...
cd ..\backend
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Backend compilation failed!
    exit /b %ERRORLEVEL%
)

echo [2/2] Building Next.js Frontend...
cd ..\frontend
call npm install
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Frontend build failed!
    exit /b %ERRORLEVEL%
)

echo ==============================================
echo [SUCCESS] OmniDrop successfully built!
echo ==============================================
cd ..
