#!/usr/bin/env bash
# remote-deploy.sh <version>  - runs on the Contabo server. Pulls the exact tag, records it in
# .env.backend, restarts ONLY the backend service, health-checks it, and rolls back to the
# previous version on failure. Prints DEPLOY_OK <version> as the last line on success.
set -u

VERSION="$1"
IMAGE="geofrey2025/cisystem-backend:${VERSION}"
# BADILISHA: dir iliyo na docker-compose.yml + .env + .env.backend + .env.frontend kwenye Contabo.
DIR="/root/deployment"
ENV_FILE="$DIR/.env.backend"
COMPOSE="docker compose --env-file .env --env-file .env.backend --env-file .env.frontend"
SERVICE="backend"
TIMEOUT=120   # seconds to wait for Spring Boot to report "Started ...Application"

cd "$DIR" || { echo "[backend] FATAL: $DIR haipo"; exit 1; }

say() { echo "[backend] $*"; }

# Previous version ("" on the very first deploy - rollback is then impossible, only report).
PREV=""
if [ -f "$ENV_FILE" ]; then
    PREV=$(grep -E '^BACKEND_VERSION=' "$ENV_FILE" | tail -1 | cut -d= -f2 | tr -d '[:space:]')
fi
say "Toleo la sasa: ${PREV:-<none>}  ->  $VERSION"

say "docker pull $IMAGE"
if ! docker pull -q "$IMAGE" >/dev/null; then
    say "FATAL: pull imeshindwa - image haipo Docker Hub au hakuna network. Hakuna kilichobadilishwa."
    exit 1
fi

# Health check: container must be running AND Spring Boot must have printed its startup line
# (or be a container that already has 'Started ...' from an earlier life - we check fresh logs).
wait_healthy() {
    local cid="$1" waited=0
    while [ "$waited" -lt "$TIMEOUT" ]; do
        local state
        state=$(docker inspect -f '{{.State.Status}} {{.State.ExitCode}}' "$cid" 2>/dev/null || echo "gone 1")
        case "$state" in
            gone*|exited*|dead*) return 1 ;;   # container crashed - no point waiting longer
        esac
        if docker logs "$cid" 2>&1 | grep -qE 'Started [A-Za-z0-9_]+Application in [0-9.]+ seconds'; then
            return 0
        fi
        # JVM-level fatal - fail fast instead of waiting out the whole timeout.
        if docker logs "$cid" 2>&1 | grep -qE 'APPLICATION FAILED TO START|Error starting ApplicationContext'; then
            return 1
        fi
        sleep 3; waited=$((waited + 3))
    done
    return 1
}

printf 'BACKEND_VERSION=%s\n' "$VERSION" > "$ENV_FILE"
say "Recreating $SERVICE ..."
if ! $COMPOSE up -d --force-recreate "$SERVICE" >/dev/null; then
    say "FATAL: compose up imeshindwa."
else
    CID=$($COMPOSE ps -q "$SERVICE" 2>/dev/null | head -1)
    if [ -n "$CID" ] && wait_healthy "$CID"; then
        say "DEPLOY_OK $VERSION (container $CID)"
        docker logs "$CID" --tail 15 2>&1 | sed 's/^/    /'
        exit 0
    fi
    say "Health check IMESHINDWA - container hajathibitisha 'Started ...Application' ndani ya ${TIMEOUT}s."
    [ -n "$CID" ] && docker logs "$CID" --tail 40 2>&1 | sed 's/^/    /'
fi

# -- ROLLBACK ------------------------------------------------
if [ -z "$PREV" ]; then
    say "Hakuna toleo la awali la kurejesha (rollback haiwezekani)."
    exit 1
fi
say "ROLLBACK -> $PREV ..."
printf 'BACKEND_VERSION=%s\n' "$PREV" > "$ENV_FILE"
if $COMPOSE up -d --force-recreate "$SERVICE" >/dev/null; then
    CID=$($COMPOSE ps -q "$SERVICE" 2>/dev/null | head -1)
    if [ -n "$CID" ] && wait_healthy "$CID"; then
        say "ROLLBACK_OK - server imerudi kwenye $PREV"
    else
        say "ROLLBACK_IMESHINDWA - container ya $PREV pia haikuwa healthy. Kagua kwa mkono!"
    fi
else
    say "ROLLBACK_IMESHINDWA - compose up ya $PREV imeshindwa. Kagua kwa mkono!"
fi
exit 1
