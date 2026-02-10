#!/bin/bash
set -e

cd ~/deployment

# Convert Windows line endings to Linux
sed -i 's/\r\$//' .env*

# Pull latest backend image
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend pull backend

# Restart backend container
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend up -d --force-recreate backend

# List running backend containers
docker ps --filter name=backend

# Show recent backend logs
docker logs backend --tail 40
