# Rapport Déploiement Technique — Release `1.6.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-02 |
| Heure UTC | 16:22–16:25 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.6.0` |
| Tag déployé | **`sha-2da27182`** |
| Tag précédent | `sha-08b366fa` (`1.5.0`) |
| Verdict | **PASS technique** |

## 1. Exécution

### Synchronisation dépôt + pull des images (16:22 UTC)

```
cd ~/loyertracker
git pull --ff-only origin main          # 366f5b6 → b824a0e (fast-forward)
LOYERTRACKER_TAG=sha-2da27182 docker compose -f docker-compose.yml -f docker-compose.prod.yml pull api nginx
```

`api Pulled` et `nginx Pulled` — layers GHCR téléchargés sans erreur.

Digests vérifiés **avant** recréation (`docker inspect --format='{{index .RepoDigests 0}}'`) :

| Image | Digest GHCR |
|---|---|
| `loyertracker-api:sha-2da27182` | `sha256:ecdd14084db6fcd5a556dac5ec8f6c62ee0c0303fce4475c2ee0fb8e959b1f3f` |
| `loyertracker-web:sha-2da27182` | `sha256:64263317fd09874f910a309e22b09e748529eb671b2202a76f643667bde920aa` |

Conformes aux digests du Gate Production `1.6.0`.

### Recréation ciblée (16:24 UTC)

```
LOYERTRACKER_TAG=sha-2da27182 docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
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

**Aucun écart** cette fois : contrairement au déploiement `1.5.0` (recréation non ciblée de
`postgres`/`keycloak` sans cause identifiée), seuls `api` et `nginx` ont été recréés, comme prévu
par le plan (Gate Production `1.6.0` §6).

## 2. Migration Flyway V19

```
Successfully validated 19 migrations (execution time 00:00.129s)
Current version of schema "public": 18
Migrating schema "public" to version "19 - sprint7 patrimoine enrichi"
Successfully applied 1 migration to schema "public", now at version v19 (execution time 00:00.040s)
```

**Migration appliquée sans erreur.** Vérification post-migration :

```sql
SELECT count(*), bool_and(success) FROM flyway_schema_history;   -- 19 | t
SELECT id, nom, adresse, ville, pays FROM patrimoine;
```

| id | nom | adresse | ville | pays |
|---|---|---|---|---|
| `d753e6d6-564e-4e6d-91c4-09a7c3265a91` | Patrimoine principal | `Adresse à renseigner` | *(vide)* | *(vide)* |

Backfill générique appliqué comme prévu (RP-160-05 : adresse réelle **restant à appliquer**,
action différée à exécuter avant la validation finale — voir §4). Aucune perte de données :
2 bailleurs, 3 biens, 3 baux présents (état applicatif courant, cohérent avec l'exploitation
depuis le dernier nettoyage transactionnel).

## 3. Contrôles post-déploiement

### Services (16:25 UTC, après stabilisation)

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
| Prometheus cibles `up` | **5/5** — prometheus, pushgateway, loyertracker-api, blackbox-keycloak, blackbox-postgres |
| Alertmanager alertes actives | **0** (`[]`) — l'alerte `BackupHeartbeatMissing` constatée au Préflight est résolue |
| CSP Nginx (héritée de `1.5.0`, US-72) | ✅ PASS — `script-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'` présents |

## 4. État `.env`

**`.env` non modifié à ce stade.** Le tag `LOYERTRACKER_TAG=sha-08b366fa` reste dans `.env`
pendant la validation finale. La persistance de `sha-2da27182` interviendra après smoke Production
PASS et décision CDO `PRODUCTION_DEPLOYED`.

Rollback applicatif disponible :
`LOYERTRACKER_TAG=sha-08b366fa docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx`
— **avec la réserve documentée au Gate Production `1.6.0`** : un rollback applicatif seul est
risqué si une inscription a eu lieu après ce déploiement (violation potentielle de la contrainte
`NOT NULL` posée par V19 sur `patrimoine.adresse` par l'ancien `InscriptionService`).

## 5. Application de l'adresse réelle du patrimoine (RP-160-05) — LEVÉE

**Écart qualifié et accepté par le PO** : le plan prévoyait `PUT /api/patrimoines/{id}` (endpoint
existant, validation applicative). L'action a été réalisée par **mise à jour SQL directe** sur
`patrimoine` au lieu de l'API, à la demande explicite du PO (session du 2026-07-02), faute d'un
jeton d'accès Bearer valide pour le compte bailleur réel `jptshilombo@gmail.com` — l'endpoint
exige `hasRole('BAILLEUR')` + vérification de propriété, non contournable sans authentification
réelle. Donnée strictement identique à celle prévue par le mapping documenté ; seule la voie
d'application diffère (SQL au lieu d'API), sans validation Bean Validation/Spring Security
exercée pour ce cas précis (accepté : les 6 champs sont des chaînes simples sans contrainte de
format autre que la longueur, déjà respectée).

```sql
UPDATE patrimoine
SET adresse = '5172, Avenue Kasamvu',
    quartier = 'Kasa-Vubu',
    commune = 'Bandalungwa',
    ville = 'Kinshasa',
    province_etat = 'Kinshasa',
    pays = 'République Démocratique du Congo'
WHERE id = 'd753e6d6-564e-4e6d-91c4-09a7c3265a91';
-- UPDATE 1
```

Vérification round-trip (`SELECT`) :

| id | nom | adresse | quartier | commune | ville | province_etat | pays |
|---|---|---|---|---|---|---|---|
| `d753e6d6-…` | Patrimoine principal | `5172, Avenue Kasamvu` | `Kasa-Vubu` | `Bandalungwa` | `Kinshasa` | `Kinshasa` | `République Démocratique du Congo` |

**RP-160-05 levée.** Vérification applicative complète (affichage dans le dashboard bailleur,
`GET /api/patrimoines/{id}` authentifié) recommandée en session utilisateur normale, en
complément de cette vérification SQL directe.

## 6. Verdict

| Critère | Statut |
|---|---|
| Pull `api` + `nginx` `sha-2da27182` sans erreur | ✅ PASS |
| Digests conformes au Gate Production `1.6.0` (vérifiés avant recréation) | ✅ PASS |
| Recréation ciblée `api`+`nginx` uniquement, `postgres`/`keycloak` inchangés | ✅ PASS |
| 4/4 services applicatifs `(healthy)`, restart=0 | ✅ PASS |
| Flyway : V19 appliquée, 19/19 `success`, aucune perte de données | ✅ PASS |
| Actuator UP, `/healthz` `ok` | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |
| CSP Nginx conforme | ✅ PASS |
| `.env` non modifié (persistance différée) | ✅ PASS |
| Adresse réelle patrimoine appliquée (RP-160-05, écart SQL qualifié) | ✅ PASS |

**Déploiement technique PASS. `PRODUCTION_DEPLOYED` non atteint — validation finale requise.**

Prochaine étape : validation finale `1.6.0` (smoke Production, `PRODUCTION_DEPLOYED`).
