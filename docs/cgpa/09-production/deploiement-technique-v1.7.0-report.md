# Rapport Déploiement Technique — Release `1.7.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-03 |
| Heure UTC | ~13:20–13:25 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.7.0` — Sprint 9 EP-12a (US-94, Garantie ledger) |
| Tag déployé | **`sha-6a358eb6`** |
| Tag précédent | `sha-2da27182` (`1.6.0`) |
| Verdict | **PASS technique** |

## 1. Exécution

### Sauvegarde `.env` + bascule du tag

```
cd ~/loyertracker
cp .env .env.bak-pre-1.7.0
sed -i 's/^LOYERTRACKER_TAG=.*/LOYERTRACKER_TAG=sha-6a358eb6/' .env
```

**Écart de procédure noté** : contrairement au déploiement `1.6.0` (où `.env` restait inchangé
pendant la validation finale, le tag étant passé en variable d'environnement ponctuelle à la
commande `docker compose up`), `.env` a ici été modifié **directement avant** le pull/déploiement.
Sans impact pratique : une sauvegarde (`.env.bak-pre-1.7.0`) a été prise avant modification,
offrant le même filet de sécurité qu'un `.env` non touché — un rollback resterait aussi simple
(`cp .env.bak-pre-1.7.0 .env` puis redéploiement de `sha-2da27182`).

### Pull des images

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull api nginx
```

`api Pulled` et `nginx Pulled` — layers GHCR téléchargés sans erreur. Digests vérifiés **avant**
pull (`docker buildx imagetools inspect`) :

| Image | Digest GHCR |
|---|---|
| `loyertracker-api:sha-6a358eb6` | `sha256:485c8574cca057d4e00f3c0de640faf4ad8b378c302604b76a752563eb98dfba` |
| `loyertracker-web:sha-6a358eb6` | `sha256:70ae97f2eda455b5c9640cc33aeb6ea4abda9131222b3f948a5ea29768bca5c5` |

Conformes aux digests du Gate Production `1.7.0`.

### Recréation ciblée

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
```

| Conteneur | Action |
|---|---|
| `loyertracker-postgres-1` | `Running` — **inchangé** |
| `loyertracker-keycloak-1` | `Running` — **inchangé** |
| `loyertracker-api-1` | `Recreate` → `Recreated` → `Started` → `Healthy` |
| `loyertracker-nginx-1` | `Recreate` → `Recreated` → `Started` → `Healthy` |

Conteneurs `monitoring` (`prometheus`, `alertmanager`, `pushgateway`, `blackbox`) signalés
« orphelins » par Compose (non inclus dans cette invocation ciblée) **sans** `--remove-orphans` —
non touchés, conformément à l'interdiction des commandes Docker à portée globale (CLAUDE.md v5.4).

**Aucun écart** : seuls `api` et `nginx` ont été recréés, comme prévu.

## 2. Migration Flyway V20

```
Successfully validated 20 migrations (execution time 00:00.064s)
Current version of schema "public": 19
Migrating schema "public" to version "20 - sprint9 garantie ledger"
Successfully applied 1 migration to schema "public", now at version v20 (execution time 00:00.098s)
```

**Migration appliquée sans erreur.**

### Vérification schéma

```sql
SELECT column_name FROM information_schema.columns WHERE table_name='bail' AND column_name='depot_garantie';
-- (0 rows) — colonne supprimée comme prévu
```

### Vérification du backfill — les 3 garanties, y compris les 2 reconstituées par A1

```sql
SELECT g.bail_id, gm.type, gm.debit, gm.credit, gm.solde_apres
FROM garantie_movement gm JOIN garantie g ON g.id = gm.garantie_id ORDER BY g.bail_id;
```

| bail_id | type | débit | crédit | solde après |
|---|---|---|---|---|
| `659ea02c-…` (reconstitué A1) | `DEPOT_INITIAL` | 0.00 | 600.00 | 600.00 |
| `8c905d18-…` (préexistant) | `DEPOT_INITIAL` | 0.00 | 2100.00 | 2100.00 |
| `cb653273-…` (reconstitué A1) | `DEPOT_INITIAL` | 0.00 | 600.00 | 600.00 |

```sql
SELECT bail_id, montant, solde_actuel, statut FROM garantie;
```

| bail_id | montant | solde_actuel | statut |
|---|---|---|---|
| `8c905d18-…` | 2100.00 | 2100.00 | DETENU |
| `659ea02c-…` | 600.00 | 600.00 | DETENU |
| `cb653273-…` | 600.00 | 600.00 | DETENU |

**Confirmation directe que l'option A1 a fonctionné comme prévu** : les 2 baux qui auraient
silencieusement affiché `depotGarantie: 0` (RSV-PROD-S9-01) ont désormais un mouvement
`DEPOT_INITIAL` généré normalement par le backfill de V20 et un `solde_actuel` identique au dépôt
d'origine (600,00 chacun) — aucune perte d'affichage.

## 3. Contrôles post-déploiement

### Services

| Service | État | Restart |
|---|---|---|
| `loyertracker-api-1` | `(healthy)` | 0 |
| `loyertracker-nginx-1` | `(healthy)` | 0 |
| `loyertracker-postgres-1` | `(healthy)` | 0 |
| `loyertracker-keycloak-1` | `(healthy)` | 0 |
| `loyertracker-prometheus-1` | Up (inchangé) | 0 |
| `loyertracker-alertmanager-1` | Up (inchangé) | 0 |
| `loyertracker-pushgateway-1` | Up (inchangé) | 0 |
| `loyertracker-blackbox-1` | Up (inchangé) | 0 |

**8/8 Up — 4/4 `(healthy)` — restart count 0.**

### Applicatif et observabilité

| Contrôle | Résultat |
|---|---|
| `/healthz` (port web HTTP `18080`) | ✅ PASS — `ok` |
| Actuator `{"status":"UP"}` (via Nginx `/api/actuator/health`, `18443`) | ✅ PASS |
| Prometheus cibles `up` | **5/5** |
| Alertmanager alertes actives | **0** (`[]`) |
| Tag actif (api + web) | ✅ `sha-6a358eb6` (confirmé `docker inspect`) |

## 4. Rollback disponible

**Aucun rollback applicatif seul viable** (RSV-S9-03, acceptée par le PO au Gate Production
`1.7.0`) : `bail.depot_garantie` est désormais supprimée — l'ancien code (`sha-2da27182`) qui
mappe encore `Bail.depotGarantie` sur cette colonne échouerait sur toute requête `bail`. Seule
option : restauration complète du backup pré-déploiement
(`loyertracker-20260703-131331.dump`, `infra/backup/restore-postgres.sh`), qui annule aussi V20 et
les 2 garanties reconstituées par A1 (à ré-exécuter si un rollback puis un nouveau déploiement
étaient nécessaires).

## 5. Verdict

| Critère | Statut |
|---|---|
| Pull `api` + `nginx` `sha-6a358eb6` sans erreur | ✅ PASS |
| Digests conformes au Gate Production `1.7.0` (vérifiés avant recréation) | ✅ PASS |
| Recréation ciblée `api`+`nginx` uniquement, `postgres`/`keycloak` inchangés | ✅ PASS |
| 4/4 services applicatifs `(healthy)`, restart=0 | ✅ PASS |
| Flyway : V20 appliquée, 20/20, aucune erreur | ✅ PASS |
| `bail.depot_garantie` supprimée comme prévu | ✅ PASS |
| Backfill des 3 garanties (dont les 2 reconstituées A1) vérifié cohérent | ✅ PASS |
| Actuator UP, `/healthz` `ok` | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |

**Déploiement technique PASS. `PRODUCTION_DEPLOYED` non atteint — validation finale requise.**

Prochaine étape : validation finale `1.7.0` (smoke Production, vérification comportementale du
correctif A1 via l'API authentifiée, `PRODUCTION_DEPLOYED`).
