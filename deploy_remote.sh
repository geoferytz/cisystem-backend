#!/usr/bin/env bash
set -e


cd /home/ubuntu/deployment

echo "=== Pulling latest backend image ==="
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend pull backend

echo "=== Restarting backend container ==="
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend up -d --force-recreate backend

echo "=== Running containers ==="
docker ps --filter name=backend

echo "=== Last backend logs ==="
container_name=

if [ -n "" ]; then
    docker logs "" --tail 40
else
    echo "Backend container not found"
fi
