# Rapport Préflight + Backup Production — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Heure UTC | 08:49–09:50 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.2.1` — candidat `sha-47172297` |
| Production actuelle | `1.2.0` — `sha-5bf187af` |
| Verdict préflight | **PASS** |
| Verdict backup | **PASS — RP-121-01 LEVÉE** |

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
| `LOYERTRACKER_TAG` | `sha-5bf187af` (Production `1.2.0` — conforme) |
| Digest API actuel | `sha256:3e511356e723acb0a9769f494b1b574bfa9342f4cb0d419a23168166112cca0d` |
| Digest Web actuel | `sha256:36493866018fc2cbd7a15eed86a378bd836ef472378ea998e25a83ea9eca2520` |

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
| Alertmanager alertes actives | **0** |

### Capacité hôte (08:49 UTC)

| Ressource | Valeur | Seuil |
|---|---|---|
| Disque `/` | 33 Gio libres (15 %) | > 5 % |
| Mémoire disponible | 2,0 Gio | > 500 Mio |
| Charge (load avg) | 0,00 / 0,01 / 0,05 | < 1,5 |

## 2. Backup pré-déploiement

### Fichiers créés (09:50 UTC)

| Fichier | Taille | SHA-256 |
|---|---|---|
| `loyertracker-20260627-085033.dump` | 311 Kio | `511ef7a730c11a2a10b620d12e34b03174857fea020103d3b874ea626054fe9e` |
| `loyertracker-20260627-085033-globals.sql` | 1,1 Kio | `c934315163d707b49bae87d3d7f4052d61cff3175e206eb243453985b9b20396` |

Chemin : `/home/ubuntu/loyertracker-backups/daily/` — permissions 600.

### Vérification `pg_restore --list`

```
docker exec loyertracker-postgres-1 pg_restore --list loyertracker-20260627-085033.dump
```

**Total : 730 entrées** — schéma, données, index, politiques RLS, ACL — intégrité confirmée.

Extrait des dernières entrées (politiques RLS + ACL) :
```
4480; POLICY public paiement bailleur_isolation
4485; POLICY public patrimoine bailleur_isolation
4466; ROW SECURITY public bien
4474; ROW SECURITY public patrimoine
2473; DEFAULT ACL public FUNCTIONS
2472; DEFAULT ACL public TABLES
```

### Note sur la taille du dump

311 Kio est cohérent avec la base de données LoyerTracker en exploitation minimale (données
de tests de smoke nettoyées, utilisateurs réels limités à la phase MVP). Le dump `1.2.0`
pré-déploiement était de 308 Kio (`loyertracker-20260626-182030.dump`) — différence de 3 Kio
imputable aux données d'exploitation depuis le 2026-06-26.

## 3. Rollback disponible

| Scénario | Procédure |
|---|---|
| Rollback `1.2.1` → `1.2.0` (applicatif) | `LOYERTRACKER_TAG=sha-5bf187af docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d nginx api` |
| Rollback avec perte de données post-1.2.1 | `pg_restore` du dump `loyertracker-20260627-085033.dump` (procédure : `infra/backup/restore-postgres.sh`) |
| pg_restore requis entre 1.2.0 et 1.2.1 ? | **Non** — aucune migration entre les deux versions |

## 4. Verdict

| Critère | Statut |
|---|---|
| Services 8/8 Up, 4/4 healthy, restart=0 | ✅ PASS |
| Tag `sha-5bf187af` conforme à la Production `1.2.0` | ✅ PASS |
| Flyway 15/15 | ✅ PASS |
| Actuator UP | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |
| Capacité hôte (disque, mémoire, charge) | ✅ PASS |
| Backup `pg_dump -Fc` créé | ✅ PASS |
| `pg_restore --list` OK (730 entrées) | ✅ PASS |
| Permissions 600 | ✅ PASS |
| SHA-256 consigné | ✅ PASS |

**Préflight PASS. Backup vérifié. Réserve RP-121-01 levée.**

Prochaine étape autorisée : déploiement technique `1.2.1` (Étape 5), sous décision distincte.
