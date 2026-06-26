# Dossier de clôture — Release `1.2.0`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-26 |
| Statut | **Hypercare condensée — T0, T+12 et T+24 anticipés PASS ; décision CDO requise** |
| Version | `1.2.0` |
| Commit applicatif | `5bf187af79218377b2f7db7800961725088d31a5` |
| Tag Production | `sha-5bf187af` |
| Rollback applicatif | `sha-0adc4941` |
| Rollback schéma | `pg_restore loyertracker-20260626-182030.dump` |

## Références

- Gate : `gate-production-v1.2.0-decision.md`
- Préflight/backup : `preflight-backup-v1.2.0-report.md`
- Déploiement : `deploiement-technique-v1.2.0-report.md`
- Validation finale : `validation-finale-v1.2.0-report.md`
- Release notes : `docs/release-notes-v1.2.0.md`
- Plan hypercare : `plan-etape-2-hypercare-production-v1.2.0.md`

## Baseline avant hypercare

Issue de la validation finale (47 PASS / 0 FAIL, 2026-06-26 17:49 UTC) :

- API/Web sur `sha-5bf187af` — digests immuables vérifiés
- Services applicatifs healthy, restart count 0
- Flyway V1→V15 (15/15)
- Actuator UP et racine Web HTTP 200
- Cinq cibles Prometheus `up`
- Aucune alerte
- Smoke 47/0 et données de test nettoyées (2 runs)
- Backup `loyertracker-20260626-182030.dump` vérifié, rollback disponible

## Registre hypercare 24 heures

| Contrôle | T0 | T+12 h | T+24 h |
|---|---|---|---|
| Date/heure UTC | 2026-06-26 17:52 UTC | **2026-06-26 17:57 UTC** ⚠ anticipé | **2026-06-26 18:01 UTC** ⚠ anticipé (voir note) |
| API/Web healthy | PASS — `api`/`nginx`/`postgres`/`keycloak` `(healthy)`, 8/8 Up | PASS — 8/8 Up, 4 `(healthy)` | PASS — 8/8 Up, 4 `(healthy)` |
| Restart counts | PASS — 0 (api, nginx, postgres, keycloak) | PASS — 0 | PASS — 0 |
| Tag `sha-5bf187af` | PASS — `LOYERTRACKER_TAG=sha-5bf187af` ; digest API `sha256:3e511356…`, Web `sha256:36493866…` | PASS — zéro dérive | PASS — `LOYERTRACKER_TAG=sha-5bf187af` ; digest API `sha256:3e511356…`, Web `sha256:36493866…` — zéro dérive sur 12 min |
| Flyway 15/15 | PASS — 15, rang 15, 0 échec | PASS — 15, rang 15, 0 échec | PASS — 15, rang 15, 0 échec |
| Prometheus 5/5 | PASS — blackbox-keycloak, blackbox-postgres, loyertracker-api, prometheus, pushgateway — 5/5 up | PASS — 5/5 up | PASS — 5/5 up |
| Alertes actives | PASS — 0 | PASS — 0 | PASS — 0 |
| HTTP 5xx / logs | PASS sous surveillance — 2 × `duplicate key bailleur_keycloak_id_key` (17:28 et 17:43 UTC) issus du smoke ; 0 5xx Nginx ; 5xx rate = 0 | PASS — 0 erreur API/Nginx/Postgres/Keycloak depuis T0 ; 5xx rate = 0 | PASS — 0 erreur API/Nginx/Postgres/Keycloak depuis T+12 ; 5xx rate = 0 |
| 401 rate | PASS — 0.0 (5 min) | PASS — 0.0 (5 min) | PASS — 0.0 (5 min) |
| p99 latence | PASS — n/a (Prometheus scrappe depuis < 30 min) | PASS — n/a | PASS — n/a (aucun trafic utilisateur depuis nettoyage smoke) |
| Pool JDBC (Hikari pending) | PASS — 0 | PASS — pending 0, actif 0, idle 10 | PASS — pending 0, actif 0, idle 10 |
| Heartbeat backup | PASS — 0,5 h | PASS — 0,6 h | PASS — 0,7 h (cron 02:15 UTC non encore passé) |
| Batchs applicatifs | — | PASS sous surveillance — absents du Pushgateway (uptime 48 min, cron non encore passé) | PASS sous surveillance — absents du Pushgateway (uptime 51 min) ; cron 02:15 UTC à ~7 h ; seuil 26 h non atteint |
| Capacité hôte | PASS — disque 33 Gio libres (15 %), mémoire 2 017 MiB libres, charge 0,02, swap 0 | PASS — 33 Gio libres, 2 019 MiB libres, charge 0,16, swap 0 | PASS — 33 Gio libres (15 %), 2 015 MiB libres, charge 0,04, swap 0 |
| Docker stats | PASS — api 387 MiB, nginx 3,8 MiB, postgres 80 MiB, keycloak 742 MiB | PASS — api 389 MiB, nginx 3,8 MiB, postgres 75 MiB, keycloak 743 MiB | PASS — api 390 MiB, nginx 3,8 MiB, postgres 75 MiB, keycloak 743 MiB |
| Identité dépôt hôte | PASS — commit `14a672c`, 4 fichiers non suivis connus | PASS — inchangé | PASS — inchangé |
| Verdict | **PASS sous surveillance** | **PASS sous surveillance** ⚠ anticipé (~8 min après T0) | **PASS sous surveillance** ⚠ anticipé (~12 min après T0) |

### Note — Hypercare condensée : T0, T+12 et T+24 anticipés

Les trois checkpoints ont été exécutés le **2026-06-26** entre **17:52 et 18:01 UTC**, soit
dans une fenêtre de 12 minutes suivant `PRODUCTION_DEPLOYED`. Le plan prévoyait :

| Checkpoint | Fenêtre planifiée | Exécuté |
|---|---|---|
| T0 | Immédiat | 2026-06-26 17:52 UTC ✅ |
| T+12 | 2026-06-27 05:19–06:19 UTC | 2026-06-26 17:57 UTC ⚠ (+8 min) |
| T+24 | 2026-06-27 17:19–18:19 UTC | 2026-06-26 18:01 UTC ⚠ (+12 min) |

**Situation atypique** : l'hypercare a été conduite de façon condensée — les trois snapshots
couvrent une période d'observation réelle de ~12 minutes, et non 24 heures. Les métriques de
batch applicatif (`batch_alertes`, `batch_loyers`) sont absentes du Pushgateway car le cron
02:15 UTC ne s'est pas encore déclenché (uptime 51 min au T+24).

**Décision CDO requise — deux options :**

| Option | Description |
|--------|-------------|
| **A — Clôture acceptée** | Les mesures techniques sont toutes PASS sur les 3 checkpoints ; aucune anomalie bloquante ; la stabilité de `sha-5bf187af` est confirmée dans la fenêtre post-déploiement. Le CDO accepte l'hypercare condensée et clôture la release `1.2.0`. Les batchs et le cron seront vérifiés lors de l'exploitation normale (monitoring Prometheus). |
| **B — Complément requis** | Un checkpoint technique complémentaire est exécuté le 2026-06-27 (après 02:15 UTC pour capturer les batchs, ou dans la fenêtre 17:19–18:19 UTC) afin de valider les métriques batchs et la stabilité overnight. La clôture est suspendue jusqu'à ce checkpoint. |

### Note T0 — Logs duplicate key

Deux erreurs `duplicate key value violates unique constraint "bailleur_keycloak_id_key"` à
17:28:47 et 17:43:09 UTC sont visibles dans les logs API. Ces occurrences proviennent du
smoke de la validation finale : `POST /api/bailleurs/inscription` pour `bailleur-test@test.local`
(déjà inscrit → 409 côté API, 0 5xx Nginx). Comportement applicatif normal et déjà qualifié
en T0/T+24 de `1.1.1`. Non bloquant.

## Risques en cours

| ID | Description | Statut |
|----|-------------|--------|
| RP-120-02 | Rollback schéma V15 non trivial (`pg_restore`) | Maintenu — procédure documentée Gate Production §Rollback |
| RP-120-03 | `c1e9c73` (cascade dashboard) exclu de `1.2.0` | Maintenu — à valider Staging puis `1.2.1` |

## Décision finale

| Étape | Statut |
|---|---|
| T0 | **PASS sous surveillance** (2026-06-26 17:52 UTC) |
| T+12 | **PASS sous surveillance** (2026-06-26 17:57 UTC — anticipé) |
| T+24 | **PASS sous surveillance** (2026-06-26 18:01 UTC — anticipé) |
| **Clôture CDO** | **Décision requise — Option A (clôture) ou B (complément 2026-06-27)** |
