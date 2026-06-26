# Dossier de clôture — Release `1.2.0`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-26 |
| Statut | **Hypercare en cours — T0 PASS sous surveillance** |
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
| Date/heure UTC | 2026-06-26 17:52 UTC | Cible 2026-06-27 05:49 UTC (05:19–06:19) | Cible 2026-06-27 17:49 UTC (17:19–18:19) |
| API/Web healthy | PASS — `api`/`nginx`/`postgres`/`keycloak` `(healthy)`, 8/8 Up | — | — |
| Restart counts | PASS — 0 (api, nginx, postgres, keycloak) | — | — |
| Tag `sha-5bf187af` | PASS — `LOYERTRACKER_TAG=sha-5bf187af` ; digest API `sha256:3e511356…`, Web `sha256:36493866…` | — | — |
| Flyway 15/15 | PASS — 15, rang 15, 0 échec | — | — |
| Prometheus 5/5 | PASS — blackbox-keycloak, blackbox-postgres, loyertracker-api, prometheus, pushgateway — 5/5 up | — | — |
| Alertes actives | PASS — 0 | — | — |
| HTTP 5xx / logs | PASS sous surveillance — 2 × `duplicate key bailleur_keycloak_id_key` (17:28 et 17:43 UTC) issus du smoke (inscription 409 déjà qualifiée) ; 0 requête 5xx Nginx ; 5xx rate (5 min) = 0 | — | — |
| 401 rate | PASS — 0.0 (5 min) | — | — |
| p99 latence | PASS — n/a (aucune requête après nettoyage smoke ; Prometheus scrappe depuis < 30 min) | — | — |
| Pool JDBC (Hikari pending) | PASS — 0 | — | — |
| Heartbeat backup | PASS — 0,5 h (backup créé à ~17:20 UTC lors du préflight) | — | — |
| Capacité hôte | PASS — disque 33 Gio libres (15 %), mémoire 2 017 MiB libres, charge 0,02, swap 0 | — | — |
| Docker stats | PASS — api 387 MiB, nginx 3,8 MiB, postgres 80 MiB, keycloak 742 MiB | — | — |
| Identité dépôt hôte | PASS — commit `14a672c` (git pull effectué avant smoke), 4 fichiers non suivis connus (`.env.pre-*`, `.env_bkp`) | — | — |
| Verdict | **PASS sous surveillance** | — | — |

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
| T+12 | En attente (cible 2026-06-27 05:49 UTC) |
| T+24 | En attente (cible 2026-06-27 17:49 UTC) |
| **Clôture CDO** | **En attente de T+24** |
