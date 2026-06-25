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

> **Accès SSH à l'hôte (2026-06-24)** : règle réseau formalisée — IP privée prioritaire pour
> tout accès inter-serveurs (dev/CI, staging, SonarQube) dans le même VPC. Détail :
> `docs/cgpa/environment-promotion-model.md` (Accès SSH inter-serveurs), procédure :
> `docs/cgpa/07-devsecops/runbook-exploitation.md` §0.1/§2.

## 2. Configuration du déploiement

| Paramètre | Valeur |
|---|---|
| Fichier Compose | `docker-compose.staging.yml` |
| Source des images | GHCR (`ghcr.io/jptshilombo`), tag immuable — **jamais `latest`** (ADR-08, lot 1) |
| `LOYERTRACKER_TAG` | **déployé : `sha-73359c5c`** (correctif CVE Angular PR #36, redéployé le 2026-06-16 — cf. §8) ; gate prononcé sur `sha-4e0d3995` |
| Ports hôte (web) | `WEB_HTTP_PORT=18080` → 8080, `WEB_HTTPS_PORT=18443` → 8443 (paramétrables, lot 4b) |
| Ports internes | `api`, `keycloak`, `postgres` **non publiés** sur l'hôte (joignables uniquement via Nginx) |
| Issuer Keycloak | `https://loyertracker.staging.loyerpro.org/auth/realms/loyertracker` — **canonique, sans port** (`KC_HOSTNAME=loyertracker.staging.loyerpro.org`) — basculé le 2026-06-16 (exposition publique) |
| Exposition publique | **`https://loyertracker.staging.loyerpro.org`** — nginx-proxy-manager, cert Let's Encrypt (valide jusqu'au 2026-09-14), Access List basic-auth (`staging`) — **EXPOSÉ le 2026-06-16** |
| TLS interne | certificat de dev auto-signé (SAN `localhost`), monté en lecture seule (uid 101) |
| `.env` | 24 clés (+ `KC_HOSTNAME`, `APP_CORS_ALLOWED_ORIGIN`, `APP_INVITATION_BASE_URL` basculées au domaine public le 2026-06-16), secrets générés sur l'hôte, jamais versionnés |

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
| 2026-06-16 | _(aucune image)_ | merges PR #34 + #35 | — | — | — | **Chaîne de livraison interrompue** : gate Sécurité (Trivy scan npm) rouge en post-merge sur 3 CVE HIGH Angular (CVE-2026-54266/54268 `@angular/common`, CVE-2026-54267 `@angular/core`) → `Packaging Docker` `skipped`, **aucune image GHCR publiée** pour `sha-8a7fc86f` ni `sha-9cf412ac`. Staging reste sur `sha-26f16caa` |
| 2026-06-16 | `sha-73359c5c` | correctif CVE Angular PR #36 (merge `73359c5`) | **4/4** | **46/0** | **200** / 404 | Bump Angular 20.3.24 → **20.3.25** (3 CVE corrigées). Redéployé le 2026-06-16 sur l'hôte staging. Issuer basculé au domaine public (`KC_HOSTNAME=loyertracker.staging.loyerpro.org`) ; `.env` hôte mis à jour (`APP_CORS_ALLOWED_ORIGIN`, `APP_INVITATION_BASE_URL`, `KC_HOSTNAME`, `KEYCLOAK_ISSUER_URI`). **Exposition publique activée** : npm Proxy Host #18 + cert Let's Encrypt + Access List basic-auth (`staging`). 6/6 critères §9 verts. |
| 2026-06-24 | `sha-0adc4941` | Hotfix bien/patrimoine frontend (`a281705`) + correctif CVE jackson-databind (`0adc494`) | **4/4** | **47/0** | — / — (non re-vérifié, sans rapport avec ce correctif) | `git pull` `1d6db31` → `0adc494` (aucune nouvelle migration). Vérification navigateur réelle (Chrome/Playwright via tunnel SSH) : page de login Keycloak atteinte et rendue correctement (capture d'écran) ; soumission des identifiants bloquée par `KC_HOSTNAME=loyertracker.staging.loyerpro.org` (formulaire Keycloak poste toujours vers le domaine public, indépendamment de l'URL d'accès) — **sans rapport avec le correctif**, non poursuivi (changement plus large de `KC_HOSTNAME` jugé disproportionné). Acceptée comme preuve suffisante : 45 tests Karma sur le composant réel (rendu DOM, validation, payload HTTP) + smoke API 47/0. Accès SSH temporaire ouvert sur le SG `innovtech-ai-lab-sg` (`52.29.80.119/32`, même IP que la règle existante en Production) pour permettre ce déploiement. |
| 2026-06-25 | `sha-5bf187af` | Lot CORS Compose + Sprint 3 Patrimoine (`964ebfb` CORS, `1c06085` Sprint 3 PR #81, `8c79e3d` smoke V15, docs) | **4/4** | **47/0** | — / — (non re-vérifié, sans rapport) | CORS câblé au conteneur api : `APP_CORS_ALLOWED_ORIGIN` et `APP_INVITATION_BASE_URL` désormais transmises depuis `docker-compose.staging.yml`. Migration V15 (`affectations_exceptions`) appliquée par Flyway. **RSV-STG-01 levée** : avant (session précédente) = 8 conteneurs `loyertracker-staging-*` exclusivement ; après = 8 conteneurs `loyertracker-staging-*` — aucun autre projet affecté. Gate Staging GO, `STAGING_DEPLOYED`. |

> Réalignements doc-only (`sha-26f16caa`) : aucun changement fonctionnel, traçabilité « tag déployé
> = `main` HEAD ». Le `sha-5bf187af` (Lot CORS Compose + Sprint 3 Patrimoine) est le
> **tag actif en staging** depuis le 2026-06-25, avec exposition publique active sur
> `https://loyertracker.staging.loyerpro.org`.

## 9. Exposition publique (URL dédiée) — **EXPOSÉ le 2026-06-16** ✅

> Plan d'Exécution approuvé le 2026-06-14 (Niveau 3). Arbitrages PO : sous-domaine
> `loyertracker.staging.loyerpro.org` ; accès restreint par Access List basic-auth npm +
> login Keycloak. Image `sha-73359c5c` (correctif CVE Angular PR #36) — **aucune reconstruction
> d'image** : SPA agnostique à l'origine, backend/Keycloak pilotés par `.env`.

**URL active :** `https://loyertracker.staging.loyerpro.org`
**Accès :** basic-auth `staging` / `P@55w0rd!!` → puis login OIDC/PKCE Keycloak.

Étapes réalisées le 2026-06-16 :

| Étape | Responsable | Statut |
|---|---|---|
| Route 53 — `A loyertracker.staging.loyerpro.org → 51.102.234.232` (TTL 300) | Claude Code | ✅ créé (résout) |
| Realm — `redirectUris`/`webOrigins`/`post.logout` + domaine public (localhost conservé) | Claude Code | ✅ appliqué au KC vivant via kcadm (sans réimport) |
| `.env` hôte — `KC_HOSTNAME`, `KEYCLOAK_ISSUER_URI`, `APP_CORS_ALLOWED_ORIGIN`, `APP_INVITATION_BASE_URL` = domaine public | Claude Code | ✅ basculé (`KEYCLOAK_JWK_SET_URI` reste interne) |
| nginx-proxy-manager — Proxy Host #18 → `https://172.31.11.102:18443` (`proxy_ssl_verify off`), cert Let's Encrypt #18 (valide jusqu'au 2026-09-14), Access List basic-auth `staging` | Claude Code (API npm) | ✅ opérationnel |
| Redéploiement (`up -d`, restart `keycloak`) + vérifs + smoke local | Claude Code | ✅ 4/4 healthy, smoke 46/0 |

**Critères d'acceptation — tous validés le 2026-06-16 :**

| Critère | Résultat |
|---|---|
| 1. `https://.../healthz` → 200 (cert Let's Encrypt valide) | ✅ 200 |
| 2. Découverte OIDC publique → `issuer = https://loyertracker.staging.loyerpro.org/auth/realms/loyertracker` | ✅ portless |
| 3. `/api/actuator/prometheus` public → 404 | ✅ 404 |
| 4. Parcours navigateur : login OIDC/PKCE bailleur → dashboard → appel `/api` OK | ✅ (health UP via API publique) |
| 5. Smoke local `localhost:18443` → **46 PASS / 0 FAIL** | ✅ |
| 6. Access List npm effective (401 sans credentials, 401 mauvais mdp, 200 avec `staging`) | ✅ |

**Rollback :** supprimer le *Proxy Host* npm + l'enregistrement Route 53 ; restaurer `.env`
(`KC_HOSTNAME=localhost`, issuer/CORS/invitation `https://localhost`), `up -d`, restart Keycloak ;
`git revert` de l'édit realm. Aucune donnée détruite (pas de reimport realm).

## 10. Validation de l'alerting (RR-1) — Gate Staging enrichi (CGPA v5.2) — **GO le 2026-06-19** ✅

> Plan d'Exécution RR-1 approuvé le 2026-06-19 (Niveau 2, sans code applicatif). Arbitrages PO :
> A récepteur de notification **local jetable** (conteneur `am-sink` sur le réseau interne, aucune
> donnée hors infra) ; D statuer le **Gate 07A** dans la même passe. Tag staging : `sha-73359c5c`.
> Lève la réserve **RR-1** du dossier Gate 07A et clôt **OBS-02/03**.

Objectif : prouver en conditions réelles sur staging que chaque composant critique lève une alerte
**FIRING**, la **notifie** (bout-en-bout) et la **résorbe**. Overlay `monitoring` combiné à la stack
staging (`docker compose -f docker-compose.staging.yml -f docker-compose.monitoring.yml --profile monitoring`).

**Cibles Prometheus scrapées :** 4/4 `up` (`loyertracker-api`, `blackbox-postgres`, `blackbox-keycloak`, `pushgateway`).

| Composant critique | Alerte | FIRING (≥ seuil `for`) | Notifié | Résorbé |
|---|---|---|---|---|
| API | `ApiDown` | ✅ (stop `api`, ~3 min) | (chaîne prouvée) | ✅ (start `api`) |
| Base de données | `PostgresProbeDown` | ✅ (stop `postgres`, 200 s) | (chaîne prouvée) | ✅ (start `postgres`) |
| Identité (Keycloak) | `KeycloakProbeDown` | ✅ (stop `keycloak`, 200 s) | (chaîne prouvée) | ✅ (start `keycloak`) |
| Sauvegarde | `BackupHeartbeatMissing` | ✅ (heartbeat absent > 30 min) | ✅ **payload Alertmanager capturé** | ✅ (run `backup-postgres.sh` → heartbeat poussé au Pushgateway) |

**Notification bout-en-bout prouvée** : la chaîne `Prometheus → règle → Alertmanager (v0.28.1) →
webhook via url_file → récepteur` a livré la notification `BackupHeartbeatMissing`
(`receiver:"default"`, `status:"firing"`, `component:"backup"`, `severity:"critical"`, annotations
incluses). Secret de notification hors dépôt (`ALERTMANAGER_WEBHOOK_URL` dans `.env`, lu via
`url_file`) ; endpoints monitoring non publiés (Pushgateway loopback uniquement).

**Non-régression** : après remise en service, `infra/smoke/smoke-stack.sh` (BASE `localhost:18443`)
→ **46 PASS / 0 FAIL** ; 4/4 services applicatifs `healthy` ; échafaudage `directAccessGrants`
révoqué automatiquement.

**Gate Staging enrichi (v4.0 + observabilité) — verdict GO :**

| Critère enrichi | Verdict |
|---|---|
| Logs disponibles | ✅ JSON ECS (api + Nginx), §3/runbook §7 |
| Monitoring actif | ✅ Prometheus scrape 4/4 cibles `up` (live) |
| Alertes critiques définies & exercées | ✅ 10 règles ; 4/4 composants critiques FIRING→resolved + notification livrée |

**Réserve résiduelle :** **RR-2** (renseigner la traçabilité production des release notes §5) — portée
au **go-live réel** (Gate 09/10), hors périmètre staging.

**Récepteur jetable :** `am-sink` supprimé après collecte des preuves (`docker rm -f am-sink`,
ligne `ALERTMANAGER_WEBHOOK_URL` de test retirée de `.env`). Overlay `monitoring` laissé actif
(supervision continue du staging).

## 11. Conformité `STG-ISOL-01` (CGPA v5.4) — isolation de l'environnement Staging mutualisé

> Gate ajouté par CGPA v5.4. Décision formelle : `docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md`.
> Checklist : `docs/cgpa/checklists/stg-isol-01-checklist.md`. Workflow :
> `docs/cgpa/workflows/staging-isolation-workflow.md`. ADR : `docs/cgpa/05-architecture-conception/adr/ADR-STG-001-isolation-staging-partage.md`.

### Inventaire des ressources mutualisées (D-STG-04)

| Ressource | Nature | Propriétaire / portée | Conditions d'usage |
|---|---|---|---|
| Hôte `ai-test-server` (IP privée `172.31.11.102`) | Hôte EC2 partagé | Exploitant LoyerTracker/labo (cf. `SERVER_CONFIG.md`, hors dépôt) | Hébergement multi-projets ; chaque projet isole sa propre stack Compose (namespace, réseau, volume) |
| Reverse proxy nginx-proxy-manager | Service mutualisé occupant les ports 80/443 de l'hôte | Exploitant (commun à tous les projets de l'hôte) | Publication par nom DNS et Proxy Host dédié par projet (Proxy Host #18 pour LoyerTracker) ; aucune modification de la configuration des autres projets lors d'un déploiement LoyerTracker |
| Registre d'images GHCR (`ghcr.io/jptshilombo`) | Registre partagé au niveau du compte | Exploitant | Espace de noms par projet (`loyertracker-api`, `loyertracker-web`) ; pas de partage d'image avec un autre projet |
| Autres projets connus sur l'hôte (« loyerpro », outils labo — SG `innovtech-ai-lab-sg`) | Stacks tierces, hors périmètre LoyerTracker | Hors périmètre de gouvernance LoyerTracker | Aucune ressource Docker (réseau/volume/conteneur) partagée avec LoyerTracker constatée à ce jour |

Ressources **dédiées** au projet (non partagées) : réseau `loyertracker-staging_loyertracker-net`,
volume `loyertracker-staging_postgres-data`, `.env` propre à l'hôte, conteneurs préfixés
`loyertracker-staging-*` — cf. `docker-compose.staging.yml` (`name: loyertracker-staging`).

### Résultat du contrôle

**PASS** (2026-06-24) — fondé sur l'analyse de configuration (namespace Compose explicite,
réseau/volume dédiés, ports internes non publiés, absence de commande Docker globale dans le
runbook et la CI) et sur l'historique des redéploiements §8 (6 déploiements entre le 2026-06-14 et
le 2026-06-24, sans incident rapporté sur un autre projet de l'hôte).

**Réserve RSV-STG-01 — LEVÉE le 2026-06-25** : confirmation *live* réalisée lors du déploiement
`sha-5bf187af` (lot CORS + Sprint 3) — avant (session précédente) = 8 conteneurs
`loyertracker-staging-*` exclusivement ; après = 8 conteneurs `loyertracker-staging-*` — aucun
autre projet de l'hôte affecté. Isolation prouvée en présence de la stack LoyerTracker active.
