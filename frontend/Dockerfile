# syntax=docker/dockerfile:1
# Image web de production = Nginx point d'entrée unique (ADR-08) : sert la SPA Angular
# ET reverse-proxy /api + /auth. Construire depuis la RACINE du dépôt afin d'embarquer
# à la fois le code frontend et la configuration Nginx partagée :
#   docker build -f frontend/Dockerfile -t loyertracker-web .

# ---- Étape build : compilation Angular en mode production ----
FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build -- --configuration production

# ---- Étape run : Nginx servant les assets + config reverse-proxy ----
FROM nginx:1.27-alpine
# Patche les paquets OS au-delà de l'image de base (gate Trivy HIGH/CRITICAL en CI),
# même approche que l'image API : ne pas attendre la reconstruction upstream.
RUN apk -U upgrade --no-cache
# Assets statiques de la SPA (sortie du builder application : dist/<projet>/browser).
COPY --from=build /app/dist/loyertracker/browser /usr/share/nginx/html
# Configuration Nginx unique (TLS, en-têtes de sécurité, proxy /api et /auth, fallback SPA).
# Les certificats TLS ne sont PAS embarqués (secrets) : montés au runtime sous /etc/nginx/certs.
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
# Exécution non-root (lot Production Readiness 2) : utilisateur `nginx` (uid 101) fourni par
# l'image de base ; la conf écoute sur des ports non privilégiés et écrit pid/temp sous /tmp.
# Prérequis runtime : les certificats montés doivent être lisibles par cet uid.
USER nginx
EXPOSE 8080 8443
