#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] Building Docker test images..."
docker compose --profile test build backend-test frontend-test

echo "[2/3] Running backend tests in Docker..."
docker compose --profile test run --rm backend-test

echo "[3/3] Running frontend tests in Docker..."
docker compose --profile test run --rm frontend-test

echo "All Dockerized tests passed."
