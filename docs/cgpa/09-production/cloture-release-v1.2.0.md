# Dossier de clôture — Release `1.2.0`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-26 |
| Statut | **Hypercare en cours — T0 et T+12 anticipé PASS ; T+24 en attente** |
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
| Date/heure UTC | 2026-06-26 17:52 UTC | **2026-06-26 17:57 UTC** ⚠ anticipé (voir note) | Cible 2026-06-27 17:49 UTC (17:19–18:19) |
| API/Web healthy | PASS — `api`/`nginx`/`postgres`/`keycloak` `(healthy)`, 8/8 Up | PASS — `api`/`nginx`/`postgres`/`keycloak` `(healthy)`, 8/8 Up | — |
| Restart counts | PASS — 0 (api, nginx, postgres, keycloak) | PASS — 0 (api, nginx, postgres, keycloak) | — |
| Tag `sha-5bf187af` | PASS — `LOYERTRACKER_TAG=sha-5bf187af` ; digest API `sha256:3e511356…`, Web `sha256:36493866…` | PASS — `LOYERTRACKER_TAG=sha-5bf187af` ; digest API `sha256:3e511356…`, Web `sha256:36493866…` — zéro dérive | — |
| Flyway 15/15 | PASS — 15, rang 15, 0 échec | PASS — 15, rang 15, 0 échec | — |
| Prometheus 5/5 | PASS — blackbox-keycloak, blackbox-postgres, loyertracker-api, prometheus, pushgateway — 5/5 up | PASS — 5/5 up | — |
| Alertes actives | PASS — 0 | PASS — 0 | — |
| HTTP 5xx / logs | PASS sous surveillance — 2 × `duplicate key bailleur_keycloak_id_key` (17:28 et 17:43 UTC) issus du smoke (inscription 409 déjà qualifiée) ; 0 requête 5xx Nginx ; 5xx rate (5 min) = 0 | PASS — 0 erreur API depuis T0, 0 5xx Nginx, 0 FATAL Postgres, 0 erreur Keycloak depuis 17:52 UTC ; 5xx rate = 0 | — |
| 401 rate | PASS — 0.0 (5 min) | PASS — 0.0 (5 min) | — |
| p99 latence | PASS — n/a (aucune requête après nettoyage smoke ; Prometheus scrappe depuis < 30 min) | PASS — n/a (pas de trafic utilisateur depuis le smoke ; période trop courte après T0) | — |
| Pool JDBC (Hikari pending) | PASS — 0 | PASS — pending 0, actif 0, idle 10 | — |
| Heartbeat backup | PASS — 0,5 h (backup créé à ~17:20 UTC lors du préflight) | PASS — 0,6 h | — |
| Batchs applicatifs | — | PASS sous surveillance — `batch_alertes` et `batch_loyers` absents du Pushgateway (métriques non encore poussées depuis le redémarrage serveur à ~17:10 UTC, uptime 48 min) ; seuil 26 h non atteint ; à confirmer à T+24 | — |
| Capacité hôte | PASS — disque 33 Gio libres (15 %), mémoire 2 017 MiB libres, charge 0,02, swap 0 | PASS — disque 33 Gio libres (15 %), mémoire 2 019 MiB libres, charge 0,16, swap 0 | — |
| Docker stats | PASS — api 387 MiB, nginx 3,8 MiB, postgres 80 MiB, keycloak 742 MiB | PASS — api 389 MiB, nginx 3,8 MiB, postgres 75 MiB, keycloak 743 MiB | — |
| Identité dépôt hôte | PASS — commit `14a672c` (git pull effectué avant smoke), 4 fichiers non suivis connus (`.env.pre-*`, `.env_bkp`) | PASS — inchangé | — |
| Verdict | **PASS sous surveillance** | **PASS sous surveillance** ⚠ exécuté ~8 min après T0 — décision CDO requise | — |

### Note T+12 — Checkpoint anticipé

Le checkpoint T+12 a été exécuté à 17:57 UTC le 2026-06-26, soit environ **8 minutes après T0**
(17:49 UTC), bien avant la fenêtre planifiée (05:19–06:19 UTC le 2026-06-27). Cette situation
est inverse au cas `1.1.1` (T+12 exécuté en retard) : ici le checkpoint est en avance.

Le CDO doit décider si ce checkpoint :
- (A) se substitue au T+12 planifié (les mesures PASS justifient de passer directement à T+24) ;
- (B) constitue un checkpoint intermédiaire et un T+12 formel doit encore être exécuté dans la
  fenêtre 05:19–06:19 UTC le 2026-06-27.

Les métriques batchs (`batch_alertes`, `batch_loyers`) absentes du Pushgateway sont à surveiller
à T+24 — le cron nocturne devrait les alimenter d'ici là.

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
| T+12 | **PASS sous surveillance** (2026-06-26 17:57 UTC — anticipé, décision CDO requise) |
| T+24 | En attente (cible 2026-06-27 17:49 UTC) |
| **Clôture CDO** | **En attente de T+24** |
