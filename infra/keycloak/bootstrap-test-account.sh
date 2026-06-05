#!/usr/bin/env bash
# Bootstrap DEV uniquement : positionne le mot de passe du compte de test
# bailleur-test@test.local du realm loyertracker, à partir d'une variable
# d'environnement (.env) — aucun secret n'est versionné dans realm-loyertracker.json.
#
# Idempotent : ré-exécutable sans effet de bord (re-positionne simplement le mot de passe).
# Lancé automatiquement par le service `keycloak-init` après que Keycloak soit healthy.
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER="${KC_INIT_SERVER:-http://keycloak:8080/auth}"
REALM=loyertracker
TEST_EMAIL=bailleur-test@test.local

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

echo "[init] Compte de test prêt (mot de passe issu de .env). Terminé."
