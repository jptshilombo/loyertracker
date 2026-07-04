# Rapport Déploiement Technique — Release `1.8.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-04 |
| Heure UTC | ~16:22–16:26 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.8.0` — Sprint 10 EP-12b (US-95/96/97, Garantie usage métier) + correctif RSV-S10-01 |
| Tag déployé | **`sha-2c5f43c7`** |
| Tag précédent | `sha-6a358eb6` (`1.7.0`) |
| Verdict | **PASS technique** |

## 1. Exécution

### Synchronisation du dépôt + sauvegarde `.env` + bascule du tag

```
cd ~/loyertracker
git pull --ff-only origin main       # → 97f7caf (merge PR #177)
cp .env .env.bak-pre-1.8.0           # permissions 600
sed -i 's/^LOYERTRACKER_TAG=.*/LOYERTRACKER_TAG=sha-2c5f43c7/' .env
```

### Vérification des digests **avant** pull

`docker buildx` n'étant pas installé sur l'hôte (écart d'outillage vs le rapport `1.7.0`),
vérification faite par `docker manifest inspect -v` — même garantie :

| Image | Digest GHCR | Conforme au Gate |
|---|---|---|
| `loyertracker-api:sha-2c5f43c7` | `sha256:bab66aa35d9b70d045be284cd0132746e36377110dd29a3079f1ca821a2b45a5` | ✅ |
| `loyertracker-web:sha-2c5f43c7` | `sha256:9c8915f0eca279bf75c548d7a4846d48c266a9d44be7c9b405bd526847cd3f87` | ✅ |

Confirmés une seconde fois **après** recréation via `docker inspect` (`RepoDigests`) — zéro
dérive.

### Pull + recréation ciblée

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull api nginx
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
```

| Conteneur | Action |
|---|---|
| `loyertracker-postgres-1` | `Healthy` — **inchangé** (Up 2 h) |
| `loyertracker-keycloak-1` | `Running` — **inchangé** (Up 2 h) |
| `loyertracker-api-1` | Recréé → `Started` → `(healthy)` |
| `loyertracker-nginx-1` | Recréé → `Started` → `(healthy)` |

Monitoring (`prometheus`, `alertmanager`, `pushgateway`, `blackbox`) non touché — aucune
commande Docker à portée globale (CLAUDE.md v5.4). **Aucun écart.**

## 2. Migration Flyway V21

```
Successfully validated 21 migrations (execution time 00:00.421s)
Current version of schema "public": 20
Migrating schema "public" to version "21 - sprint10 garantie usage"
Successfully applied 1 migration to schema "public", now at version v21 (execution time 00:00.063s)
```

**Migration appliquée sans erreur — 21/21.** Additive : aucune ligne existante modifiée
(conforme au profil de risque du Gate §4.2).

### Vérification schéma

| Objet | Résultat |
|---|---|
| Colonne `paiement.garantie_movement_id` | ✅ présente, `is_nullable = YES` |
| Contrainte `paiement_garantie_movement_id_fkey` | ✅ présente |
| Index `idx_paiement_garantie_movement_id` | ✅ présent |

### Vérification des données réelles

Invariant `garantie.solde_actuel = Σ(crédit − débit)` : **3/3 PASS** (`550f1d84…`, `ef87b3aa…`,
`01754057…`) — identique au Préflight, aucune donnée altérée par V21.

## 3. Contrôles post-déploiement

| Contrôle | Résultat |
|---|---|
| Services | **8/8 Up — 4/4 `(healthy)` — restart=0** |
| `/healthz` (port web HTTP `18080`) | ✅ `ok` |
| Actuator (via Nginx `18443`) | ✅ `{"status":"UP"}` |
| Prometheus cibles `up` | **5/5** |
| Alertmanager | **`[]`** — 0 alerte |
| Tag actif (api + web, `docker inspect`) | ✅ `sha-2c5f43c7` |
| `.env` persisté | ✅ `LOYERTRACKER_TAG=sha-2c5f43c7` (backup `.env.bak-pre-1.8.0`, 600) |
| Site public `https://loyertracker.loyerpro.org` | ✅ HTTP 200 |

## 4. Rollback disponible

**Rollback applicatif seul viable** (contraste explicite avec `1.7.0`/RSV-S9-03) : retour du tag
à `sha-6a358eb6` (`cp .env.bak-pre-1.8.0 .env` puis recréation ciblée) — l'ancien code ignore la
colonne V21, qui resterait en place. Limite documentée au Gate (§3) : les mouvements
`RETENUE_LOYER`/`COMPLEMENT` créés entre-temps resteraient en base, cohérents mais non
« explicables » dans l'UI `1.7.0`. Restauration complète du backup pré-déploiement
(`loyertracker-20260704-170836.dump`) en dernier recours.

## 5. Verdict

| Critère | Statut |
|---|---|
| Pull `api`+`nginx` `sha-2c5f43c7` sans erreur | ✅ PASS |
| Digests conformes au Gate (vérifiés avant **et** après) | ✅ PASS |
| Recréation ciblée uniquement, `postgres`/`keycloak` inchangés | ✅ PASS |
| 4/4 services applicatifs `(healthy)`, restart=0 | ✅ PASS |
| Flyway : V21 appliquée, 21/21, aucune erreur | ✅ PASS |
| Schéma V21 vérifié (colonne + FK + index) | ✅ PASS |
| Invariant ledger 3/3, données réelles intactes | ✅ PASS |
| Actuator UP, `/healthz` ok, site public 200 | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |

**Déploiement technique PASS.** Validation finale (smoke + nettoyage) :
`validation-finale-v1.8.0-report.md` — exécutée dans la même session.
