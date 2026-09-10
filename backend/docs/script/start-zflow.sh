#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_DIR="$PROJECT_ROOT/backend/docs/docker"

if [[ ! -f "$COMPOSE_DIR/.env" ]]; then
    echo "[ZFlow] Configure backend/docs/docker/.env from .env.example before starting."
    exit 1
fi

exec docker compose --project-directory "$COMPOSE_DIR" \
    -f "$COMPOSE_DIR/docker-compose.yml" up -d --build "$@"
