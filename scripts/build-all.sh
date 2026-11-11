#!/usr/bin/env bash
set -e

echo "=============================================="
echo "Building OmniDrop (Backend & Frontend)"
echo "=============================================="

echo "[1/2] Compiling Java Backend with Maven..."
cd "$(dirname "$0")/../backend"
mvn clean package -DskipTests

echo "[2/2] Building Next.js Frontend..."
cd "../frontend"
npm install
npm run build

echo "=============================================="
echo "[SUCCESS] OmniDrop successfully built!"
echo "=============================================="
