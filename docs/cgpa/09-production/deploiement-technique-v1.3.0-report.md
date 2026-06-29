# Rapport Déploiement Technique — Release `1.3.0`

| Champ | Valeur |
|---|---|
| Date | 2026-06-29 |
| Heure UTC | 15:11–15:12 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.3.0` |
| Tag déployé | **`sha-a42d860d`** |
| Tag précédent | `sha-47172297` (`1.2.1`) |
| Verdict | **PASS technique** |

## 1. Exécution

### Pull des images (15:11 UTC)

```
cd ~/loyertracker
LOYERTRACKER_TAG=sha-a42d860d docker compose -f docker-compose.yml -f docker-compose.prod.yml pull api nginx
```

`api Pulled` et `nginx Pulled` — layers GHCR téléchargés sans erreur.

### Recréation ciblée (15:11 UTC)

```
LOYERTRACKER_TAG=sha-a42d860d docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
```

| Conteneur | Action |
|---|---|
| `loyertracker-postgres-1` | `Running` — inchangé |
| `loyertracker-keycloak-1` | `Running` — inchangé |
| `loyertracker-api-1` | `Recreate` → `Recreated` → `Started` |
| `loyertracker-nginx-1` | `Recreate` → `Recreated` → `Started` |

Note : avertissement orphelins monitoring (prometheus/alertmanager/pushgateway/blackbox) attendu —
ces conteneurs sont lancés par l'overlay `docker-compose.monitoring.yml --profile monitoring`, non
par la commande de déploiement ciblée. Comportement nominal.

## 2. Contrôles post-déploiement

### Services (15:12 UTC)

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

**8/8 Up — 4/4 `(healthy)` — restart count 0.**

### Digests déployés

| Image | Digest GHCR |
|---|---|
| `loyertracker-api:sha-a42d860d` | `sha256:c3d89f0d6da5cfad55daa0ad921df4be0539757c8ca3384110323e7425290749` |
| `loyertracker-web:sha-a42d860d` | `sha256:c30708984117717b8bad0b6447fd009b561680376c7d3a7d60ffa81e1ba8c4ba` |

Conformes aux digests du Gate Production `1.3.0`.

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **15/15** (V1→V15) — aucune migration supplémentaire |
| Succès toutes migrations | `t` |

Comportement attendu : `1.3.0` n'introduit aucune migration, Flyway reste à V15.

### Actuator et observabilité

| Contrôle | Résultat |
|---|---|
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus cibles `up` | **5/5** |
| Alertmanager alertes actives | **0** (`[]`) |

## 3. État `.env`

**`.env` non modifié à ce stade.** Le tag `LOYERTRACKER_TAG=sha-47172297` reste dans `.env` pendant
la validation finale. La persistance de `sha-a42d860d` interviendra après smoke 47/0 PASS et
décision CDO `PRODUCTION_DEPLOYED`.

Rollback applicatif disponible : `LOYERTRACKER_TAG=sha-47172297 docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx`

## 4. Verdict

| Critère | Statut |
|---|---|
| Pull `api` + `nginx` `sha-a42d860d` sans erreur | ✅ PASS |
| Postgres et Keycloak inchangés (`Running`) | ✅ PASS |
| 4/4 services applicatifs `(healthy)`, restart=0 | ✅ PASS |
| Digests conformes au Gate Production | ✅ PASS |
| Flyway 15/15, aucune migration supplémentaire | ✅ PASS |
| Actuator UP | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |

**Déploiement technique PASS. `PRODUCTION_DEPLOYED` non atteint — validation finale requise.**

Prochaine étape : validation finale `1.3.0` (smoke 47/0, persistance `.env`, `PRODUCTION_DEPLOYED`).
