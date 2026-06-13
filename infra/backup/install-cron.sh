#!/usr/bin/env bash
# Installe (ou retire) le cron quotidien de sauvegarde PostgreSQL sur l'hôte
# (lot Production Readiness 4). Idempotent : repérage par marqueur, jamais de doublon.
#
# La planification effective de backup-postgres.sh (lot 3) se fait ici, côté hôte —
# elle n'a volontairement pas sa place dans le dépôt (couplée à la machine).
#
# Usage (depuis n'importe où) :
#   ./infra/backup/install-cron.sh                 # installe le cron (02h15, stack dev)
#   ./infra/backup/install-cron.sh --dry-run       # affiche la ligne sans rien installer
#   ./infra/backup/install-cron.sh --uninstall     # retire la ligne du crontab
#   COMPOSE_FILE=docker-compose.staging.yml ./infra/backup/install-cron.sh   # cible staging
#   SCHEDULE="30 3 * * *" ./infra/backup/install-cron.sh                      # autre horaire
#   BACKUP_DIR=/srv/backups ./infra/backup/install-cron.sh                    # cible de dépôt
#
# Vérifier après coup : crontab -l | grep loyertracker-backup

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Marqueur de fin de ligne : permet de retrouver et remplacer NOTRE entrée sans toucher
# aux autres tâches cron de l'utilisateur.
MARKER="# loyertracker-backup"
SCHEDULE="${SCHEDULE:-15 2 * * *}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
LOG_DIR="${BACKUP_DIR:-$HOME/loyertracker-backups}"

# Environnement transmis au script de backup (COMPOSE_FILE sélectionne dev/staging ;
# BACKUP_DIR n'est propagé que s'il a été défini explicitement).
ENV_PREFIX="COMPOSE_FILE=${COMPOSE_FILE}"
[[ -n "${BACKUP_DIR:-}" ]] && ENV_PREFIX+=" BACKUP_DIR=${BACKUP_DIR}"

CRON_CMD="cd ${REPO_DIR} && ${ENV_PREFIX} ./infra/backup/backup-postgres.sh >> \"${LOG_DIR}/backup.log\" 2>&1"
CRON_LINE="${SCHEDULE} ${CRON_CMD} ${MARKER}"

# crontab actuel (vide si aucun), débarrassé de toute entrée LoyerTracker préexistante.
current_crontab() { crontab -l 2>/dev/null || true; }
without_marker() { current_crontab | grep -vF "$MARKER" || true; }

case "${1:-}" in
  --dry-run)
    echo "# Ligne cron qui serait installée :"
    echo "$CRON_LINE"
    exit 0
    ;;
  --uninstall)
    without_marker | crontab -
    echo "OK  cron LoyerTracker retiré (s'il existait)."
    exit 0
    ;;
  "")
    : # installation (défaut)
    ;;
  *)
    echo "ERREUR : argument inconnu '$1' (attendu : --dry-run | --uninstall | rien)." >&2
    exit 2
    ;;
esac

# Pré-requis : le script de backup doit exister et le répertoire de log être créable.
[[ -x "${REPO_DIR}/infra/backup/backup-postgres.sh" ]] || {
  echo "ERREUR : ${REPO_DIR}/infra/backup/backup-postgres.sh introuvable ou non exécutable." >&2
  exit 1
}
mkdir -p "$LOG_DIR" && chmod 700 "$LOG_DIR"

# Réécrit le crontab : entrées existantes (hors la nôtre) + notre ligne à jour.
{ without_marker; echo "$CRON_LINE"; } | crontab -

echo "OK  cron installé :"
echo "    $CRON_LINE"
echo "    log : ${LOG_DIR}/backup.log"
echo "Vérifier : crontab -l | grep loyertracker-backup"
