#!/usr/bin/env bash
# Restauration PostgreSQL de la stack LoyerTracker depuis une sauvegarde produite par
# backup-postgres.sh (lot Production Readiness 3). Objectif RTO < 1 h (drill : quelques minutes).
#
# DESTRUCTIF : la base ${POSTGRES_DB} courante est REMPLACÉE par le contenu du dump
# (schéma applicatif + tables Keycloak — restauration cohérente app/IdP).
#
# Usage (la stack visée est arrêtée ou non : le script gère l'orchestration) :
#   ./infra/backup/restore-postgres.sh <chemin/loyertracker-<stamp>.dump> [--yes]
#   COMPOSE_FILE=docker-compose.staging.yml ./infra/backup/restore-postgres.sh <dump> [--yes]
#
# Le fichier <stamp>.globals.sql jumeau (rôles) est appliqué s'il est présent à côté du dump
# — indispensable sur un volume PostgreSQL vierge (rôles loyertracker_api/loyertracker_batch).
# Après restauration : la stack complète est redémarrée ; valider avec
# ./infra/smoke/smoke-stack.sh (0 FAIL attendu).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_DIR"

DUMP="${1:?Usage: restore-postgres.sh <fichier.dump> [--yes]}"
[[ -f "$DUMP" ]] || { echo "ERREUR : dump introuvable : $DUMP" >&2; exit 1; }
GLOBALS="${DUMP%.dump}.globals.sql"

[[ -f .env ]] || { echo "ERREUR : .env introuvable dans ${REPO_DIR}" >&2; exit 1; }
set -a; source .env; set +a

if [[ "${2:-}" != "--yes" ]]; then
    echo "ATTENTION : la base '${POSTGRES_DB}' va être SUPPRIMÉE puis restaurée depuis :"
    echo "  ${DUMP}"
    read -r -p "Confirmer la restauration ? (taper 'oui') " REPLY
    [[ "$REPLY" == "oui" ]] || { echo "Abandon."; exit 1; }
fi

SECONDS=0

# 1. Arrêt des écrivains (API, Keycloak) — postgres seul reste nécessaire.
docker compose stop api keycloak keycloak-init nginx 2>/dev/null || true
docker compose up -d postgres

echo "-- attente de PostgreSQL..."
for _ in $(seq 1 30); do
    docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d postgres -q && break
    sleep 2
done
docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d postgres -q

# 2. Rôles cluster (volume vierge) — les erreurs « already exists » sont attendues et bénignes
#    sur un volume existant (psql continue sans ON_ERROR_STOP).
if [[ -f "$GLOBALS" ]]; then
    echo "-- application des globals (rôles) : $(basename "$GLOBALS")"
    docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -q < "$GLOBALS" || true
else
    echo "-- AVERTISSEMENT : pas de fichier globals jumeau (${GLOBALS}) — rôles supposés présents."
fi

# 3. Recréation de la base (connexions résiduelles terminées d'abord).
echo "-- recréation de la base ${POSTGRES_DB}"
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -q -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${POSTGRES_DB}' AND pid <> pg_backend_pid();" > /dev/null
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -q -c "DROP DATABASE IF EXISTS \"${POSTGRES_DB}\";"
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -q -c "CREATE DATABASE \"${POSTGRES_DB}\" OWNER \"${POSTGRES_USER}\";"

# 4. Restauration (propriétaires préservés : dump et restauration par le rôle admin).
echo "-- pg_restore : $(basename "$DUMP")"
docker compose exec -T postgres pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error < "$DUMP"

# 5. Redémarrage de la stack complète.
echo "-- redémarrage de la stack"
docker compose up -d

echo "== Restauration terminée en ${SECONDS}s — valider avec ./infra/smoke/smoke-stack.sh =="
