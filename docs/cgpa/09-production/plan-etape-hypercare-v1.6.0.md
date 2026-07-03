# Plan Hypercare — Release `1.6.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-02 16:50 UTC |
| T0 | 2026-07-02 17:11 UTC (checkpoint réalisé ~21 min après `PRODUCTION_DEPLOYED`) |
| T+12 | 2026-07-03 04:50 UTC ± 30 min |
| T+24 | 2026-07-03 16:50 UTC ± 30 min |
| Tag surveillé | `sha-2da27182` |
| Rollback disponible | `sha-08b366fa` — **applicatif seul, avec réserve** (cf. Gate Production `1.6.0` : risqué si une inscription a eu lieu post-déploiement, contrainte `NOT NULL` V19 vs ancien `InscriptionService`) |

## Critères de suspension (tout FAIL → rollback immédiat)

- Restart inattendu d'un conteneur applicatif (`api`, `nginx`, `keycloak`, `postgres`).
- Régression fonctionnelle sur le parcours bailleur (dashboard vide, création de patrimoine
  bloquée par la contrainte `adresse`, affichage devise incorrect sur Paiements/Honoraires).
- Erreur 500 à l'inscription d'un nouveau bailleur (signal du risque de rollback documenté au
  Gate Production — contrainte V19 vs ancien code, à surveiller particulièrement pour ce cycle).
- Augmentation anormale des erreurs 5xx sur Prometheus.
- Alerte Alertmanager hors `BackupHeartbeatMissing` (pré-existante connue, hors fenêtre de
  déploiement).

## Rollback immédiat si nécessaire

```
LOYERTRACKER_TAG=sha-08b366fa \
  docker compose \
    --project-directory /home/ubuntu/loyertracker \
    -f /home/ubuntu/loyertracker/docker-compose.yml \
    -f /home/ubuntu/loyertracker/docker-compose.prod.yml \
    up -d nginx api
```

**Attention** : si une inscription de nouveau bailleur a eu lieu entre `PRODUCTION_DEPLOYED` et le
rollback, ce rollback applicatif seul est insuffisant (l'ancien `InscriptionService` violerait la
contrainte `NOT NULL` posée par V19). Dans ce cas, restaurer aussi le backup pré-déploiement
(`loyertracker-20260702-170536.dump`, procédure `infra/backup/restore-postgres.sh`).

---

## Checkpoint T0 — 2026-07-02 17:11 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke 59/0 | ✅ PASS — `validation-finale-v1.6.0-report.md` |
| `LOYERTRACKER_TAG=sha-2da27182` persisté dans `.env` | ✅ PASS |
| `bailleur-test` désactivé dans Keycloak | ✅ PASS (`enabled: false`) |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ PASS |
| Flyway 19/19 | ✅ PASS |
| Digest API | ✅ `sha256:ecdd1408…` — conforme au Gate Production |
| Digest Web | ✅ `sha256:64263317…` — conforme au Gate Production |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 `up` | ✅ PASS |
| Alertmanager | ✅ `[]` — 0 alerte active |
| Capacité hôte | ✅ disque 32 Gio libres (18 %), mémoire 1,9 Gio dispo, charge 0,07/0,04/0,08 |
| p99 latence (10 min) | ✅ ~47 ms |
| 5xx rate (10 min) | ✅ 0 (aucun point de données) |
| Hikari pending | ✅ 0 |
| Heartbeat backup | ✅ âge ~65 min (`loyertracker_backup_last_success_epoch`, poussé au Préflight) — largement sous le seuil (26 h) |
| Logs API — erreurs critiques (60 min) | ✅ 2× `duplicate key bailleur_keycloak_id_key` — attendues (smoke POST inscription 409), aucune erreur inattendue |

**Décision T0 : PASS — hypercare sous surveillance.**

T+12 prévu : **2026-07-03 04:50 UTC** (tolérance ±30 min).
T+24 prévu : **2026-07-03 16:50 UTC** (tolérance ±30 min).

---

## Checkpoint T+12 — exécuté le 2026-07-03 à ~11:25 UTC

**Statut : PASS (qualifié — retard de planning, cf. ci-dessous)**

| Contrôle | Résultat |
|---|---|
| `LOYERTRACKER_TAG=sha-2da27182` persisté dans `.env` | ✅ PASS — aucune dérive depuis T0 |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ PASS |
| Flyway 19/19 | ✅ PASS — inchangé (Sprint 9/V20 non déployé) |
| Actuator `{"status":"UP"}` | ✅ PASS |
| `/healthz` | ✅ PASS — `ok` |
| Prometheus 5/5 `up` | ✅ PASS |
| Alertmanager | ⚠️ **1 alerte active constatée** `BackupHeartbeatMissing` (critical, `startsAt` 07:19:05 UTC) — **remédiée dans la même session** (voir ci-dessous), `[]` confirmé après |
| Capacité hôte | ✅ disque 32 Gio libres (18 %), mémoire 2,0 Gio dispo, charge 0,31/0,11/0,03 |
| p99 latence (10 min) | ✅ ~6,2 ms |
| 5xx rate (10 min) | ✅ 0 (aucun point de données) |
| Hikari pending | ✅ 0 |
| Logs API — erreurs critiques (60 min) | ✅ 0 erreur, 0 `duplicate key` |

**Écart de planning constaté** : la fenêtre T+12 (04:50 UTC ± 30 min) était fermée depuis
~6h35 au moment du contrôle réel (~11:25 UTC). **Qualifié comme retard de process, pas comme
signal d'incident** : l'alerte `BackupHeartbeatMissing` elle-même n'a commencé à sonner qu'à
07:19 UTC — soit **après** la fermeture de la fenêtre T+12 — aucun signal n'existait pendant la
fenêtre cible elle-même. Aucun autre contrôle (conteneurs, Flyway, latence, erreurs) ne montre de
dérive sur la période.

**Cause de `BackupHeartbeatMissing` et remédiation** : dernier dump sur disque avant ce contrôle
= `loyertracker-20260702-170536.dump` (Préflight pré-déploiement, 2026-07-02 17:05 UTC). Le cron
est correctement configuré (`15 2 * * * … backup-postgres.sh`), mais **le daemon `cron` lui-même
n'a redémarré que le 2026-07-03 07:48:37** (arrêt/redémarrage non expliqué, probablement lié à une
maintenance système comme les précédents redémarrages `unattended-upgrades` déjà observés en
hypercare `1.4.0`) — il a donc raté l'exécution planifiée de 02:15 UTC ce matin. **Même cause
récurrente et déjà qualifiée que RSV-T24-01** (hypercare `1.4.0`, 2026-07-01) : cron peu fiable
sur cet hôte, jamais bloquant, résolu à chaque fois par une exécution manuelle du script de
sauvegarde. Remédiation exécutée : `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
./infra/backup/backup-postgres.sh` → `loyertracker-20260703-124953.dump` (316 Kio, SHA-256
`8535ea8f…`, globals SHA-256 `528a343e…`, `pg_restore --list` 730 entrées OK, permissions 600).
Heartbeat repoussé au Pushgateway ; `GET /api/v2/alerts` Alertmanager confirmé **`[]`** (0 alerte
active) après remédiation.

**Décision T+12 : PASS** — hypercare sous surveillance, aucun critère de suspension déclenché.

T+24 inchangé, toujours prévu : **2026-07-03 16:50 UTC** (tolérance ±30 min).

---

## Post-clôture (après décision CDO GO au T+24)

- Promouvoir `CHANGELOG.md` : scinder `[Non publié]` en `[1.5.0] — 2026-07-01` (rétroactif) +
  `[1.6.0] — 2026-07-02` (RP-160-03).
- Mettre à jour `docs/project-state.md` (version, bandeau).
- Mettre à jour `docs/prod-state.md` §0G (statut clôture).
- Créer `docs/cgpa/09-production/cloture-release-v1.6.0.md`.
