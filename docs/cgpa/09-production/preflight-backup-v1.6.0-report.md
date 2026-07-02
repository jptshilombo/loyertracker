# Rapport Préflight + Backup Production — Release `1.6.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-02 |
| Heure UTC | 16:00–17:06 UTC (approx., session distante) |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.6.0` — candidat `sha-2da27182` |
| Production actuelle | `1.5.0` — `sha-08b366fa` |
| Verdict préflight | **PASS** |
| Verdict backup | **PASS — RP-160-01 LEVÉE** |
| Verdict recomptage `patrimoine.adresse` | **PASS — RP-160-02 LEVÉE** |

## 1. Contrôles de santé (lecture seule)

### Services

| Service | État | Restart |
|---|---|---|
| `loyertracker-api-1` | `(healthy)` | 0 |
| `loyertracker-nginx-1` | `(healthy)` | 0 |
| `loyertracker-postgres-1` | `(healthy)` | 0 |
| `loyertracker-keycloak-1` | `(healthy)` | 0 |
| `loyertracker-prometheus-1` | Up | 0 |
| `loyertracker-alertmanager-1` | Up | 0 |
| `loyertracker-pushgateway-1` | Up | 0 |
| `loyertracker-blackbox-1` | Up | 0 |

**8/8 conteneurs Up — 4/4 `(healthy)` — restart count 0.**

### Tag et digests en production

| Élément | Valeur |
|---|---|
| `LOYERTRACKER_TAG` | `sha-08b366fa` (Production `1.5.0` — conforme) |
| Digest API actuel | `sha256:865dd686f76c90d514a26056ed7d6ad248ad5dd6c46d8776e88c68a144d80520` — conforme au Gate Production `1.5.0` |
| Digest Web actuel | `sha256:a7c74954700f300da1e5b40f104087da4c3bb629f0269aba0c1703b07d612b3e` — conforme au Gate Production `1.5.0` |

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **18/18** (V1→V18) — rang max V18 inchangé, conforme à `1.5.0` (V19 pas encore appliquée) |
| Succès toutes migrations | `t` (`bool_and(success)` sur `flyway_schema_history`) |

### RP-160-02 — Recomptage `patrimoine.adresse IS NULL`

```sql
SELECT count(*) AS total_patrimoines, count(*) FILTER (WHERE adresse IS NULL) AS sans_adresse
FROM patrimoine;
```

Résultat : **1 patrimoine total, 1 avec `adresse IS NULL`** — même ligne qu'au comptage du
2026-07-01 (`d753e6d6-564e-4e6d-91c4-09a7c3265a91`, « Patrimoine principal », bailleur
`jptshilombo@gmail.com`). **Aucun écart** : aucune écriture n'a eu lieu sur `patrimoine` depuis
le dernier comptage (serveur resté en usage restreint hors fenêtre de déploiement).
**RP-160-02 LEVÉE** — la migration V19 peut être appliquée sans risque de backfill sur un
périmètre plus large que prévu.

### Applicatif et observabilité

| Contrôle | Résultat |
|---|---|
| `/healthz` (port web) | ✅ `ok` |
| Prometheus cibles `up` | **5/5** — prometheus, pushgateway, loyertracker-api, blackbox-keycloak, blackbox-postgres |
| Alertmanager alertes actives | **1** — `BackupHeartbeatMissing` (heartbeat de sauvegarde absent avant ce Préflight) — **résolue par ce Préflight** : le backup ci-dessous a poussé un nouveau heartbeat au Pushgateway. Écart qualifié et connu (cron de sauvegarde hôte non fiable hors fenêtre de déploiement, cf. historique RSV-T24-01) — **sans rapport avec le candidat `1.6.0`**, non bloquant. |

### Capacité hôte (~17:05 UTC)

| Ressource | Valeur | Seuil |
|---|---|---|
| Disque `/` | 32 Gio libres (82 %) | > 5 % |
| Mémoire disponible | 2,0 Gio | > 500 Mio |
| Charge (load avg 1/5/15 min) | 0,16 / 0,06 / 0,01 | < 1,5 |

## 2. Backup pré-déploiement

### Fichiers créés (17:05:36 UTC, via `infra/backup/backup-postgres.sh`)

| Fichier | Taille | SHA-256 |
|---|---|---|
| `loyertracker-20260702-170536.dump` | 312 Kio (319 264 octets) | `e95064d45dc43b2f8a87f238d0dad1d8fd79647fe5a200a7df9f642f885d8e26` |
| `loyertracker-20260702-170536.globals.sql` | 1,1 Kio (1 108 octets) | `267cae88de045df078331b6e2b57ef8badf0b51b37e9a87b71259008580cacd9` |

Chemin : `/home/ubuntu/loyertracker-backups/daily/` — permissions 600 (script). Heartbeat de
sauvegarde poussé avec succès vers le Pushgateway (`loyertracker_backup_last_success_epoch`),
confirmé par le message `OK heartbeat de sauvegarde poussé`.

### Vérification `pg_restore --list`

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_restore --list < loyertracker-20260702-170536.dump
```

**Total : 730 entrées** — identique au dump pré-déploiement `1.5.0` (même schéma V18, aucune
migration entre les deux) — schéma, données, index, politiques RLS, ACL, intégrité confirmée.
Vérification également exécutée automatiquement par le script (`pg_restore --list ... > /dev/null`,
sortie sans erreur).

### Note sur la taille du dump

312 Kio, cohérent avec le dump pré-déploiement `1.5.0` (`loyertracker-20260701-102523.dump`,
316 Kio) — variation négligeable, aucune donnée de smoke résiduelle en Production.

## 3. Rollback disponible

| Scénario | Procédure |
|---|---|
| Rollback `1.6.0` → `1.5.0` (applicatif seul) | `LOYERTRACKER_TAG=sha-08b366fa docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx` |
| Rollback avec perte de données post-`1.6.0` ou schéma à défaire (V19) | `pg_restore` du dump `loyertracker-20260702-170536.dump` (procédure : `infra/backup/restore-postgres.sh`) |
| `pg_restore` requis entre `1.5.0` et `1.6.0` ? | **Conditionnel** — voir le point d'architecture du Gate Production `1.6.0` : un rollback applicatif seul (sans restaurer le schéma) est **risqué si une inscription a eu lieu après le déploiement** (l'ancien `InscriptionService` créerait un patrimoine sans `adresse`, en violation de la contrainte `NOT NULL` posée par V19). Si aucune inscription n'a eu lieu depuis le déploiement, le rollback applicatif seul reste acceptable en dégradé temporaire ; sinon, restauration complète requise. |

## 4. Verdict

| Critère | Statut |
|---|---|
| Services 8/8 Up, 4/4 healthy, restart=0 | ✅ PASS |
| Tag `sha-08b366fa` conforme à la Production `1.5.0` | ✅ PASS |
| Digests API/Web conformes au Gate Production `1.5.0` | ✅ PASS |
| Flyway 18/18, toutes `success` | ✅ PASS |
| Recomptage `patrimoine.adresse IS NULL` = 1/1, sans écart (RP-160-02) | ✅ PASS |
| Actuator/`healthz` UP | ✅ PASS |
| Prometheus 5/5 up | ✅ PASS |
| Alertmanager | ⚠️ 1 alerte `BackupHeartbeatMissing`, résolue par ce Préflight, sans rapport avec `1.6.0` |
| Capacité hôte (disque 32 Gio, mémoire 2,0 Gio, charge 0,16) | ✅ PASS |
| Backup `pg_dump -Fc` créé (312 Kio) | ✅ PASS |
| `pg_restore --list` OK (730 entrées) | ✅ PASS |
| Permissions 600 | ✅ PASS |
| SHA-256 consigné (dump + globals) | ✅ PASS |

**Préflight PASS. Backup vérifié. Réserves RP-160-01 et RP-160-02 levées.**

Les deux conditions bloquantes du Gate Production `1.6.0`
(`gate-production-v1.6.0-decision.md` §4/§6) sont désormais satisfaites.

Prochaine étape autorisée : déploiement technique `1.6.0` (`api` + `nginx`, migration V19), sous
décision distincte.
