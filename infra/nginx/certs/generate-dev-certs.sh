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

echo "Certificats de dev générés dans ${DIR} (localhost.pem / localhost-key.pem)."
