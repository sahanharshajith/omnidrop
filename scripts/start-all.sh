#!/usr/bin/env bash
echo "=============================================="
echo "Starting OmniDrop Local Environment"
echo "=============================================="

# Launch Backend
(cd "$(dirname "$0")/../backend" && mvn spring-boot:run) &
BACKEND_PID=$!

# Launch Frontend
(cd "$(dirname "$0")/../frontend" && npm run dev) &
FRONTEND_PID=$!

echo "OmniDrop processes running (Backend PID: $BACKEND_PID, Frontend PID: $FRONTEND_PID)"
echo "Frontend: http://localhost:3000"
echo "Backend:  http://localhost:8080"

trap "kill $BACKEND_PID $FRONTEND_PID" SIGINT SIGTERM
wait
