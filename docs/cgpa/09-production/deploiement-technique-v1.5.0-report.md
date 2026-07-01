# Rapport Déploiement Technique — Release `1.5.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-01 |
| Heure UTC | 09:38–09:40 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.5.0` |
| Tag déployé | **`sha-08b366fa`** |
| Tag précédent | `sha-98afa99a` (`1.4.0`) |
| Verdict | **PASS technique — avec écart constaté (§1.1), sans impact** |

## 1. Exécution

### Synchronisation dépôt + pull des images (09:38 UTC)

```
cd ~/loyertracker
git pull --ff-only origin main          # a40a8ad → 366f5b6 (fast-forward)
LOYERTRACKER_TAG=sha-08b366fa docker compose -f docker-compose.yml -f docker-compose.prod.yml pull api nginx
```

`api Pulled` et `nginx Pulled` — layers GHCR téléchargés sans erreur.

### Recréation ciblée (09:38 UTC)

```
LOYERTRACKER_TAG=sha-08b366fa docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
```

| Conteneur | Action |
|---|---|
| `loyertracker-postgres-1` | `Recreate` → `Recreated` → `Started` → `Healthy` |
| `loyertracker-keycloak-1` | `Recreate` → `Recreated` → `Started` → `Healthy` |
| `loyertracker-api-1` | `Recreate` → `Recreated` → `Started` → `Healthy` |
| `loyertracker-nginx-1` | `Recreate` → `Recreated` → `Started` → `Healthy` |

### 1.1 Écart constaté — recréation de `postgres`/`keycloak` non ciblés

Contrairement au plan (services cibles = `api` + `nginx` uniquement, cf. Gate Production
`1.5.0` §6) et au comportement observé lors des déploiements précédents (`1.2.1`, `1.3.0`,
`1.4.0` : `postgres`/`keycloak` systématiquement rapportés `Running — inchangé`), Docker
Compose a recréé **les 4 conteneurs de la chaîne de dépendance**, y compris `postgres` et
`keycloak`, bien que la commande n'ait explicitement ciblé que `api nginx`.

**Investigation :**
- `docker-compose.yml` / `docker-compose.prod.yml` : **inchangés** entre le commit précédemment
  déployé (`a40a8ad`) et le candidat `08b366f` (confirmé par `git log` sur ces deux fichiers —
  dernière modification antérieure, commit `5d58ff9`).
- `.env` : **inchangé** depuis le déploiement `1.4.0` (mtime `2026-06-30 16:15:48`, antérieur à
  ce déploiement).
- Cause la plus probable : divergence du hash de configuration calculé par Docker Compose entre
  deux invocations (comportement connu lors d'un changement de version du binaire `docker
  compose` sur l'hôte, sans changement de configuration côté dépôt) — non confirmée avec
  certitude, aucun changement de configuration applicatif identifié.

**Impact réel : aucun.**
- Volume `loyertracker_postgres-data` : **inchangé**, créé le `2026-06-20T12:09:03+01:00` —
  les données ont survécu à la recréation du conteneur (volume nommé, non supprimé).
- Données vérifiées présentes après recréation : 3 bailleurs, 4 biens, 4 baux (cohérent avec
  l'exploitation réelle, aucune perte).
- Keycloak : realm et comptes persistés en base PostgreSQL (partagée), donc inchangés malgré la
  recréation du conteneur applicatif.
- Tous les services `(healthy)`, restart count = 0 après stabilisation.

**Non bloquant pour ce Gate** — traité comme observation à surveiller lors du prochain
déploiement (si la recréation complète se reproduit, investiguer la version du binaire
`docker compose` sur l'hôte).

## 2. Contrôles post-déploiement

### Services (09:40 UTC, après stabilisation)

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

### Digests déployés

| Image | Digest GHCR |
|---|---|
| `loyertracker-api:sha-08b366fa` | `sha256:865dd686f76c90d514a26056ed7d6ad248ad5dd6c46d8776e88c68a144d80520` |
| `loyertracker-web:sha-08b366fa` | `sha256:a7c74954700f300da1e5b40f104087da4c3bb629f0269aba0c1703b07d612b3e` |

Conformes aux digests du Gate Production `1.5.0`.

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **18/18** (V1→V18) — aucune migration supplémentaire |
| Données applicatives | 3 bailleurs, 4 biens, 4 baux — présentes, aucune perte |

Comportement attendu : `1.5.0` n'introduit aucune migration, Flyway reste à V18.

### Actuator et observabilité

| Contrôle | Résultat |
|---|---|
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus cibles `up` | **5/5** |
| Alertmanager alertes actives | **0** (`[]`) |
| CSP Nginx (US-72) | ✅ PASS — `content-security-policy` observé conforme (`script-src 'self'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`) |

## 3. État `.env`

**`.env` non modifié à ce stade.** Le tag `LOYERTRACKER_TAG=sha-98afa99a` reste dans `.env`
pendant la validation finale. La persistance de `sha-08b366fa` interviendra après smoke Production
PASS et décision CDO `PRODUCTION_DEPLOYED`.

Rollback applicatif disponible :
`LOYERTRACKER_TAG=sha-98afa99a docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx`

## 4. Verdict

| Critère | Statut |
|---|---|
| Pull `api` + `nginx` `sha-08b366fa` sans erreur | ✅ PASS |
| 4/4 services applicatifs `(healthy)`, restart=0 | ✅ PASS |
| Digests conformes au Gate Production | ✅ PASS |
| Flyway 18/18, aucune migration supplémentaire, données intactes | ✅ PASS |
| Actuator UP | ✅ PASS |
| Prometheus 5/5, Alertmanager 0 alerte | ✅ PASS |
| CSP Nginx conforme | ✅ PASS |
| Écart : recréation non ciblée de `postgres`/`keycloak` | ⚠️ Constaté, sans impact (§1.1) |

**Déploiement technique PASS. `PRODUCTION_DEPLOYED` non atteint — validation finale requise.**

Prochaine étape : validation finale `1.5.0` (smoke Production, vérification comportementale
RGPD, persistance `.env`, `PRODUCTION_DEPLOYED`).
