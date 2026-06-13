#!/usr/bin/env bash
# Sauvegarde PostgreSQL de la stack LoyerTracker (lot Production Readiness 3).
#
# Stratégie (RPO 24 h) : dump logique quotidien de la base complète (schéma applicatif
# + tables Keycloak, qui partagent la même base — restauration cohérente app/IdP) :
#   - <stamp>.dump        : pg_dump format custom -Fc (compressé, restauration sélective)
#   - <stamp>.globals.sql : pg_dumpall --globals-only (rôles loyertracker_api/loyertracker_batch
#                           et leurs mots de passe — objets cluster, absents du pg_dump)
# Rétention : 7 sauvegardes quotidiennes + 4 hebdomadaires (copie du dimanche, 28 jours).
#
# Usage (depuis n'importe où ; la stack visée doit être démarrée) :
#   ./infra/backup/backup-postgres.sh
#   COMPOSE_FILE=docker-compose.staging.yml ./infra/backup/backup-postgres.sh   # stack staging
#   BACKUP_DIR=/srv/backups ./infra/backup/backup-postgres.sh                   # cible dédiée
#
# Planification quotidienne (cron de l'hôte, cf. runbook lot 4) :
#   15 2 * * * cd /home/ubuntu/loyertracker && ./infra/backup/backup-postgres.sh >> "$HOME/loyertracker-backups/backup.log" 2>&1
#
# Les sauvegardes contiennent des données et des hashes de mots de passe de rôles :
# répertoire 700, fichiers 600, JAMAIS versionnés (hors du dépôt par défaut).

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_DIR"

# Variables de connexion depuis .env (jamais en dur, jamais versionnées).
[[ -f .env ]] || { echo "ERREUR : .env introuvable dans ${REPO_DIR}" >&2; exit 1; }
set -a; source .env; set +a

BACKUP_DIR="${BACKUP_DIR:-$HOME/loyertracker-backups}"
DAILY_DIR="${BACKUP_DIR}/daily"
WEEKLY_DIR="${BACKUP_DIR}/weekly"
STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP="${DAILY_DIR}/loyertracker-${STAMP}.dump"
GLOBALS="${DAILY_DIR}/loyertracker-${STAMP}.globals.sql"

mkdir -p "$DAILY_DIR" "$WEEKLY_DIR"
chmod 700 "$BACKUP_DIR" "$DAILY_DIR" "$WEEKLY_DIR"

# La stack (au moins postgres) doit tourner — COMPOSE_FILE sélectionne dev ou staging.
if ! docker compose ps --status running postgres --format '{{.Service}}' | grep -q '^postgres$'; then
    echo "ERREUR : le service postgres n'est pas démarré (COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.yml})." >&2
    exit 1
fi

echo "== Sauvegarde de ${POSTGRES_DB} (${STAMP}) vers ${DAILY_DIR} =="

# 1. Objets cluster (rôles + mots de passe) — requis avant pg_restore sur un volume vierge.
docker compose exec -T postgres pg_dumpall -U "$POSTGRES_USER" --globals-only > "$GLOBALS"

# 2. Base complète, format custom (compressé).
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > "$DUMP"

chmod 600 "$DUMP" "$GLOBALS"

# 3. Vérification d'intégrité : le dump doit être lisible par pg_restore (liste du contenu).
#    Lecture via stdin implicite : sous `docker compose exec`, le chemin /dev/stdin n'est
#    pas exploitable par pg_restore.
docker compose exec -T postgres pg_restore --list < "$DUMP" > /dev/null
echo "OK  dump vérifié : $(du -h "$DUMP" | cut -f1) ($(basename "$DUMP"))"

# 4. Copie hebdomadaire le dimanche (conservée 28 jours).
if [[ "$(date +%u)" == "7" ]]; then
    cp -p "$DUMP" "$GLOBALS" "$WEEKLY_DIR/"
    echo "OK  copie hebdomadaire (dimanche) déposée dans ${WEEKLY_DIR}"
fi

# 5. Rétention : 7 jours en quotidien, 28 jours en hebdomadaire.
find "$DAILY_DIR"  -name 'loyertracker-*' -mtime +7  -delete
find "$WEEKLY_DIR" -name 'loyertracker-*' -mtime +28 -delete

echo "== Sauvegarde terminée — rétention appliquée (quotidien 7 j, hebdo 28 j) =="
