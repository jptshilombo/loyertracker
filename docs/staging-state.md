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
| `LOYERTRACKER_TAG` | **`sha-26f16caa`** (= `main` HEAD courant) — historique des redéploiements en §8 ; gate prononcé sur `sha-4e0d3995` |
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

### Résidu (non bloquant) — confirmation live du correctif #4 → **levé**

Le correctif #4 touche l'image **api**, or les images ne sont publiées sur GHCR que sur push
`main` (ci.yml). Au moment du gate, la stack tournait encore `sha-4e0d3995` (pré-correctif).
**Résidu levé** après le passage par la chaîne de livraison standard : redéploiement sur
`sha-e7067215` (post-merge PR #29) puis sur `sha-26f16caa` (HEAD courant), avec scrape interne
Prometheus **200** (tag `application="loyertracker-api"`) et public **404** confirmés live à
chaque redéploiement (cf. §8). Le correctif était déjà validé au niveau unitaire
(`SecurityIntegrationTest` vert).

## 6. Gate Staging Readiness (CGPA v4.0) — clôture R-4

| Critère | Verdict |
|---|---|
| 1. Déploiement par image GHCR, tag immuable (jamais `latest`) | ✅ `sha-26f16caa` (courant ; gate prononcé sur `sha-4e0d3995`) |
| 2. Stack complète `healthy` (4/4) | ✅ |
| 3. Point d'entrée unique Nginx TLS ; ports internes non publiés | ✅ |
| 4. Issuer OIDC correct (canonique, portless) | ✅ |
| 5. RLS `FORCE` réellement exercée (pool sous rôle restreint) | ✅ |
| 6. Isolation cross-tenant prouvée en live | ✅ 0 fuite |
| 7. Parcours métier S01→S04 de bout en bout | ✅ 46/46 |
| 8. Observabilité : `/healthz` OK, Prometheus bloqué publiquement | ✅ scrape interne 200 confirmé live (résidu §5 levé, cf. §8) |
| 9. Smoke contre staging rejouable et versionné | ✅ `infra/smoke/smoke-stack.sh` |

**Verdict : GO** — réserve **R-4** levée, **zéro résidu**. Le résidu de confirmation *live* du
correctif sécurité #4 a été levé par les redéploiements post-merge (cf. §8).

## 7. Procédure de redéploiement / rollback

- **Déploiement** : sur l'hôte, `.env` en place (ports alternatifs), puis
  `docker compose -f docker-compose.staging.yml up -d` avec `LOYERTRACKER_TAG=sha-<8>`.
- **Rollback** : redéployer avec le `LOYERTRACKER_TAG` précédent (tags immuables).
- **Re-vérification observabilité** après redéploiement post-merge :
  `docker compose -f docker-compose.staging.yml exec nginx wget -qO- http://api:8080/api/actuator/prometheus | head` <!-- gitleaks:allow (URL de service interne, aucun secret) -->
  (attendu : métriques, 200) ; `curl -sk -o /dev/null -w '%{http_code}' https://localhost:18443/api/actuator/prometheus` (attendu : 404).

## 8. Historique des redéploiements

Chaîne de livraison standard : merge `main` → CI publie `sha-<8>` sur GHCR → redéploiement
staging avec ce tag (`LOYERTRACKER_TAG`) → re-vérification observabilité + smoke.

| Date | Tag déployé | Origine | Healthy | Smoke | Scrape Prometheus interne / public | Note |
|---|---|---|---|---|---|---|
| 2026-06-14 | `sha-4e0d3995` | `main` HEAD au lot 4b | 4/4 | 46/0 | — / 404 | Déploiement initial (gate Staging GO) ; correctif #4 pas encore dans l'image → scrape interne 401 (résidu §5) |
| 2026-06-14 | `sha-e7067215` | post-merge PR #29 | 4/4 | 46/0 | **200** / 404 | Correctif sécurité #4 embarqué → scrape interne confirmé live ; résidu §5 levé |
| 2026-06-14 | `sha-26f16caa` | `main` HEAD courant (post PR #31/#32) | 4/4 | 46/0 | **200** / 404 | Réalignement du tag déployé sur `main` HEAD. Delta depuis `sha-e7067215` = **documentation uniquement** (`runbook-exploitation.md`, `project-state.md`) : images api/web fonctionnellement identiques |

> Le réalignement sur `sha-26f16caa` n'apporte aucun changement fonctionnel (delta doc-only
> depuis l'image précédente) ; il maintient la traçabilité « tag déployé = `main` HEAD ».

## 9. Exposition publique (URL dédiée) — **EN COURS**

> Plan d'Exécution approuvé le 2026-06-14 (Niveau 3). Arbitrages PO : sous-domaine
> `loyertracker.staging.loyerpro.org` ; **accès restreint au niveau nginx-proxy-manager**
> (Access List) en plus du login Keycloak ; exécution partagée (repo/DNS côté Claude Code,
> `.env` hôte + npm côté exploitant). **Aucune reconstruction d'image** : le SPA est agnostique
> à l'origine (`url: '/auth'`, `redirectUri: window.location.origin`) et le backend/Keycloak
> sont pilotés par variables d'environnement. L'image `sha-26f16caa` est réutilisée telle quelle.

**URL cible :** `https://loyertracker.staging.loyerpro.org` (point d'entrée unique : SPA + `/api` + `/auth`).

État d'avancement :

| Étape | Responsable | Statut |
|---|---|---|
| Route 53 — `A loyertracker.staging.loyerpro.org → 51.102.234.232` (TTL 300) | Claude Code | ✅ créé (résout) |
| Realm — `redirectUris`/`webOrigins`/`post.logout` du client `loyertracker-spa` + domaine public (localhost conservé) | Claude Code | ✅ commité (`infra/keycloak/realm-loyertracker.json`) — à appliquer au Keycloak vivant (Console/Admin API, sans reimport) |
| `.env` hôte — `KC_HOSTNAME`, `KEYCLOAK_ISSUER_URI`, `APP_CORS_ALLOWED_ORIGIN`, `APP_INVITATION_BASE_URL` = domaine public (`KEYCLOAK_JWK_SET_URI` reste interne) | Exploitant | ⏳ en attente |
| nginx-proxy-manager — *Proxy Host* → `https://127.0.0.1:18443` (Websockets, ignore invalid SSL amont), cert Let's Encrypt, **+ Access List** (allowlist IP / basic-auth) | Exploitant | ⏳ en attente |
| Redéploiement (`up -d`, restart `keycloak`) + vérifs + smoke ciblé | Exploitant / Claude Code | ⏳ en attente |

**⚠️ L'application n'est PAS encore joignable publiquement** : tant que le *Proxy Host* npm
(+ Access List) et le `.env` hôte ne sont pas en place, le sous-domaine résout vers l'hôte mais
n'est routé vers aucune stack. À ne marquer « exposée » qu'après validation des critères ci-dessous.

**Critères d'acceptation (à vérifier une fois en place) :**
1. `https://.../healthz` → 200 `ok` (cert Let's Encrypt valide).
2. Découverte OIDC publique → `issuer = https://loyertracker.staging.loyerpro.org/auth/realms/loyertracker`.
3. `/api/actuator/prometheus` **public → 404** (régression sécurité maintenue).
4. Parcours navigateur : login OIDC/PKCE bailleur → dashboard → appel `/api` authentifié OK.
5. `BASE=https://loyertracker.staging.loyerpro.org ./infra/smoke/smoke-stack.sh` → 46/0.
6. Access List npm effective (accès refusé hors allowlist) ; stacks voisines (LoyerPro) inchangées.

**Rollback :** supprimer le *Proxy Host* npm + l'enregistrement Route 53 ; restaurer `.env`
(`KC_HOSTNAME=localhost`, issuer/CORS/invitation `https://localhost`), `up -d`, restart Keycloak ;
`git revert` de l'édit realm. Aucune donnée détruite (pas de reimport realm).
