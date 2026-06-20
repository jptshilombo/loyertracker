#!/usr/bin/env bash
# Création outillée d'un compte bailleur (onboarding — piste B, variante A3-script).
#
# Industrialise la création manuelle d'un bailleur : crée le compte Keycloak, lui assigne le
# rôle realm BAILLEUR et pose un mot de passe TEMPORAIRE (changement forcé au 1er login). Le
# tenant applicatif (RLS) se crée automatiquement au 1er login via le flux d'inscription
# existant (POST /api/bailleurs/inscription, auto-appelé par le dashboard).
#
# Onboarding CONTRÔLÉ (pas d'inscription publique : registrationAllowed reste false) et SANS
# dépendance email (B1 : les identifiants sont affichés une fois, à transmettre hors-bande).
#
# Usage (depuis n'importe où ; la stack visée doit tourner ; COMPOSE_FILE sélectionne dev/staging/prod) :
#   ./infra/keycloak/creer-bailleur.sh <email> <prenom> <nom>
#   COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml ./infra/keycloak/creer-bailleur.sh marie@exemple.fr Marie Durand
#   COMPOSE_FILE=docker-compose.staging.yml               ./infra/keycloak/creer-bailleur.sh ...
#
# Idempotent : refuse de créer un doublon si l'email existe déjà dans le realm.
# Aucun secret versionné : les identifiants admin sont lus depuis .env ; le mot de passe
# temporaire est généré à l'exécution et affiché une seule fois.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_DIR"

REALM=loyertracker
ROLE=BAILLEUR
KC_INIT_SERVER="${KC_INIT_SERVER:-http://localhost:8080/auth}"

usage() {
  echo "Usage : $0 <email> <prenom> <nom>" >&2
  echo "  (COMPOSE_FILE sélectionne la stack ; défaut docker-compose.yml)" >&2
  exit 2
}

EMAIL="${1:-}"; PRENOM="${2:-}"; NOM="${3:-}"
[[ -n "$EMAIL" && -n "$PRENOM" && -n "$NOM" ]] || usage
[[ "$EMAIL" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || { echo "ERREUR : email invalide « $EMAIL »." >&2; exit 2; }

# Identifiants admin Keycloak depuis .env (jamais en dur, jamais versionnés).
[[ -f .env ]] || { echo "ERREUR : .env introuvable dans ${REPO_DIR}" >&2; exit 1; }
set -a; source .env; set +a
: "${KEYCLOAK_ADMIN:?KEYCLOAK_ADMIN requis dans .env}"
: "${KEYCLOAK_ADMIN_PASSWORD:?KEYCLOAK_ADMIN_PASSWORD requis dans .env}"

# Conteneur Keycloak de la stack visée (COMPOSE_FILE sélectionne dev/staging/prod).
KC="$(docker compose ps -q keycloak || true)"
[[ -n "$KC" ]] || { echo "ERREUR : service keycloak non démarré (COMPOSE_FILE=${COMPOSE_FILE:-docker-compose.yml})." >&2; exit 1; }

KCADM=/opt/keycloak/bin/kcadm.sh
kc() { docker exec "$KC" "$KCADM" "$@"; }

echo "[bailleur] Connexion à l'API d'admin Keycloak..."
kc config credentials --server "$KC_INIT_SERVER" --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

# Anti-doublon : refuser si l'email existe déjà (registrationEmailAsUsername=true).
EXIST="$(kc get users -r "$REALM" -q "email=${EMAIL}" --fields id --format csv --noquotes 2>/dev/null | tr -d '\r')"
if [[ -n "$EXIST" ]]; then
  echo "ERREUR : un compte avec l'email « ${EMAIL} » existe déjà (id=${EXIST}) — aucune création." >&2
  exit 3
fi

echo "[bailleur] Création du compte ${EMAIL}..."
kc create users -r "$REALM" \
  -s "username=${EMAIL}" -s "email=${EMAIL}" -s emailVerified=true -s enabled=true \
  -s "firstName=${PRENOM}" -s "lastName=${NOM}" >/dev/null
USER_ID="$(kc get users -r "$REALM" -q "email=${EMAIL}" --fields id --format csv --noquotes | tr -d '\r')"
[[ -n "$USER_ID" ]] || { echo "ERREUR : compte créé mais introuvable à la relecture." >&2; exit 1; }

# Mot de passe TEMPORAIRE (changement forcé au 1er login). Conforme aux politiques usuelles
# (majuscule, minuscule, chiffre, caractère spécial, longueur).
TEMP="Lt-$(openssl rand -hex 6)-Aa9!"
kc set-password -r "$REALM" --userid "$USER_ID" --new-password "$TEMP" --temporary

echo "[bailleur] Attribution du rôle realm ${ROLE}..."
kc add-roles -r "$REALM" --uusername "$EMAIL" --rolename "$ROLE"

echo "[bailleur] Compte prêt. Le tenant se créera au 1er login (inscription auto)."
echo "============================================================"
echo " IDENTIFIANTS À TRANSMETTRE (hors-bande, une seule fois) :"
echo "   URL      : voir le domaine public de la stack"
echo "   Login    : ${EMAIL}"
echo "   Mot de passe TEMPORAIRE : ${TEMP}"
echo "   (à changer obligatoirement à la 1re connexion)"
echo "============================================================"
