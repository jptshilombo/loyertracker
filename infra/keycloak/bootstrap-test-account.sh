#!/usr/bin/env bash
# Bootstrap DEV uniquement, après import du realm :
#   1. positionne le mot de passe du compte de test bailleur-test@test.local ;
#   2. positionne le secret du client confidentiel loyertracker-admin (Admin API, R6),
# tous deux à partir de variables d'environnement (.env) — aucun secret n'est versionné
# dans realm-loyertracker.json.
#
# Idempotent : ré-exécutable sans effet de bord (re-positionne simplement les valeurs).
# Lancé automatiquement par le service `keycloak-init` après que Keycloak soit healthy.
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER="${KC_INIT_SERVER:-http://keycloak:8080/auth}"
REALM=loyertracker
TEST_EMAIL=bailleur-test@test.local
ADMIN_CLIENT_ID=loyertracker-admin

echo "[init] Connexion à l'API d'admin Keycloak (${SERVER})..."
for i in $(seq 1 30); do
  if "$KCADM" config credentials --server "$SERVER" --realm master \
       --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  echo "[init] Keycloak pas encore prêt — nouvelle tentative ($i/30)..."
  sleep 5
done

echo "[init] Recherche du compte de test ${TEST_EMAIL}..."
USER_ID="$("$KCADM" get users -r "$REALM" -q "email=${TEST_EMAIL}" --fields id --format csv --noquotes | head -1)"
if [ -z "${USER_ID}" ]; then
  echo "[init] ERREUR : compte de test introuvable dans le realm ${REALM}." >&2
  exit 1
fi

echo "[init] Positionnement du mot de passe du compte de test..."
"$KCADM" set-password -r "$REALM" --userid "$USER_ID" \
  --new-password "$KEYCLOAK_TEST_BAILLEUR_PASSWORD"

# Secret du client Admin API loyertracker-admin (R6) : injecté hors dépôt, pour que le
# flux client_credentials de l'API corresponde au secret configuré dans .env. Idempotent.
echo "[init] Positionnement du secret du client ${ADMIN_CLIENT_ID}..."
ADMIN_CLIENT_UUID="$("$KCADM" get clients -r "$REALM" -q "clientId=${ADMIN_CLIENT_ID}" --fields id --format csv --noquotes | head -1)"
if [ -z "${ADMIN_CLIENT_UUID}" ]; then
  echo "[init] ERREUR : client ${ADMIN_CLIENT_ID} introuvable dans le realm ${REALM}." >&2
  exit 1
fi
"$KCADM" update "clients/${ADMIN_CLIENT_UUID}" -r "$REALM" \
  -s "secret=${KEYCLOAK_API_CLIENT_SECRET}"

echo "[init] Compte de test et client Admin API prêts (secrets issus de .env). Terminé."
