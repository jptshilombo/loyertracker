# Rapport Préflight + Backup Production — Release `1.5.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-01 |
| Heure UTC | 09:24–10:25 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.5.0` — candidat `sha-08b366fa` |
| Production actuelle | `1.4.0` — `sha-98afa99a` |
| Verdict préflight | **PASS** |
| Verdict backup | **PASS — RP-150-01 LEVÉE** |

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
| `LOYERTRACKER_TAG` | `sha-98afa99a` (Production `1.4.0` — conforme) |
| Digest API actuel | `sha256:d643ea900ae81f27ea327eb569f24879f9263ebde91ec49a3160fbcd390635a3` |
| Digest Web actuel | `sha256:575606758828a24a2f6d67e9d9ed8152c3fe9f7ecc412c9672555619791b2e74` |

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **18/18** (V1→V18) — rang max V18 inchangé |
| Succès toutes migrations | `t` (`bool_and(success)` sur `flyway_schema_history`) |

### Applicatif et observabilité

| Contrôle | Résultat |
|---|---|
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus cibles `up` | **5/5** — prometheus, pushgateway, loyertracker-api, blackbox-keycloak, blackbox-postgres |
| Alertmanager alertes actives | **0** (`[]`) |

### Capacité hôte (09:24 UTC)

| Ressource | Valeur | Seuil |
|---|---|---|
| Disque `/` | 32 Gio libres (83 %) | > 5 % |
| Mémoire disponible | 1,9 Gio | > 500 Mio |
| Charge (load avg 1/5/15 min) | 0,11 / 0,11 / 0,03 | < 1,5 |

## 2. Backup pré-déploiement

### Fichiers créés (10:25 UTC)

| Fichier | Taille | SHA-256 |
|---|---|---|
| `loyertracker-20260701-102523.dump` | 316 Kio | `bd00393291b5089317b99682c19f61110ae77d4070053aed61be0f8cef2e3448` |
| `loyertracker-20260701-102523.globals.sql` | 4,0 Kio | `22dff9abf8d6ec04943801fb22af698ca56065817e27dc9dd2a6324153d602dd` |

Chemin : `/home/ubuntu/loyertracker-backups/daily/` — permissions 600. Heartbeat de sauvegarde
poussé vers le Pushgateway (`loyertracker_backup_last_success_epoch`).

### Vérification `pg_restore --list`

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_restore --list < loyertracker-20260701-102523.dump
```

**Total : 730 entrées** — schéma, données, index, politiques RLS, ACL — intégrité confirmée.

Extrait des dernières entrées (politiques RLS + ACL) :
```
4482; 3256 16631 POLICY public paiement bailleur_isolation loyertracker
4487; 3256 18271 POLICY public patrimoine bailleur_isolation loyertracker
4468; 0 16437 ROW SECURITY public bien loyertracker
4472; 0 16527 ROW SECURITY public garantie loyertracker
4473; 0 16550 ROW SECURITY public honoraire loyertracker
4467; 0 16419 ROW SECURITY public invitation loyertracker
4471; 0 16500 ROW SECURITY public paiement loyertracker
4476; 0 18256 ROW SECURITY public patrimoine loyertracker
2473; 826 16645 DEFAULT ACL public DEFAULT PRIVILEGES FOR FUNCTIONS loyertracker
2472; 826 16644 DEFAULT ACL public DEFAULT PRIVILEGES FOR TABLES loyertracker
```

### Note sur la taille du dump

316 Kio, cohérent avec le dump pré-déploiement `1.4.0` (`loyertracker-20260630-160619.dump`,
312 Kio) — légère croissance liée à l'exploitation depuis le 2026-06-30 (aucune donnée de
smoke résiduelle, la stack Production n'ayant pas de compte de smoke actif en dehors des
fenêtres de déploiement).

## 3. Rollback disponible

| Scénario | Procédure |
|---|---|
| Rollback `1.5.0` → `1.4.0` (applicatif) | `LOYERTRACKER_TAG=sha-98afa99a docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx` |
| Rollback avec perte de données post-1.5.0 | `pg_restore` du dump `loyertracker-20260701-102523.dump` (procédure : `infra/backup/restore-postgres.sh`) |
| `pg_restore` requis entre 1.4.0 et 1.5.0 ? | **Non** — aucune migration entre les deux versions |

## 4. Verdict

| Critère | Statut |
|---|---|
| Services 8/8 Up, 4/4 healthy, restart=0 | ✅ PASS |
| Tag `sha-98afa99a` conforme à la Production `1.4.0` | ✅ PASS |
| Flyway 18/18, toutes `success` | ✅ PASS |
| Actuator UP | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |
| Capacité hôte (disque 32 Gio, mémoire 1,9 Gio, charge 0,11) | ✅ PASS |
| Backup `pg_dump -Fc` créé (316 Kio) | ✅ PASS |
| `pg_restore --list` OK (730 entrées) | ✅ PASS |
| Permissions 600 | ✅ PASS |
| SHA-256 consigné | ✅ PASS |

**Préflight PASS. Backup vérifié. Réserve RP-150-01 levée.**

Prochaine étape autorisée : déploiement technique `1.5.0` (`api` + `nginx`), sous décision distincte.
