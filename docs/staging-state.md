# État Staging — LoyerTracker

> Rapport de déploiement staging réel et de smoke test contre staging (plan Production
> Readiness, lot 4b). Document vivant : à mettre à jour à chaque déploiement staging.
> Cadre : CGPA v5.0.1 — clôture de la réserve **R-4** (jalon Gate Staging Readiness v4.0).

## 1. Contexte

| Champ | Valeur |
|---|---|
| Date | 2026-06-14 |
| Branche | `chore/prodready-lot4b-staging-smoke` |
| Lot | Production Readiness 4b — déploiement staging réel + smoke ciblé |
| Hôte | Serveur partagé `ai-test-server` (IP privée `172.31.11.102`) |
| Reverse proxy mutualisé | nginx-proxy-manager (occupe 80/443 de l'hôte) + autres stacks (loyerpro, outils labo) |
| Approche d'intégration | Ports alternatifs, smoke local (option retenue) — **aucune modification de l'infra partagée** |

La stack LoyerTracker est déployée de façon **isolée** (projet Compose `loyertracker-staging`,
réseau bridge dédié), sans toucher au reverse proxy mutualisé ni aux stacks voisines.

## 2. Configuration du déploiement

| Paramètre | Valeur |
|---|---|
| Fichier Compose | `docker-compose.staging.yml` |
| Source des images | GHCR (`ghcr.io/jptshilombo`), tag immuable — **jamais `latest`** (ADR-08, lot 1) |
| `LOYERTRACKER_TAG` | `sha-4e0d3995` (= `main` HEAD au moment du déploiement) |
| Ports hôte (web) | `WEB_HTTP_PORT=18080` → 8080, `WEB_HTTPS_PORT=18443` → 8443 (paramétrables, lot 4b) |
| Ports internes | `api`, `keycloak`, `postgres` **non publiés** sur l'hôte (joignables uniquement via Nginx) |
| Issuer Keycloak | `https://localhost/auth/realms/loyertracker` — **canonique, sans port** (`KC_HOSTNAME=localhost`) |
| TLS | certificat de dev auto-signé (SAN `localhost`), monté en lecture seule (uid 101) |
| `.env` | 21 clés, secrets générés sur l'hôte, jamais versionnés |

Le paramétrage des ports web (`WEB_HTTP_PORT`/`WEB_HTTPS_PORT`, défaut 80/443) permet de
cohabiter avec un reverse proxy mutualisé sans modifier l'infra partagée. L'issuer reste
**portless** malgré le port alternatif : `KC_HOSTNAME=localhost` force l'hôte canonique et
Nginx transmet l'en-tête `Host` sans port — confirmé via l'endpoint de découverte OIDC.

## 3. Vérifications de plateforme

| Contrôle | Attendu | Résultat |
|---|---|---|
| Tous les services `healthy` | 4/4 | ✅ api, keycloak, postgres, nginx |
| `keycloak-init` (one-shot) | exit 0 | ✅ exit 0 |
| Issuer réel (découverte OIDC sur :18443) | `https://localhost/auth/realms/loyertracker` | ✅ portless, = `KEYCLOAK_ISSUER_URI` |
| `/healthz` (port web HTTP) | 200 `ok` | ✅ |
| `/api/actuator/prometheus` **public** (via Nginx TLS) | 404 (bloqué) | ✅ 404 |
| `/api/actuator/prometheus` **interne** (réseau Docker, api:8080) | métriques 200 | ⚠️ voir §5 (défaut 4a trouvé et corrigé) |

## 4. Smoke test runtime contre staging

Commande (depuis `~/loyertracker` sur l'hôte staging) :

```
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.staging.yml ./infra/smoke/smoke-stack.sh
```

**Résultat : 46 PASS / 0 FAIL.**

Couverture prouvée en conditions réelles (au-delà des tests Testcontainers / JWT simulés) :

- Sanity : 10 migrations Flyway, pool API connecté sous `loyertracker_api` (NOSUPERUSER,
  NOBYPASSRLS → RLS `FORCE` réellement exercée), Actuator `UP` via Nginx TLS.
- JWT réels Keycloak via Nginx ; **issuer portless** validé contre `KEYCLOAK_ISSUER_URI`.
- Parcours S01→S04 complet : inscription, bien, bail, invitation→acceptation (Admin API réelle),
  affectation 8 %, échéances (SECURITY DEFINER), pointage, honoraire 72,00 € sur la période
  pointée, alertes dont **PREAVIS** à J+75, audit.
- Matrice de rôles : `403` honoraire/audit côté gestionnaire, scoping des biens affectés.
- **Isolation cross-tenant live** : 2e bailleur → 0 fuite (biens, paiements, honoraires, alertes).
- Garde-fous : `401` sans jeton, **ports internes non publiés** sur l'hôte.

Échafaudage `directAccessGrants` activé puis **révoqué automatiquement** (trap EXIT) ; aucune
modification versionnée du realm ; comptes suffixés par un id de run (script rejouable).

## 5. Écarts trouvés en déploiement et corrigés (lot 4b)

Le déploiement réel sur hôte partagé a révélé quatre écarts, tous corrigés sur la branche
(commits cités), testés, et re-vérifiés sur staging (sauf le scrape interne, cf. résidu) :

1. **Healthcheck Nginx faux-négatif (IPv6).** La sonde `wget http://localhost:8080/healthz`
   échouait (`connection refused`) : `/etc/hosts` résout `localhost` en `::1` d'abord et le
   `wget` busybox (image alpine) tente l'IPv6, alors que Nginx n'écoute qu'en IPv4
   (`listen 8080;`). Corrigé en sondant `127.0.0.1` (déterministe).
   *`docker-compose.yml`, `docker-compose.staging.yml`.* → re-vérifié : nginx `healthy`.

2. **Smoke — comparaison d'issuer avec port.** Le smoke comparait l'`iss` du jeton à `$BASE`,
   qui porte un port alternatif sur hôte partagé, alors que l'issuer reste portless. Corrigé
   en comparant à `KEYCLOAK_ISSUER_URI` (= l'URI réellement validée par l'API).
   *`infra/smoke/smoke-stack.sh`.* → re-vérifié : `issuer = https://localhost/auth/realms/loyertracker`.

3. **Smoke — détection de publication de port.** `docker compose port api 8080` renvoie
   `invalid IP:0` (et non du vide) sur cette version de Compose quand le port n'est pas publié,
   d'où un faux `FAIL`. Corrigé en extrayant le port hôte et en traitant `0`/vide comme
   « non publié ». *`infra/smoke/smoke-stack.sh`.* → re-vérifié : PASS.

4. **Sécurité — scrape interne Prometheus en 401 (défaut lot 4a).** Le lot 4a a exposé
   `/api/actuator/prometheus` (application.yml) et documenté son scrape interne **sans jeton**
   (runbook), mais `SecurityConfig` ne mettait en liste blanche que `health`/`info` : l'endpoint
   tombait sous `.anyRequest().authenticated()` → **401** pour un scrapeur du réseau Docker.
   L'endpoint n'est pas publié sur l'hôte (port 8080 interne) et Nginx le bloque publiquement
   (404 ✅) : l'accès interne sans jeton est le contrat voulu. Corrigé en ajoutant
   `/api/actuator/prometheus` au `permitAll`, avec test de régression sécurité.
   *`backend/.../SecurityConfig.java`, `SecurityIntegrationTest.java`.*

### Résidu (non bloquant) — confirmation live du correctif #4

Le correctif #4 touche l'image **api**, or les images ne sont publiées sur GHCR que sur push
`main` (ci.yml). La stack staging tourne donc encore `sha-4e0d3995` (pré-correctif) : le scrape
interne renverra 200 (métriques) **après** le passage par la chaîne de livraison standard
(merge `main` → CI publie le nouveau `sha-<8>` → redéploiement staging avec ce tag → re-run du
contrôle interne). Le correctif est validé au niveau unitaire (`SecurityIntegrationTest` vert).
Aucun impact sécurité : l'endpoint reste bloqué publiquement (404) sur l'image déployée.

## 6. Gate Staging Readiness (CGPA v4.0) — clôture R-4

| Critère | Verdict |
|---|---|
| 1. Déploiement par image GHCR, tag immuable (jamais `latest`) | ✅ `sha-4e0d3995` |
| 2. Stack complète `healthy` (4/4) | ✅ |
| 3. Point d'entrée unique Nginx TLS ; ports internes non publiés | ✅ |
| 4. Issuer OIDC correct (canonique, portless) | ✅ |
| 5. RLS `FORCE` réellement exercée (pool sous rôle restreint) | ✅ |
| 6. Isolation cross-tenant prouvée en live | ✅ 0 fuite |
| 7. Parcours métier S01→S04 de bout en bout | ✅ 46/46 |
| 8. Observabilité : `/healthz` OK, Prometheus bloqué publiquement | ✅ (scrape interne : §5 résidu) |
| 9. Smoke contre staging rejouable et versionné | ✅ `infra/smoke/smoke-stack.sh` |

**Verdict : GO (avec un résidu non bloquant)** — réserve **R-4** levée. Le seul résidu est la
confirmation *live* du correctif sécurité #4, subordonnée au redéploiement post-merge (chaîne
de livraison standard) ; il n'affecte ni la sécurité publique ni le smoke.

## 7. Procédure de redéploiement / rollback

- **Déploiement** : sur l'hôte, `.env` en place (ports alternatifs), puis
  `docker compose -f docker-compose.staging.yml up -d` avec `LOYERTRACKER_TAG=sha-<8>`.
- **Rollback** : redéployer avec le `LOYERTRACKER_TAG` précédent (tags immuables).
- **Re-vérification observabilité** après redéploiement post-merge :
  `docker compose -f docker-compose.staging.yml exec nginx wget -qO- http://api:8080/api/actuator/prometheus | head` <!-- gitleaks:allow (URL de service interne, aucun secret) -->
  (attendu : métriques, 200) ; `curl -sk -o /dev/null -w '%{http_code}' https://localhost:18443/api/actuator/prometheus` (attendu : 404).
