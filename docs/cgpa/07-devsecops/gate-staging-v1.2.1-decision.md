# Gate Staging — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Type | Release PATCH — correctif frontend |
| Version | `1.2.1` |
| Tag déployé | `sha-47172297` |
| Digest API | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Digest Web | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |
| Environnement | `ai-test-server` — `https://loyertracker.staging.loyerpro.org` |
| Décision | **GO — `STAGING_DEPLOYED`** |
| Dossier candidat | `docs/cgpa/09-production/release-candidate-v1.2.1.md` |

## 1. Périmètre

Correctif Angular `c1e9c73` : `dashboard.component.ts` (+5/-3 lignes, opérateur `finalize`).
Aucun changement backend, aucune migration, aucun changement Compose.

## 2. Contrôle STG-ISOL-01

### État avant déploiement (08:27 UTC)

| Conteneurs `loyertracker-staging-*` | État |
|---|---|
| `loyertracker-staging-nginx-1` | Up (healthy) — `sha-5bf187af` |
| `loyertracker-staging-api-1` | Up (healthy) — `sha-5bf187af` |
| `loyertracker-staging-postgres-1` | Up (healthy) |
| `loyertracker-staging-keycloak-1` | Up (healthy) |
| `loyertracker-staging-alertmanager-1` | Up |
| `loyertracker-staging-pushgateway-1` | Up |
| `loyertracker-staging-prometheus-1` | Up |
| `loyertracker-staging-blackbox-1` | Up |

Autres projets sur l'hôte : `nginx-proxy-manager` (reverse proxy mutualisé — ressource partagée
inventoriée `docs/staging-state.md` §11).

### Déploiement exécuté (08:29–08:43 UTC)

```
cd /home/ubuntu/loyertracker
git pull --ff-only origin main          # 5bf187a → 47172297 (fast-forward)
LOYERTRACKER_TAG=sha-47172297 \
  docker compose -f docker-compose.staging.yml up -d --pull always
```

Aucune commande Docker globale. Pull ciblé sur les images `loyertracker-api:sha-47172297`
et `loyertracker-web:sha-47172297` uniquement.

### État après déploiement (08:45 UTC)

| Conteneurs `loyertracker-staging-*` | État |
|---|---|
| `loyertracker-staging-nginx-1` | Up (healthy) — `sha-47172297` |
| `loyertracker-staging-api-1` | Up (healthy) — `sha-47172297` |
| `loyertracker-staging-postgres-1` | Up (healthy) |
| `loyertracker-staging-keycloak-1` | Up (healthy) |
| `loyertracker-staging-alertmanager-1` | Up (inchangé) |
| `loyertracker-staging-pushgateway-1` | Up (inchangé) |
| `loyertracker-staging-prometheus-1` | Up (inchangé) |
| `loyertracker-staging-blackbox-1` | Up (inchangé) |

`loyertracker-staging-keycloak-init-1` : conteneur d'initialisation du realm, exécuté et terminé
normalement (comportement attendu à chaque déploiement).

**Verdict STG-ISOL-01 : PASS** — 8 conteneurs `loyertracker-staging-*` avant et après ; aucun
autre projet affecté ; `nginx-proxy-manager` inchangé.

## 3. Contrôles post-déploiement

| Contrôle | Résultat |
|---|---|
| 4/4 services applicatifs `(healthy)` | ✅ PASS — nginx, api, postgres, keycloak |
| Restart count = 0 | ✅ PASS — api=0, nginx=0, postgres=0, keycloak=0 |
| Tag `sha-47172297` actif | ✅ PASS — API et Web |
| Digests GHCR conformes au dossier candidat | ✅ PASS — API `sha256:eb6e362b…`, Web `sha256:ce956419…` |
| Flyway 15/15 (V1→V15) | ✅ PASS — 15 migrations success, rang max V15 |
| `APP_CORS_ALLOWED_ORIGIN` injecté dans api | ✅ PASS — `https://loyertracker.staging.loyerpro.org` |
| Actuator `{"status":"UP"}` | ✅ PASS |

## 4. Smoke test

```
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.staging.yml bash infra/smoke/smoke-stack.sh
```

**Résultat : 47 PASS / 0 FAIL** (2026-06-27 08:44 UTC)

| Section | PASS |
|---|---|
| 0. Sanity (stack, Flyway V1-V15, pool `loyertracker_api` NOBYPASSRLS) | 5/5 |
| 1. JWT Keycloak réel via Nginx TLS | 2/2 |
| 2. Parcours bailleur (inscription 409, patrimoine, bien, bail) | 4/4 |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | 4/4 |
| 4. Affectation patrimoine, échéances SECURITY DEFINER, pointage, honoraires | 9/9 |
| 5. Alertes (PREAVIS à J+75), audit bailleur | 6/6 |
| 6. Scoping gestionnaire | 4/4 |
| 7. Isolation cross-tenant live (2e bailleur) | 9/9 |
| 8. Garde-fous AuthN/ports | 4/4 |

Note sur le port : le smoke a été exécuté sur `https://localhost:18443` (port interne) car le
reverse proxy public `https://loyertracker.staging.loyerpro.org` applique une Access List
`staging` (basic-auth) — comportement conforme et inchangé depuis l'exposition publique.

## 5. Vérification comportementale `c1e9c73`

Le smoke §2 (`POST /api/bailleurs/inscription → 409`) confirme le scénario corrigé : le bailleur
de test est déjà inscrit, l'API retourne 409. Avec `c1e9c73`, `chargerBiens()` s'exécute via
`finalize` même en cas d'erreur → le dashboard charge les biens. Ce comportement est garanti par
l'opérateur RxJS `finalize` intégré au bundle Angular compilé dans l'image `sha-47172297`,
et validé par la CI (`ng test` SUCCESS, run `47172297`).

## 6. Checklist Gate Staging v5.3

| Critère | Statut |
|---|---|
| Plan d'Exécution approuvé | ✅ `plan-execution-v1.2.1.md` — approuvé PO 2026-06-27 |
| Commit/artefact candidat identifié | ✅ `sha-47172297`, digests immuables vérifiés |
| Build CI stable | ✅ SUCCESS toutes jobs (Backend, Frontend, Sécurité, CodeQL, Packaging) |
| Tests unitaires | ✅ `ng test` inclus dans CI SUCCESS |
| Migrations DB | ✅ Aucune nouvelle migration — V15 rang max inchangé |
| Secrets non exposés | ✅ `.env` hors dépôt ; aucun secret dans les images |
| Rollback Staging identifié | ✅ `sha-5bf187af` (retour immédiat sans pg_restore) |
| STG-ISOL-01 | ✅ **PASS live** (2026-06-27) |
| Smoke Staging | ✅ **47 PASS / 0 FAIL** |
| `docs/staging-state.md` | À mettre à jour — §8 redéploiements |

## 7. Décision

**GO — `STAGING_DEPLOYED` atteint le 2026-06-27.**

- `PRODUCTION_READY` : non atteint — Gate Production distinct requis.
- `PRODUCTION_DEPLOYED` : non atteint.

**Prochaine étape autorisée :** Gate Production `1.2.1` (Étape 3 du plan d'exécution),
sous décision CDO/Release Manager distincte.
