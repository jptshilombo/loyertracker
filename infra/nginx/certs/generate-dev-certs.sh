#!/usr/bin/env bash
# Génère un certificat TLS auto-signé pour le développement local (localhost).
# Les .pem produits sont gitignorés (jamais versionnés). À exécuter une fois avant `docker compose up`.
#
#   ./infra/nginx/certs/generate-dev-certs.sh
#
# En production, utiliser un vrai certificat (Let's Encrypt / autorité interne), pas ce script.

set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

openssl req -x509 -nodes -newkey rsa:2048 \
    -keyout "${DIR}/localhost-key.pem" \
    -out    "${DIR}/localhost.pem" \
    -days 365 \
    -subj "/C=FR/ST=Dev/L=Dev/O=LoyerTracker/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

# Le conteneur Nginx tourne non-root (uid 101) : la clé doit lui être lisible via le mont :ro.
# Acceptable ici : certificat de DEV auto-signé uniquement (jamais un vrai certificat).
chmod 644 "${DIR}/localhost-key.pem" "${DIR}/localhost.pem"

echo "Certificats de dev générés dans ${DIR} (localhost.pem / localhost-key.pem)."
