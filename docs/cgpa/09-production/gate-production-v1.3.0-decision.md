# Gate Production — Release `1.3.0`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Type | Release MINOR — Sprint 4 UI Patrimoine |
| Version | `1.3.0` |
| Commit applicatif | `a42d860d5a10b80b85d5a94d79c3680ef06bacdc` |
| Tag candidat | `sha-a42d860d` |
| Digest GHCR API | `sha256:c3d89f0d6da5cfad55daa0ad921df4be0539757c8ca3384110323e7425290749` |
| Digest GHCR Web | `sha256:c30708984117717b8bad0b6447fd009b561680376c7d3a7d60ffa81e1ba8c4ba` |
| Production actuelle | `1.2.1` — `sha-47172297` |
| Rollback disponible | `sha-47172297` — applicatif seul, sans pg_restore |
| Décision | **GO sous réserve** |
| Statut | **`PRODUCTION_READY`** |

## 1. Objet

Statuer l'autorisation Production de la release `1.3.0`, composée des évolutions du Sprint 4
UI Patrimoine, de la remédiation d'audit CGPA v5.4.1 et du correctif É-01 (PR #82, #83, #96),
validés en Staging le 2026-06-27 sous le tag `sha-a42d860d`.

## 2. Périmètre

### Inclus

**PR #82 — Sprint 4 UI Patrimoine** (`7738a08` → merge, commits `0c25c33`..`55183b5`)
- `feat(s02)` : extension du modèle frontend `Affectation`/`AffectationPayload` et ajout de
  `listerAffectationsPatrimoine()` dans `s02-api.service.ts`.
- `feat(dashboard)` : signaux Angular, formulaires et méthodes pour créer/révoquer les
  affectations au niveau patrimoine et configurer les exceptions INCLUSION/EXCLUSION par bien.
- `feat(dashboard)` : template Sections A (affectation patrimoine) et B (exceptions fines),
  conditionnées à une affectation patrimoine ACTIVE alignée sur RS-04.
- `test(dashboard)` : couverture des nouveaux chemins Sprint 4.
- `docs(sprint-4)` : kickoff PO confirmé.

**PR #83 — Remédiation audit CGPA v5.4.1** (`e561d0e` → merge, commits `a8b3454`..`e5585bf`)
- Activation Dependabot et mise à jour Angular DevKit/CLI `20.3.30` + override
  `http-proxy-middleware 3.0.7` : `npm audit` passe de 3 High à **0 High/Critical**.
- Correction du clone superficiel Frontend CI (`fetch-depth: 0`) — restaure le blame SonarQube.
- `test(frontend)` : couverture confirmation archivage (`91da041`).
- `docs(cgpa)` : traçabilité CGPA v5.4.1 remédiation.

**PR #96 — Correctif É-01** (`a42d860d` → merge, commit `cdeff46`)
- `fix(affectations)` : ajout de `GET /api/patrimoines/{id}/affectations` (endpoint manquant,
  détecté en Staging lors de la phase E6 Sprint 4). Trois couches ajoutées :
  `AffectationRepository.findByPatrimoineIdOrderByDateDebutDesc()`,
  `AffectationService.historiquePatrimoine()`, `AffectationController` endpoint.
- `test` : `historiqueAffectationsPatrimoineScopeParBailleur` (scope multi-bailleur + 403
  bailleur tiers + 403 patrimoine inexistant via `peutAccederPatrimoine`).

### Exclus

- Commits documentaires postérieurs au candidat (`e086548` — PR #97 docs Gate Staging) :
  aucun changement applicatif.
- Aucune modification Keycloak, Compose, réseau ou volume.
- Aucune migration Flyway (V15 reste le rang maximal — identique à `1.2.1`).

## 3. Checklist Gate Production (CGPA v5.4.1)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Release identifié | ✅ | §2 ci-dessus — MINOR SemVer, nouvelles fonctionnalités UI + backend É-01 |
| Version SemVer identifiée | ✅ | `1.3.0` — MINOR (nouvelles fonctionnalités bailleur : UI affectation patrimoine, exceptions INCLUSION/EXCLUSION, endpoint `GET /api/patrimoines/{id}/affectations`) |
| Commit et artefact identifiés | ✅ | `a42d860d` applicatif ; tag `sha-a42d860d` ; digests immuables vérifiés |
| Environnement source | ✅ | Staging `ai-test-server` (`https://loyertracker.staging.loyerpro.org`) |
| Environnement cible | ✅ | Production `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ✅ | `sha-a42d860d` déployé le 2026-06-27 (déploiement W5 post-PR #96) |
| Services Staging | ✅ | 4/4 `(healthy)` ; restart count = 0 |
| Smoke Staging | ✅ | **47 PASS / 0 FAIL** (2026-06-27, runs sur `sha-a42d860d`) |
| Flyway Staging | ✅ | 15/15 (V1→V15) — aucune migration supplémentaire par rapport à `1.2.1` |
| `STG-ISOL-01` | ✅ **PASS live** | Deux déploiements vérifiés : `sha-e561d0e5` (2026-06-27, initial Sprint 4) et `sha-a42d860d` (2026-06-27, É-01 fix) — 8 conteneurs `loyertracker-staging-*` avant et après, `nginx-proxy-manager` et autres projets non affectés |
| Validation E6 (Sprint 4) | ✅ **PASS** | Section A (POST /api/affectations patrimoine → 201), É-01 (GET /api/patrimoines/{id}/affectations → 200, affectation visible), Section B (EXCLUSION par bien → 201) — toutes sections validées |
| Accumulation Staging | ✅ | Candidat figé à `sha-a42d860d` ; `e086548` est documentaire uniquement — aucun commit applicatif post-Gate Staging |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Sprint 4 UI validé PO | ✅ | Kickoff PO confirmé (2026-06-27, `58fcd1f`) ; périmètre PR #82 + #83 + #96 approuvé |
| Défaut É-01 levé | ✅ | Défaut critique détecté en Staging (E6 NO GO initial) ; corrigé PR #96 ; re-run E6 PASS |
| Release notes disponibles | ✅ | `docs/release-notes-v1.3.0.md` |
| Changelog disponible | ✅ | `CHANGELOG.md` section `[Non publié]` contient Sprint 4 UI et remédiation audit — à promouvoir en `[1.3.0]` après `PRODUCTION_DEPLOYED` |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | `sha-a42d860d` — SUCCESS toutes jobs : Backend, Sécurité, Frontend, Packaging Docker, CodeQL |
| Tests backend | ✅ | **101/101 PASS** (0 Failures, 0 Errors, 0 Skipped) — dont `historiqueAffectationsPatrimoineScopeParBailleur` (É-01) ; `mvn verify` SUCCESS |
| Tests frontend | ✅ | `ng test` SUCCESS dans CI `sha-a42d860d` ; couverture dashboard Sprint 4 et archivage ajoutée |
| Gitleaks / SCA / Trivy | ✅ | Job Sécurité SUCCESS dans CI `sha-a42d860d` — 0 High/Critical (`npm audit` résidu : 5 Moderate, 5 Low dans la chaîne build uniquement) |
| SonarQube | ✅ | Analyse SonarQube BUILD SUCCESS dans CI `sha-a42d860d` ; blame Frontend restauré (PR #83 `c6ac2ae`) |
| Migrations Production | ✅ | **Aucune** — Flyway reste à V15 ; aucun script V16+ entre `1.2.1` et `1.3.0` |
| Observabilité | ✅ | Dispositif Production existant inchangé (5 cibles Prometheus, Alertmanager, Pushgateway) |
| Secrets | ✅ | Aucun secret versionné ; `.env` hors dépôt ; images GHCR privées ; digests immuables confirmés |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback applicatif | ✅ | `sha-47172297` (Production `1.2.1`) |
| Digests rollback | ✅ | API `sha256:eb6e362b…`, Web `sha256:ce956419…` (cf. `deploiement-technique-v1.2.1-report.md`) |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager |
| Migration V16+ et rollback | ✅ | **Aucune migration supplémentaire** entre `1.2.1` et `1.3.0` — rollback applicatif seul suffisant, sans pg_restore |
| Rollback Production | ✅ | `sha-47172297` disponible sur GHCR ; procédure : `LOYERTRACKER_TAG=sha-47172297 docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx` |
| RP-120-02 | Maintenue | Rollback au-delà de `1.2.0` (vers `1.1.x`) toujours non trivial (pg_restore V15) — inchangé ; sans impact sur ce déploiement |

## 4. Réserves et conditions

| ID | Nature | Traitement |
|----|--------|------------|
| RP-130-01 | Backup Production non encore créé | **Bloquant** — levé uniquement après `pg_dump -Fc` + `pg_restore --list` OK en Préflight |
| RP-120-02 | Rollback schéma V15 au-delà de `1.2.0` non trivial | Maintenue — sans impact sur ce déploiement (aucune migration ajoutée) |

## 5. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — traçabilité Staging complète (x2 STG-ISOL-01 PASS, E6 PASS), É-01 levé en Staging, réserve RP-130-01 explicite |
| Enterprise Architect | **GO** — backend (1 endpoint GET read-only) + frontend (UI affectation patrimoine), aucune migration, rollback trivial (`sha-47172297` sans pg_restore), RLS/Keycloak/Compose inchangés |
| DevSecOps Lead | **GO sous réserve** — CI SUCCESS 101/101, SonarQube vert, 0 High/Critical sécurité, backup pré-déploiement obligatoire (RP-130-01) |
| Release Manager | **GO sous réserve** — candidat recevable `sha-a42d860d`, preuves Staging complètes (E6 PASS + smoke 47/0), condition backup maintenue |
| Product Owner | **GO** — Sprint 4 UI Patrimoine validé ; É-01 corrigé en Staging avant promotion Production |

## 6. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint** pour la release `1.3.0`.
- `PRODUCTION_DEPLOYED` : non atteint — déploiement à exécuter selon le plan
  Préflight → Déploiement → Validation finale → Hypercare.

### Condition bloquante avant déploiement

**RP-130-01** — backup Production vérifié (`pg_dump -Fc` + `pg_restore --list` OK).
Aucune autre condition.

### Services cibles du déploiement

**`api` et `nginx`** — le backend reçoit un nouvel endpoint (`GET /api/patrimoines/{id}/affectations`)
et le frontend reçoit la nouvelle UI Sprint 4 (Sections A et B). Postgres, Keycloak et le
monitoring sont inchangés.

### Prochaines étapes

| Étape | Document à créer |
|---|---|
| Préflight + backup Production | `preflight-backup-v1.3.0-report.md` |
| Déploiement technique (`api` + `nginx`) | `deploiement-technique-v1.3.0-report.md` |
| Validation finale + smoke 47/0 | `validation-finale-v1.3.0-report.md` |
| Hypercare + clôture | `plan-etape-hypercare-v1.3.0.md` / `cloture-release-v1.3.0.md` |
| CHANGELOG — promouvoir `[Non publié]` → `[1.3.0]` | Après `PRODUCTION_DEPLOYED` |
