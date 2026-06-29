# Rapport Préflight + Backup Production — Release `1.3.0`

| Champ | Valeur |
|---|---|
| Date | 2026-06-29 |
| Heure UTC | 14:05–15:07 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.3.0` — candidat `sha-a42d860d` |
| Production actuelle | `1.2.1` — `sha-47172297` |
| Verdict préflight | **PASS** |
| Verdict backup | **PASS — RP-130-01 LEVÉE** |

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
| `LOYERTRACKER_TAG` | `sha-47172297` (Production `1.2.1` — conforme) |
| Digest API actuel | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Digest Web actuel | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **15/15** (V1→V15) — rang max V15 (`affectations_exceptions`) |
| Succès toutes migrations | `t` (toutes les lignes `success=t`) |

### Applicatif et observabilité

| Contrôle | Résultat |
|---|---|
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus cibles `up` | **5/5** — prometheus, pushgateway, loyertracker-api, blackbox-keycloak, blackbox-postgres |
| Alertmanager alertes actives | **0** (`[]`) |

### Capacité hôte (14:05 UTC)

| Ressource | Valeur | Seuil |
|---|---|---|
| Disque `/` | 32 Gio libres (84 %) | > 5 % |
| Mémoire disponible | 2,1 Gio | > 500 Mio |
| Charge (load avg 1/5/15 min) | 0,11 / 0,10 / 0,09 | < 1,5 |

## 2. Backup pré-déploiement

### Fichiers créés (15:07 UTC)

| Fichier | Taille | SHA-256 |
|---|---|---|
| `loyertracker-20260629-140719.dump` | 314 Kio | `524ee4bb0e7a0a3c4a7d87e3fd73efbcd2b19ef3548cbb32e450ddd77e28c5a8` |
| `loyertracker-20260629-140719-globals.sql` | 1,1 Kio | `c041ccf225d449eba8fdcef22dc948d7f0ee4070faea54c30775340989af5547` |

Chemin : `/home/ubuntu/loyertracker-backups/daily/` — permissions 600.

### Vérification `pg_restore --list`

```
docker cp /home/ubuntu/loyertracker-backups/daily/loyertracker-20260629-140719.dump loyertracker-postgres-1:/tmp/
docker exec loyertracker-postgres-1 pg_restore --list loyertracker-20260629-140719.dump
```

**Total : 730 entrées** — schéma, données, index, politiques RLS, ACL — intégrité confirmée.

Extrait des dernières entrées (politiques RLS + ACL) :
```
4466; ROW SECURITY public bien
4470; ROW SECURITY public garantie
4471; ROW SECURITY public honoraire
4465; ROW SECURITY public invitation
4469; ROW SECURITY public paiement
4474; ROW SECURITY public patrimoine
2473; DEFAULT ACL public FUNCTIONS
2472; DEFAULT ACL public TABLES
```

### Note sur la taille du dump

314 Kio est cohérent avec la base LoyerTracker en exploitation minimale (données de smoke
nettoyées, utilisateurs réels limités à la phase MVP). Le dump `1.2.1` pré-déploiement était
de 311 Kio (`loyertracker-20260627-085033.dump`) — différence de 3 Kio imputable aux
données d'exploitation depuis le 2026-06-27.

## 3. Rollback disponible

| Scénario | Procédure |
|---|---|
| Rollback `1.3.0` → `1.2.1` (applicatif) | `LOYERTRACKER_TAG=sha-47172297 docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d nginx api` |
| Rollback avec perte de données post-1.3.0 | `pg_restore` du dump `loyertracker-20260629-140719.dump` (procédure : `infra/backup/restore-postgres.sh`) |
| pg_restore requis entre 1.2.1 et 1.3.0 ? | **Non** — aucune migration entre les deux versions |

## 4. Verdict

| Critère | Statut |
|---|---|
| Services 8/8 Up, 4/4 healthy, restart=0 | ✅ PASS |
| Tag `sha-47172297` conforme à la Production `1.2.1` | ✅ PASS |
| Flyway 15/15, toutes `success` | ✅ PASS |
| Actuator UP | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |
| Capacité hôte (disque 32 Gio, mémoire 2,1 Gio, charge 0,11) | ✅ PASS |
| Backup `pg_dump -Fc` créé (314 Kio) | ✅ PASS |
| `pg_restore --list` OK (730 entrées) | ✅ PASS |
| Permissions 600 | ✅ PASS |
| SHA-256 consigné | ✅ PASS |

**Préflight PASS. Backup vérifié. Réserve RP-130-01 levée.**

Prochaine étape autorisée : déploiement technique `1.3.0` (`api` + `nginx`), sous décision distincte.
