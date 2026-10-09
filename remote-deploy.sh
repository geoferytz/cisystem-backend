#!/usr/bin/env bash
# remote-deploy.sh <version>  - runs on the Contabo server. Pulls the exact tag, records it in
# .env.backend, restarts ONLY the backend service, health-checks it, and rolls back to the
# previous version on failure. Prints DEPLOY_OK <version> as the last line on success.
set -u

VERSION="$1"
IMAGE="geofrey2025/cisystem-backend:${VERSION}"
SERVICE="backend"
TIMEOUT=120   # seconds to wait for Spring Boot to report "Started ...Application"

say() { echo "[backend] $*"; }

# --- Tafuta deployment dir -------------------------------------------------
# 1) Kama container ya backend inarun sasa, compose iliiwekea label yenye dir halisi.
#    Hii ndiyo njia ya uhakika zaidi - hakuna kubahatisha path.
DIR=""
CID_OLD=$(docker ps -q --filter "label=com.docker.compose.service=$SERVICE" | head -1)
if [ -n "$CID_OLD" ]; then
    DIR=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' "$CID_OLD" 2>/dev/null)
    [ -n "$DIR" ] && say "Deployment dir (kutoka container inayorun): $DIR"
fi
# 2) Fallback: tafuta docker-compose file kwenye maeneo ya kawaida.
if [ -z "$DIR" ]; then
    for d in /root /root/deployment /root/cisystem /opt/deployment /opt/cisystem /srv/deployment /home/*/deployment; do
        for f in docker-compose.yml docker-compose.yaml compose.yml compose.yaml; do
            if [ -f "$d/$f" ]; then DIR="$d"; say "Deployment dir (imepatikana kwa kutafuta): $DIR"; break 2; fi
        done
    done
fi
if [ -z "$DIR" ] || [ ! -d "$DIR" ]; then
    say "FATAL: sijaona deployment dir yenye docker-compose.yml. Weka DIR=... kwenye script hii."
    exit 1
fi

ENV_FILE="$DIR/.env.backend"

cd "$DIR" || { echo "[backend] FATAL: $DIR haipo"; exit 1; }

# Jenga env-file args kutoka files zilizopo tu - kama .env.frontend haipo Contabo,
# kuitaja kwenye --env-file kungesababisha compose ifail kabla hata ya pull.
COMPOSE="docker compose"
for ef in .env .env.backend .env.frontend; do
    [ -f "$ef" ] && COMPOSE="$COMPOSE --env-file $ef"
done
say "Compose env files: $COMPOSE"

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
