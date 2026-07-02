# État Production — LoyerTracker

> Rapport de déploiement **production réel** et de validation go-live (Gate 10 — Mise en production).
> Document vivant : à mettre à jour à chaque déploiement production. Miroir de `docs/staging-state.md`.
> Cadre : CGPA v5.2 — lève les réserves **RR-2** / **RG-09-1** / **RG-09-2** (Gate 09).



## 0G. Déploiement Production `1.6.0` — 2026-07-02

| Contrôle | Résultat |
|---|---|
| Release | `1.6.0` — Sprint 7 EP-10 US-90 (Patrimoine enrichi) + Sprint 8 EP-11 US-92/93 (Money/Devise) |
| Tag déployé | **`sha-2da27182`** (GHCR, commit `2da2718` — PR #143 code Sprint 8 + #144 docs merge Sprint 8, Sprint 7 déjà fusionné avant via PR #131) |
| Tag précédent / rollback | `sha-08b366fa` (`1.5.0`) — applicatif seul, **avec réserve** : risqué si une inscription a eu lieu post-déploiement (contrainte `NOT NULL` V19 vs ancien `InscriptionService`), cf. Gate Production `1.6.0` |
| Backup pré-déploiement | `loyertracker-20260702-170536.dump` (312 Kio), SHA-256 `e95064d4…`, globals SHA-256 `267cae88…`, `pg_restore --list` 730 entrées OK |
| Déploiement | `api` + `nginx` recréés ciblés — **aucun écart** (contrairement à `1.5.0`, `postgres`/`keycloak` restés inchangés) |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **(healthy)**, zéro restart |
| Flyway | V18→**V19** — 7 colonnes optionnelles sur `patrimoine` + `adresse SET NOT NULL` (backfill générique), appliquée sans erreur |
| Adresse réelle patrimoine | `d753e6d6-…` mise à jour par SQL direct (écart qualifié PO, faute de jeton bailleur réel) : `5172, Avenue Kasamvu`, Kasa-Vubu, Bandalungwa, Kinshasa, RDC |
| Smoke Production | **59 PASS / 0 FAIL** (2026-07-02 ~16:45 UTC). Nettoyage transactionnel complet (bailleur2-smoke + gest-smoke supprimés DB+KC, bailleur-test réactivé puis redésactivé, aucun orphelin détecté) |
| Vérification comportementale US-90 | `POST /api/patrimoines` avec `adresse` obligatoire confirmé (201) |
| Vérification comportementale US-92/93 | Honoraire EUR recalculé correctement (VO `Money`) ; **USD/CDF non vérifié visuellement en Production** (aucun bail réel dans ces devises) — réserve RSV-S7-8-01 maintenue, non bloquante |
| Observabilité | 5/5 cibles Prometheus up ; Alertmanager 0 alerte active |
| Réserves levées | **RP-160-01** (backup), **RP-160-02** (recomptage `adresse IS NULL`), **RP-160-05** (adresse réelle appliquée) |
| `.env` persisté | `LOYERTRACKER_TAG=sha-2da27182`, backup `.env.bak-pre-1.6.0` (permissions 600) |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint le 2026-07-02 16:50 UTC |

Rapports : `docs/cgpa/09-production/preflight-backup-v1.6.0-report.md`,
`docs/cgpa/09-production/deploiement-technique-v1.6.0-report.md`,
`docs/cgpa/09-production/validation-finale-v1.6.0-report.md`.

Réserves ouvertes après ce déploiement : **RSV-S7-8-01** (vérification visuelle USD/CDF, non
bloquante), **RP-160-03** (`CHANGELOG.md` à scinder `[1.5.0]`/`[1.6.0]`, action Release Manager
avant clôture).

## 0F. Déploiement Production `1.5.0` — 2026-07-01

| Contrôle | Résultat |
|---|---|
| Release | `1.5.0` |
| Tag déployé | **`sha-08b366fa`** (GHCR, merge commit PR #123 — Sprint 6 RGPD US-70 + CSP US-72) |
| Tag précédent / rollback | `sha-98afa99a` (`1.4.0`) — applicatif seul, sans `pg_restore` |
| Backup pré-déploiement | `loyertracker-20260701-102523.dump` (316 Kio), SHA-256 `bd003932…`, globals SHA-256 `22dff9ab…`, `pg_restore --list` 730 entrées OK |
| Déploiement | `api` + `nginx` recréés ; **écart constaté** : `postgres`/`keycloak` également recréés par Docker Compose (non ciblés), sans cause de configuration identifiée — volume `postgres-data` préservé (créé 2026-06-20), données vérifiées intactes, aucun impact (détail : `deploiement-technique-v1.5.0-report.md` §1.1) |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **(healthy)**, zéro restart |
| Flyway | V1→**V18** — **aucune nouvelle migration** (inchangé depuis `1.4.0`) |
| Smoke Production | **59 PASS / 0 FAIL** (2026-07-01 ~10:47 UTC, dont 12 assertions RGPD §9). `bailleur-test@test.local` réactivé temporairement, redésactivé après (pattern identique à `1.4.0`). Nettoyage transactionnel complet (2 bailleur2-smoke dont 1 orphelin, 2 gest-smoke dont 1 orphelin). |
| Vérification comportementale US-70 | `GET /api/bailleurs/export` (200, scopé) et `DELETE .../locataire` (403 gestionnaire, 204 bailleur, anonymisation + `audit_log EFFACEMENT_LOCATAIRE`) confirmés en Production |
| Observabilité | 5/5 cibles Prometheus up ; Alertmanager 0 alerte active |
| Réserves levées | **RP-150-01** levée (backup vérifié préflight 2026-07-01) |
| `.env` persisté | `LOYERTRACKER_TAG=sha-08b366fa`, SHA-256 `.env` = `87b102b1…` |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint le 2026-07-01 11:02 UTC |

Rapports : `docs/cgpa/09-production/preflight-backup-v1.5.0-report.md`,
`docs/cgpa/09-production/deploiement-technique-v1.5.0-report.md`,
`docs/cgpa/09-production/validation-finale-v1.5.0-report.md`.

## 0E. Déploiement Production `1.4.0` — 2026-06-30

| Contrôle | Résultat |
|---|---|
| Release | `1.4.0` |
| Tag déployé | **`sha-98afa99a`** (GHCR, merge commit PR #115) |
| Tag précédent / rollback | `sha-a42d860d` (`1.3.0`) — applicatif seul + pg_restore V15 si nécessaire |
| Backup pré-déploiement | `loyertracker-20260630-160619.dump` (312 Kio), SHA-256 `60b1fd74…`, globals SHA-256 `ee660fc5…`, `pg_restore --list` 730 entrées OK |
| Déploiement | `api` + `nginx` recréés ; `postgres`, `keycloak`, monitoring inchangés |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **(healthy)**, zéro restart |
| Flyway | V1→**V18**, **3 nouvelles migrations** : V16 (bien.statut LOUE + patrimoine.adresse), V17 (bail.devise EUR/USD/CDF), V18 (StatutPaiement A_VENIR + contrainte CHECK + generer_echeances_loyers()) |
| Smoke Production | **47 PASS / 0 FAIL** (2026-06-30 ~15:15 UTC). Note : `bailleur-test@test.local` était désactivé KC — réactivé avant le smoke, redésactivé après (pattern identique à `1.2.1`). |
| Observabilité | 5/5 cibles Prometheus up ; Alertmanager 0 alerte active |
| Réserves levées | **RP-140-01** levée (backup vérifié préflight 2026-06-30 16:06 UTC) |
| `.env` persisté | `LOYERTRACKER_TAG=sha-98afa99a`, SHA-256 `.env` = `1e3f9a7d…` |
| Hypercare | T0 PASS (2026-06-30 15:12 UTC), T+12 anticipé PASS (2026-06-30 15:25 UTC), T+24 PASS (2026-07-01 06:38 UTC) |
| RSV-T24-01 | Cron backup inactif depuis Jun 25 — daemon actif, backup manuel 2026-07-01 effectué. Non bloquant. |
| Backup T+24 | `loyertracker-20260701-074122.dump` (316K, SHA-256 `0692dbf2…`, 730 entrées). Heartbeat restauré. |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint le 2026-06-30 ~15:12 UTC ; **RELEASE CLÔTURÉE le 2026-07-01** |

Rapports : `docs/cgpa/09-production/preflight-backup-v1.4.0-report.md` (backup 2026-06-30), Gate Production Sprint 5 (`docs/project-state.md`).

## 0D. Déploiement Production `1.3.0` — 2026-06-29

| Contrôle | Résultat |
|---|---|
| Release | `1.3.0` |
| Tag déployé | **`sha-a42d860d`** (GHCR, commit applicatif `a42d860d5a10b80b85d5a94d79c3680ef06bacdc`) |
| Tag précédent / rollback | `sha-47172297` (`1.2.1`) — applicatif seul, sans pg_restore |
| Backup pré-déploiement | `loyertracker-20260629-140719.dump` (314 Kio), SHA-256 `524ee4bb…`, `pg_restore --list` 730 entrées OK |
| Déploiement | `api` + `nginx` recréés ; Postgres, Keycloak, monitoring inchangés |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **healthy**, zéro restart |
| Flyway | V1→V15, **15 migrations success** — inchangé (`1.3.0` = UI + 1 endpoint backend, aucune migration) |
| Smoke Production | **47 PASS / 0 FAIL** (2026-06-29 15:22 UTC) |
| Nettoyage | 2 bailleur2-smoke + 2 gest-smoke supprimés (DB + KC), bailleur-test désactivé KC ; données jptshilombo intactes |
| Observabilité | 5/5 cibles Prometheus up ; alerte `BackupHeartbeatMissing` résolue (heartbeat manuellement poussé, cron 2h15 UTC assurera la suite) |
| Réserves levées | **RP-130-01** levée (backup vérifié préflight) |
| Hypercare | T0/T+12/T+24 anticipés (2026-06-29) — tous PASS ; CDO GO **RELEASE CLÔTURÉE** |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint le 2026-06-29 à 14:31 UTC ; **RELEASE CLÔTURÉE** |

Rapports : `docs/cgpa/09-production/preflight-backup-v1.3.0-report.md`, `docs/cgpa/09-production/deploiement-technique-v1.3.0-report.md`, `docs/cgpa/09-production/validation-finale-v1.3.0-report.md`, `docs/cgpa/09-production/cloture-release-v1.3.0.md`.

## 0C. Déploiement Production `1.2.1` — 2026-06-27

| Contrôle | Résultat |
|---|---|
| Release | `1.2.1` |
| Tag déployé | **`sha-47172297`** (GHCR, commit applicatif `c1e9c735e39c0375b907be9da3302e67f5cb10d4`) |
| Tag précédent / rollback | `sha-5bf187af` (`1.2.0`) — applicatif seul, sans pg_restore |
| Backup pré-déploiement | `loyertracker-20260627-085033.dump` (311 Kio), SHA-256 `511ef7a7…`, `pg_restore --list` 730 entrées OK |
| Déploiement | `nginx` + `api` recréés (`api` : dépendance + --pull always ; code Java identique à `1.2.0`) |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **healthy**, zéro restart |
| Flyway | V1→V15, **15 migrations success** — inchangé (`1.2.1` = correctif frontend pur) |
| Smoke Production | **47 PASS / 0 FAIL** ; correctif `c1e9c73` confirmé (POST inscription 409 → biens chargés via `finalize`) |
| Note smoke | `bailleur-test@test.local` était désactivé — réactivé manuellement avant le 2e run (pré-existant `1.2.0`) |
| Observabilité | 5/5 cibles Prometheus up ; alerte `BackupHeartbeatMissing` pré-existante (Pushgateway volatil post-reboot) |
| Réserves levées | **RP-120-03** levée (`c1e9c73` en Production) ; RP-121-01 levée (backup vérifié) |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint le 2026-06-27 à 09:08 UTC ; **RELEASE CLÔTURÉE** |

Rapports : `docs/cgpa/09-production/validation-finale-v1.2.1-report.md`, `docs/cgpa/09-production/cloture-release-v1.2.1.md`.

## 0B. Déploiement Production `1.2.0` — 2026-06-26

| Contrôle | Résultat |
|---|---|
| Release | `1.2.0` |
| Tag déployé | **`sha-5bf187af`** (GHCR, commit `5bf187af79218377b2f7db7800961725088d31a5`) |
| Tag précédent / rollback | `sha-0adc4941` (`1.1.1`) |
| Backup pré-déploiement | `loyertracker-20260626-182030.dump` (308 Kio), SHA-256 `4ed4e837…`, `pg_restore --list` OK |
| Déploiement | Pull puis recréation ciblée `api` + `nginx` ; tag persisté dans `.env` avant `docker compose up` |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **healthy**, zéro restart |
| **Flyway V15** | **V1→V15, 15 migrations success** — `affectations_exceptions` appliquée |
| Smoke Production | **47 PASS / 0 FAIL** ; V15, honoraires 72.00, isolation cross-tenant, RS-04 validés |
| Nettoyage | 2 runs smoke supprimés (1782495782 + orphelin 1782227595), 4 users Keycloak éphémères supprimés, bailleur-test désactivé |
| Observabilité | 5/5 cibles Prometheus up, Alertmanager 0 alerte |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint le 2026-06-26 à 17:49 UTC |

Rapport final : `docs/cgpa/09-production/validation-finale-v1.2.0-report.md`.

## 0A. Déploiement Production Hotfix `1.1.1` — 2026-06-24

| Contrôle | Résultat |
|---|---|
| Release | `1.1.1` |
| Tag déployé | **`sha-0adc4941`** (GHCR, commit canonique `0adc4941f854304a3f7412b04294615b05403707`) |
| Tag précédent / rollback | `sha-05424aa3` (`1.1.0`), digests immuables vérifiés |
| Backup pré-déploiement | `loyertracker-20260624-140441.dump`, checksum tracé, `pg_restore --list` OK |
| Déploiement | pull puis recréation ciblée d’`api` et `nginx` uniquement ; tag persisté dans `.env` après validation |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **healthy**, zéro restart |
| Flyway | V1→V14, **14 migrations success**, aucune nouvelle migration |
| Smoke Production | **47 PASS / 0 FAIL** ; patrimoine/bien et isolation cross-tenant validés |
| Nettoyage | données du run supprimées, compteurs revenus à la baseline, comptes Keycloak éphémères supprimés |
| Observabilité | cinq cibles Prometheus up, aucune alerte |
| Décision CGPA | CDO **GO** — `PRODUCTION_DEPLOYED` atteint |

Rapport final : `docs/cgpa/09-production/validation-finale-v1.1.1-report.md`.

## 0. Déploiement Production `1.1.0` — 2026-06-23

| Contrôle | Résultat |
|---|---|
| Release | `1.1.0` |
| Tag déployé | **`sha-05424aa3`** (GHCR, push `main` post-PR #77) |
| Tag précédent | `sha-73359c5c` (`1.0.0`) |
| Backup pré-déploiement | `loyertracker-20260623-150659.dump`, `pg_restore --list` OK |
| Déploiement | `.env` mis à jour (`LOYERTRACKER_TAG=sha-05424aa3`), `docker compose pull api nginx`, `up -d api nginx` |
| Services post-déploiement | `api`, `nginx`, `postgres`, `keycloak` **healthy** |
| Flyway | V1→V14, **14 migrations success** |
| Smoke Production | **47 PASS / 0 FAIL** via `infra/smoke/smoke-stack.sh` |
| État Keycloak post-smoke | `bailleur-test@test.local` désactivé ; `loyertracker-spa.directAccessGrantsEnabled=false` |
| Décision CGPA | `PRODUCTION_DEPLOYED` atteint |

Le tag initialement préparé dans le Gate Production (`sha-1d6db31`) n'était pas publié sur GHCR. Le tag immuable réellement publié par le workflow `Packaging Docker` du dernier push `main` est `sha-05424aa3`; il a donc été retenu comme artefact de déploiement réel.

## 1. Contexte

| Champ | Valeur |
|---|---|
| Date go-live | 2026-06-20 |
| Release | `1.1.1` (SemVer) — release courante ; `1.1.0` et `1.0.0` conservées en historique |
| Tag déployé | **`sha-0adc4941`** (immuable GHCR, release `1.1.1`) ; précédent `sha-05424aa3` (`1.1.0`) |
| Opérateur | Claude Code (CGPA Chief Delivery Officer), sous PO `jptshilombo@gmail.com` |
| Hôte | **Production dédié** `loyertracker-prod-server` — EC2 `t3.medium` (2 vCPU / 4 GiB / 40 GiB gp3 chiffré), Ubuntu 24.04, `eu-central-1a`, instance `i-032524e6a47b72e05` |
| Réseau | EIP `18.158.70.88` ; domaine `loyertracker.loyerpro.org` (Route53) ; SG `loyertracker-prod-sg` : `80/443` publics, **`22` restreint** (`52.29.80.119/32` admin + `172.31.30.45/32` dev privé, depuis le 2026-06-20) ; UFW + fail2ban actifs |
| Isolation | Hôte **distinct** de staging (`ai-test-server`, ENV-01 strict — Staging ≠ Production), stack Compose `loyertracker` dédiée |

La stack est déployée de façon **isolée** sur un hôte qui lui est propre (aucune mutualisation avec staging
ou d'autres charges), conformément au modèle de promotion ENV-01 (Production distincte de Staging).

## 2. Configuration du déploiement

| Paramètre | Valeur |
|---|---|
| Fichiers Compose | `docker-compose.yml` + `docker-compose.prod.yml` (+ `docker-compose.monitoring.yml` pour l'overlay) |
| Source des images | GHCR (`ghcr.io/jptshilombo`), tag immuable `sha-0adc4941` — **jamais `latest`**, **aucun build local** |
| Keycloak | mode **production** `start` (sans `--optimized`) + `--import-realm --http-relative-path=/auth` ; `KC_HOSTNAME=loyertracker.loyerpro.org` |
| Ports hôte (web) | `WEB_HTTP_PORT=18080` → 8080, `WEB_HTTPS_PORT=18443` → 8443 (via `ports: !override`) |
| Ports internes | `api`, `keycloak`, `postgres` **non publiés** ; monitoring `9090/9093/9115` internes, Pushgateway `127.0.0.1:9091` (loopback) |
| TLS public | **Nginx système hôte (1.24) + certbot/Let's Encrypt** termine le TLS sur `:443`, reverse-proxy vers le conteneur web `18443` (`proxy_ssl_verify off`) ; cert valide jusqu'au **2026-09-17** |
| TLS interne | certificat auto-signé (`infra/nginx/certs/localhost.pem`), monté en RO ; lecture par le conteneur web non-root (clé en 644 derrière le proxy TLS hôte) |
| Issuer Keycloak | `https://loyertracker.loyerpro.org/auth/realms/loyertracker` — **canonique, sans port** |
| `.env` | secrets **neufs** générés sur l'hôte (distincts de staging), perms `600`, jamais versionnés ; `ALERTMANAGER_WEBHOOK_URL` = webhook Discord (hors dépôt) |

## 3. Vérifications de plateforme

| Contrôle | Attendu | Résultat |
|---|---|---|
| Tous les services `healthy` | 4/4 | ✅ api, keycloak, postgres, nginx |
| `keycloak-init` (one-shot) | exit 0 | ✅ exit 0 (secret Admin API `loyertracker-admin` injecté) |
| Flyway | V1→V14 | ✅ **14 migrations `success`** |
| Pool applicatif | sous `loyertracker_api` (NOSUPERUSER NOBYPASSRLS) | ✅ RLS `FORCE` réellement exercée |
| Issuer (découverte OIDC public) | `https://loyertracker.loyerpro.org/auth/realms/loyertracker` | ✅ portless |
| `/healthz` (HTTPS public) | 200 (cert Let's Encrypt valide) | ✅ |
| `/api/actuator/prometheus` **public** | 404 / non routé | ✅ (Nginx ne route pas l'endpoint publiquement) |

## 4. Smoke test runtime contre production

Commande (sur l'hôte prod, **avant** ouverture publique réelle, sur tenant jetable) :

```
sudo env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

**Résultat : 46 PASS / 0 FAIL.**

Couverture prouvée en conditions réelles (JWT réels Keycloak via Nginx) :

- Sanity : 10 migrations Flyway, pool API sous `loyertracker_api`, Actuator `UP` via Nginx TLS.
- Parcours S01→S04 complet : inscription, bien, bail, invitation→acceptation (Admin API réelle),
  affectation 8 %, échéances (SECURITY DEFINER), pointage, **honoraire 72,00 €** sur la période pointée,
  alertes dont **PREAVIS à J+75**, audit.
- Matrice de rôles : `403` honoraire/audit côté gestionnaire, scoping des biens affectés.
- **Isolation cross-tenant live** : 2ᵉ bailleur → 0 fuite (biens, paiements, honoraires, alertes).
- Garde-fous : `401` sans jeton, **ports internes non publiés** sur l'hôte.

Échafaudage `directAccessGrants` activé puis **révoqué automatiquement** (trap EXIT) ; aucune modification
versionnée du realm.

## 5. Durcissement go-live (état de lancement propre)

> Le smoke crée des données (parcours S01→S04). Aucun vrai utilisateur n'existant encore, l'état de
> lancement a été ramené à une base **vierge** (procédure de déploiement documentée), plutôt qu'un
> nettoyage sélectif.

| Action | Résultat |
|---|---|
| Reset propre (`down -v` + `up -d`) | Base **vierge** : Flyway V1→V10 rejoué, **0 ligne** dans toutes les tables métier (`bien`, `bail`, `bailleur`, `honoraire`, `alerte`, `invitation`, `gestionnaire`), realm ré-importé, secret Admin API ré-injecté |
| Compte de test | `bailleur-test@test.local` **désactivé** (`enabled=false`) — embarqué `enabled:true` dans le realm, désactivé après `keycloak-init` (persiste à la ré-exécution idempotente du bootstrap) |
| Service-account API | `loyertracker-admin` (client_credentials) opérationnel — **non impacté** par la désactivation du compte de test |
| Monitoring | non exposé publiquement (cf. §6) ; pas de seed de données de test |

> **Note (dette mineure d'exploitation)** : le realm `realm-loyertracker.json` embarque le compte de test
> et `keycloak-init` couple la pose du mot de passe de test à l'injection du secret Admin API (requise). Un
> `down -v` futur réactive donc le compte (ré-import) → re-désactiver après recréation. Un realm de
> production sans compte de test serait l'état « excellent » (suivi exploitation, non bloquant).

## 6. Observabilité & alerting (overlay monitoring)

Overlay combiné à la stack prod :

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.monitoring.yml --profile monitoring up -d
```

| Contrôle | Résultat |
|---|---|
| Cibles Prometheus | **5/5 `up`** : `loyertracker-api`, `blackbox-postgres`, `blackbox-keycloak`, `prometheus`, `pushgateway` |
| Receiver Alertmanager | `discord_configs` (natif Discord), `webhook_url_file: /tmp/webhook_url`, `send_resolved: true` |
| Secret webhook | présent non-vide dans le conteneur, **hors dépôt** (`ALERTMANAGER_WEBHOOK_URL` dans `.env`, écrit dans `/tmp/webhook_url` au démarrage) |
| Exposition publique | aucune : `9090/9093/9115` internes au réseau Docker, Pushgateway `127.0.0.1:9091` (loopback). `/prometheus` public = **fallback SPA Angular** (200 = `index.html`, pas Prometheus) |

## 7. Validation de l'alerting (incident simulé)

Incident synthétique injecté via l'API Alertmanager (`amtool alert add`) — **sans couper de composant réel**
(approche non destructive sur système live) :

| Phase | Livraison Discord (preuve serveur) | Échecs |
|---|---|---|
| **FIRING** (`Gate10NotificationDrill`, severity critical) | ✅ `alertmanager_notifications_total{integration="discord"}=1` | `failed_total{discord,*}=0` |
| **RESOLVED** (résolution de la même alerte, flush à `group_interval`) | ✅ `..._total=2` | `failed_total=0` |

Discord ayant renvoyé un code 2xx aux deux notifications (sinon `failed_total` aurait incrémenté), la
livraison est prouvée côté serveur. **Réception humaine des 2 messages confirmée par le PO** dans le salon
Discord → **RG-09-2 close côté joignabilité humaine**.

## 8. Sauvegarde

| Contrôle | Résultat |
|---|---|
| Backup manuel | `pg_dump -Fc` (≈ 292 K) + `pg_dumpall --globals-only` (rôles cluster) ; perms dir 700 / fichiers 600 |
| Intégrité | **`pg_restore --list`** OK (dump lisible) |
| Cron prod installé | `15 2 * * *` avec `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml` (`infra/backup/install-cron.sh`) |
| Heartbeat (OBS-03) | poussé au Pushgateway (`loyertracker_backup_last_success_epoch`) puis **scrapé par Prometheus** ; `BackupHeartbeatMissing` vidé (jamais fired) ; **aucune alerte `pending`/`firing`** |

## 9. Procédure de redéploiement / rollback

- **Déploiement** : sur l'hôte, `.env` en place (ports `18080/18443`, secrets prod), puis
  `LOYERTRACKER_TAG=sha-<8> docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.
- **Rollback (release)** : redéployer le `LOYERTRACKER_TAG` **précédent** (tags immuables) ; procédure
  éprouvée en staging (`docs/staging-state.md` §7) et runbook §3. `1.0.0` étant la **première** release
  production, il n'existe pas encore de tag antérieur vers lequel revenir — un drill de rollback significatif
  sera réalisé à la prochaine release.
- **Rollback (données)** : restauration via `infra/backup/restore-postgres.sh` (drill prouvé, RPO 24 h /
  RTO < 1 h, PR #26) ; backup go-live vérifié comme filet de récupération.

## 10. Processus de support / astreinte minimal (RG-09-2)

| Élément | Valeur |
|---|---|
| Canal d'astreinte | **Discord** (salon dédié) — notifications Alertmanager `discord_configs`, prouvées bout-en-bout (§7) |
| Point de contact | PO `jptshilombo@gmail.com` (opérateur unique, contexte PME) |
| Escalade | mono-opérateur : l'alerte Discord déclenche l'intervention directe ; pas de niveau 2 formel (PME) |
| Runbook d'incident | `docs/cgpa/07-devsecops/runbook-exploitation.md` (diagnostic, redéploiement, rollback, restauration) |
| Surveillance capacity (RG-09-1) | Prometheus : latence p99 (`http.server.requests`), pool Hikari (`hikaricp_connections_pending`), sondes blackbox ; baseline `t3.medium` (PME), revue en exploitation |

## 11. Écarts trouvés au go-live et corrigés

L'overlay `docker-compose.prod.yml` n'avait **jamais été déployé** : le go-live réel a révélé trois écarts
(analogues aux 4 écarts du déploiement staging réel, PR #29), tous versionnés et re-vérifiés live :

1. **Ports nginx 80/443 au lieu de 18080/18443** — Compose **concatène** les listes de ports ; le compose de
   base fige `80/443`. Corrigé par `ports: !override` (PR #60, merge `f9fa67a`). → publié `18080/18443`.
2. **Keycloak crash-loop (H2 au lieu de Postgres)** — `start --optimized` suppose une image pré-`kc.sh build`
   (l'image GHCR officielle est buildée sur H2) → KC ignorait `KC_DB`/`KC_HEALTH_ENABLED`/`KC_HTTP_RELATIVE_PATH`.
   Corrigé : `start` sans `--optimized` + `KC_HOSTNAME` public (PR #61, merge `e87f8f5`). → 4/4 healthy.
3. **Receiver Alertmanager incompatible Discord** — un webhook Discord brut rejette le JSON natif
   d'Alertmanager. Bascule sur le receiver natif `discord_configs` (secret inchangé via `webhook_url_file`)
   (PR #62, merge `d576f90`). → FIRING/RESOLVED livrés (§7).

Plus un ajustement hôte non versionné (spécifique machine) : permissions du certificat interne
(`localhost-key.pem` → 644) pour lecture par le conteneur web non-root, derrière le proxy TLS hôte
(`proxy_ssl_verify off`) + `docker compose up -d --force-recreate nginx`.

## 12. Gate 10 — Mise en production initiale : **GO le 2026-06-20** ✅

Décision : `docs/cgpa/10-mise-en-production/gate-10-decision.md` — 12/12 critères ✅, réserves **RR-2 /
RG-09-1 / RG-09-2 levées**. Production **LIVE** sur `https://loyertracker.loyerpro.org`.

**SG SSH restreint le 2026-06-20** : port 22 limité à `52.29.80.119/32` (admin, host Claude Code) +
`172.31.30.45/32` (serveur de dev `loyerpro-ci-server`, **IP privée** même VPC `vpc-01a99b76679b8e92e`) ;
`0.0.0.0/0` retiré. Le serveur de dev se connecte à la prod par son **IP privée** `172.31.22.90` (pas l'EIP
publique : le hairpin sourcerait depuis l'IP publique `3.77.128.72`, non autorisée).

**Règle réseau formalisée le 2026-06-24** : ce principe (IP privée prioritaire entre serveurs du
périmètre dans le même VPC) est désormais documenté comme politique générale — dev/CI, staging/test,
SonarQube — dans `docs/cgpa/environment-promotion-model.md` (Accès SSH inter-serveurs) et
`docs/cgpa/07-devsecops/runbook-exploitation.md` §0.1. Production reste conforme sans changement.

**Suite (exploitation, hors gate)** : revue de capacité en exploitation ; drill de rollback à la prochaine
release ; realm de production sans compte de test (état « excellent », non bloquant).


## 13A. Production Hotfix `1.1.1` — `PRODUCTION_DEPLOYED` le 2026-06-24 ; hypercare clôturée le 2026-06-25 ✅

| Contrôle | Résultat |
|---|---|
| API/Web | `sha-0adc4941`, digests conformes |
| Services recréés | `api`, `nginx` uniquement |
| PostgreSQL/Keycloak/monitoring | Inchangés |
| Flyway | V1→V14, aucune migration |
| Santé | API/Web healthy, cinq cibles Prometheus up, aucune alerte |
| Smoke métier | **47 PASS / 0 FAIL** |
| Statut | **`PRODUCTION_DEPLOYED`** |
| Hypercare 24 h | T0 (2026-06-24 16:11:35 UTC) PASS sous surveillance ; T+12 (2026-06-25 06:21:54 UTC, fenêtre étendue CDO) PASS sous surveillance ; T+24 (2026-06-25 16:48:05 UTC) PASS sous surveillance → **CDO : GO — RELEASE CLÔTURÉE le 2026-06-25** |

Rapports : `docs/cgpa/09-production/deploiement-technique-v1.1.1-report.md`, `docs/cgpa/09-production/validation-finale-v1.1.1-report.md`, `docs/cgpa/09-production/cloture-release-v1.1.1.md`. Tag `sha-0adc4941` persisté dans `.env` ; rollback `sha-05424aa3` disponible. Réserves maintenues après clôture : `RSV-STG-01`, dette CORS Compose.

## 13. Gate Production v5.3 — Release `1.1.0` : **PRODUCTION_DEPLOYED le 2026-06-23** ✅

Décision : `docs/cgpa/09-production/gate-production-v1.1.0-decision.md` — Gate Production GO sous réserve acceptée, puis déploiement réel validé. Production **LIVE** sur `https://loyertracker.loyerpro.org` avec le tag `sha-05424aa3`.

Résumé d’exécution : backup pré-déploiement vérifié (`loyertracker-20260623-150659.dump`), rollback applicatif préparé vers `sha-73359c5c`, services applicatifs healthy, Flyway V1→V14, smoke Production 47 PASS / 0 FAIL, compte de smoke redésactivé et `directAccessGrants=false`.
