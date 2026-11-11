@echo off
echo ==============================================
echo Starting OmniDrop Local Environment
echo ==============================================

echo Starting Java Backend in new terminal...
start "OmniDrop Backend Agent" cmd /k "cd ..\backend && mvn spring-boot:run"

echo Starting Next.js Frontend in new terminal...
start "OmniDrop Frontend Web UI" cmd /k "cd ..\frontend && npm run dev"

echo Both services launched!
echo Frontend: http://localhost:3000
echo Backend:  http://localhost:8080
