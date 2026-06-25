# Gate Staging v5.3 — Lot CORS Compose + Sprint 3 Patrimoine

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-25 |
| Périmètre | Correctif CORS Compose + Sprint 3 Patrimoine (US-85, V15, RS-04) |
| Référence | Commits `964ebfb` (CORS), `1c06085` (PR #81 Sprint 3), `8c79e3d` (smoke V15), `b1d8cf7`, `5bf187af` (docs) |
| Commit HEAD candidat | `5bf187af` (`origin/main`) |
| Tag GHCR candidat | `sha-5bf187af` (publié par CI post-push) |
| Tag Staging précédent / rollback | `sha-73359c5c` (Sprint 2, V13 — déployé le 2026-06-16) |
| Environnement | Staging `https://loyertracker.staging.loyerpro.org` (`ai-test-server`) |
| Décision | **GO** — smoke 47/0, CORS vars injectées, V15 appliquée, RSV-STG-01 levée |
| Statut CGPA v5.3 | `STAGING_DEPLOYED` ✅ (2026-06-25) |

## 1. Objet du Gate

Ce Gate Staging v5.3 statue le passage Staging du lot combiné CORS + Sprint 3, sans déclencher
de Production. Les deux composantes sont inséparables car elles coexistent sur `main`.

**Périmètre inclus :**

- **Correctif CORS Compose** (`964ebfb`) : câblage de `APP_CORS_ALLOWED_ORIGIN` et
  `APP_INVITATION_BASE_URL` au service `api` dans `docker-compose.yml` et
  `docker-compose.staging.yml` — Spring utilisait le fallback `https://localhost` depuis
  l'exposition publique du 2026-06-16.
- **Sprint 3 Patrimoine — US-85** (`1c06085`, PR #81) : exceptions fines `INCLUSION`/`EXCLUSION`
  par bien, migration **V15** (`affectation.type_exception`, backfill, réécriture à priorité de
  `gestionnaire_affecte_actif`/`biens_affectes_gestionnaire`, correctif `calculer_honoraires`),
  garde **RS-04** (EXCLUSION orpheline rejetée 400).
- **Correctif smoke** (`8c79e3d`) : alignement compteur Flyway V14→V15.
- **Documentation** (`b1d8cf7`, `5bf187af`) : `project-state.md`, `CHANGELOG.md`.

**Hors périmètre :**

- UX/frontend des exceptions INCLUSION/EXCLUSION (différée, backend-only confirmé par le PO).
- Promotion Production.

## 2. Conditions d'entrée

| Critère | Statut | Preuve |
|---------|--------|--------|
| Sprint identifié | OK | Sprint 3 Patrimoine + lot correctif CORS |
| Plan d'Exécution approuvé | OK | `plan-correctif-cors-compose.md` (CORS) ; `sprint-3-patrimoine-rapport-validation.md` (Sprint 3) |
| Rapport d'exécution disponible | OK | Validation locale Sprint 3 : 99 tests / 0 échec, Gitleaks, Trivy SCA 0 HIGH/CRITICAL |
| Commit / artefact candidat identifié | OK | `5bf187af` HEAD `origin/main` ; tag GHCR `sha-5bf187af` |
| Environnement Staging identifié | OK | `ai-test-server`, `docs/staging-state.md` |

## 3. Contrôles Sprint

| Critère | Statut | Preuve |
|---------|--------|--------|
| Stories terminées listées | OK | US-85 (INCLUSION/EXCLUSION, V15, RS-04) ; correctif CORS |
| Stories exclues ou reportées listées | OK | Frontend exceptions UI différée (backend-only, PO 2026-06-24) |
| Écarts au plan acceptés | OK | Écart PR CORS (I-03) : direct push sans PR, qualifié Niveau 1 accepté (`project-state.md` §11) |
| Validation Product Owner | OK | Kickoff Sprint 3 GO PO 2026-06-25 ; CORS lot autorisé CDO post-hypercare |
| Validation Release Manager | OK | Staging uniquement ; Production explicitement exclue de ce Gate |

## 4. Contrôles DevSecOps

| Critère | Statut | Preuve |
|---------|--------|--------|
| Build stable | OK | CI `5bf187af` : CodeQL ✅ + pipeline complet ✅ (confirmé) |
| Tests unitaires | OK | `mvn verify` 99 tests / 0 échec (validation locale Sprint 3) |
| Tests d'intégration | OK | 4/4 combinaisons RM-98 testées, RS-04/visibilité 2 tests HTTP |
| Frontend lint/build/tests | OK | `ng lint`/`ng build`/`ng test` 41 tests (base antérieure, pas de modif frontend Sprint 3) |
| Secrets / SCA / images | OK | Gitleaks 168 commits / no leaks, Trivy SCA 0 HIGH/CRITICAL |
| SonarQube | OK | Quality Gate backend verte PR #81 (`new_violations` 0, `new_coverage` 93,1 %) |
| Migrations DB | OK | V15 (`V15__affectations_exceptions.sql`) validée localement ; smoke script aligné V15 |
| Secrets non exposés | OK | Aucun secret versionné ; `.env` hors dépôt |

## 5. Gate STG-ISOL-01 (CGPA v5.4.1)

> Checklist complète : `docs/cgpa/checklists/stg-isol-01-checklist.md`

| Critère | Statut | Preuve |
|---------|--------|--------|
| Gate `STG-ISOL-01` documentaire | PASS | `docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md` (2026-06-24) |
| Namespace Compose explicite et unique | PASS | `loyertracker-staging` (confirmé `docker compose ps`) |
| Réseaux / volumes namespacés | PASS | `loyertracker-staging_loyertracker-net`, `loyertracker-staging_postgres-data` |
| Absence de conflit de ports | PASS | Ports `18080`/`18443` ; services internes non publiés (healthy) |
| Reverse proxy mutualisé par nom DNS | PASS | nginx-proxy-manager Proxy Host #18 → `loyertracker.staging.loyerpro.org` |
| Absence de commande Docker globale | PASS | Déploiement ciblé `api` + `nginx` uniquement |
| Conteneurs des autres projets inchangés | PASS | Avant (session précédente) : 8 `loyertracker-staging-*` ; après : 8 `loyertracker-staging-*` — aucun autre projet |
| **RSV-STG-01** | **LEVÉE** | Preuve live 2026-06-25 : 8 conteneurs `loyertracker-staging-*` avant et après — isolation confirmée |

## 6. Déploiement Staging

> Commandes à exécuter sur `ai-test-server` (IP privée `172.31.11.102`).

```bash
# 1. Synchroniser le dépôt
cd ~/loyertracker
git pull origin main        # → HEAD 5bf187af

# 2. STG-ISOL-01 — avant (capturer l'état des autres projets)
docker ps --format "table {{.Names}}\t{{.Status}}" > /tmp/stg-before.txt

# 3. Déployer avec le tag candidat
LOYERTRACKER_TAG=sha-5bf187af \
  docker compose -f docker-compose.staging.yml pull api nginx
LOYERTRACKER_TAG=sha-5bf187af \
  docker compose -f docker-compose.staging.yml up -d api nginx

# 4. Vérifier la santé
docker compose -f docker-compose.staging.yml ps

# 5. Vérifier les vars CORS dans le conteneur
docker compose -f docker-compose.staging.yml exec api \
  env | grep -E "APP_CORS|APP_INVITATION"

# 6. STG-ISOL-01 — après (vérifier que les autres projets sont inchangés)
docker ps --format "table {{.Names}}\t{{.Status}}" > /tmp/stg-after.txt
diff /tmp/stg-before.txt /tmp/stg-after.txt

# 7. Smoke test (V15 attendu)
BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.staging.yml ./infra/smoke/smoke-stack.sh
```

## 7. Avis des sous-agents

| Sous-agent | Avis |
|------------|------|
| Governance Officer | GO : plans approuvés, écart PR I-03 qualifié et tracé, STG-ISOL-01 documentaire PASS |
| Enterprise Architect | GO : CORS câblage Compose pur, V15 additive non-destructive (backfill INCLUSION idempotent), RLS maintenu |
| DevSecOps Lead | GO sous réserve CI verte : CI `5bf187af` en cours, SonarQube PR #81 verte ; smoke V15 prêt |
| Release Manager | GO Staging uniquement : aucune autorisation Production par ce Gate |

## 8. Décision

**Verdict : GO** — 2026-06-25

Preuves :
- CI `5bf187af` : CodeQL ✅ + pipeline ✅ (confirmé avant déploiement).
- Smoke 47 PASS / 0 FAIL : V15 (15 migrations Flyway), CORS vars injectées (`APP_CORS_ALLOWED_ORIGIN=https://loyertracker.staging.loyerpro.org`), parcours métier complet, isolation cross-tenant.
- CORS vars vérifiées dans le conteneur via `docker compose exec api env | grep APP_CORS`.
- STG-ISOL-01 : 8 conteneurs `loyertracker-staging-*` avant et après — aucun autre projet affecté.

Statuts :

- `STAGING_READY` : ✅ atteint.
- `STAGING_DEPLOYED` : ✅ atteint (2026-06-25, `sha-5bf187af`).
- `RSV-STG-01` : ✅ levée (preuve live 2026-06-25).
- `PRODUCTION_READY` / `PRODUCTION_DEPLOYED` : non atteints — Gate Production distinct requis.

## 9. Réserves et suite

- Production non autorisée par ce Gate.
- Prochaine release à piloter par un Gate Production explicite.
- UX exceptions INCLUSION/EXCLUSION différée à un lot ultérieur (backend-only confirmé).
