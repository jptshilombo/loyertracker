# Gate Production — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Type | Release PATCH — correctif frontend |
| Version | `1.2.1` |
| Commit applicatif | `c1e9c735e39c0375b907be9da3302e67f5cb10d4` |
| Tag candidat | `sha-47172297` |
| Digest GHCR API | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Digest GHCR Web | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |
| Production actuelle | `1.2.0` — `sha-5bf187af` |
| Rollback disponible | `sha-5bf187af` — applicatif seul, sans pg_restore |
| Décision | **GO sous réserve** |
| Statut | **`PRODUCTION_READY`** |

## 1. Objet

Statuer l'autorisation Production de la release `1.2.1`, composée uniquement du correctif
Angular `c1e9c73` (`fix(dashboard): charge les biens même si l'inscription échoue`), validé
en Staging le 2026-06-27 sous le tag `sha-47172297`.

## 2. Périmètre

### Inclus

- **`c1e9c73`** — correctif `dashboard.component.ts` (+5/-3 lignes) : `chargerBiens()` déclenché
  via `finalize` (succès et erreur) ; `chargerReferentielsBien()` lancé en parallèle de
  l'inscription. Solde la réserve **RP-120-03** ouverte à la clôture de `1.2.0`.

### Exclus

- Aucun autre changement applicatif — tous les commits entre `5bf187af` et `47172297` sont
  documentaires (`docs/cgpa/09-production/`, `docs/project-state.md`, etc.).

## 3. Checklist Gate Production (CGPA v5.3)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Release identifié | ✅ | §2 ci-dessus — PATCH SemVer, correctif pur |
| Version SemVer identifiée | ✅ | `1.2.1` — PATCH (correctif comportemental, aucune fonctionnalité nouvelle) |
| Commit et artefact identifiés | ✅ | `c1e9c73` applicatif ; tag `sha-47172297` ; digests immuables vérifiés |
| Environnement source | ✅ | Staging `ai-test-server` (`https://loyertracker.staging.loyerpro.org`) |
| Environnement cible | ✅ | Production `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ✅ | `sha-47172297` déployé le 2026-06-27 |
| Services Staging | ✅ | 4/4 `(healthy)` ; restart count = 0 |
| Smoke Staging | ✅ | **47 PASS / 0 FAIL** (2026-06-27 08:44 UTC) |
| Flyway Staging | ✅ | 15/15 (V1→V15) — aucune migration supplémentaire |
| `STG-ISOL-01` | ✅ **PASS live** | 8 conteneurs `loyertracker-staging-*` avant et après — aucun autre projet affecté (2026-06-27) |
| Accumulation Staging | ✅ | Candidat figé à `sha-47172297` ; aucun commit applicatif post-Gate Staging |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Correctif validé et validé PO | ✅ | Plan d'exécution `1.2.1` approuvé PO 2026-06-27 ; périmètre `c1e9c73` confirmé |
| Release notes disponibles | ✅ | `docs/release-notes-v1.2.1.md` |
| Changelog disponible | ✅ | `CHANGELOG.md` section `[1.2.1]` prête sous `[Non publié]` |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | HEAD `47172297` — SUCCESS toutes jobs (Backend, Frontend, Sécurité, CodeQL, Packaging) |
| Tests unitaires et intégration | ✅ | `ng test` inclus dans CI SUCCESS ; comportement `finalize` couvert |
| Gitleaks / SCA / Trivy | ✅ | SUCCESS dans CI `47172297` |
| SonarQube | ✅ | Quality Gate verte (aucun code backend modifié ; frontend — 0 nouvelle violation) |
| Migrations Production | ✅ | **Aucune** — Flyway reste à V15 (rang max inchangé entre `1.2.0` et `1.2.1`) |
| Observabilité | ✅ | Dispositif Production existant inchangé (5 cibles Prometheus, Alertmanager, Pushgateway) |
| Secrets | ✅ | Aucun secret versionné ; `.env` hors dépôt ; images GHCR privées |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback applicatif | ✅ | `sha-5bf187af` (Production `1.2.0`) |
| Digests rollback | ✅ | API `sha256:3e511356…`, Web `sha256:36493866…` (cf. `deploiement-technique-v1.2.0-report.md`) |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager |
| Migration V16+ et rollback | ✅ | **Aucune migration supplémentaire** entre `1.2.0` et `1.2.1` — rollback applicatif seul suffisant |
| Rollback Production | ✅ | `sha-5bf187af` disponible sur GHCR ; procédure : `LOYERTRACKER_TAG=sha-5bf187af docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d nginx api` |
| RP-120-02 | Maintenue | Rollback au-delà de `1.2.0` (vers `1.1.x`) toujours non trivial (pg_restore V15) — inchangé |

## 4. Réserves et conditions

| ID | Nature | Traitement |
|----|--------|------------|
| RP-121-01 | Backup Production non encore créé | **Bloquant** — levé uniquement après `pg_dump` + `pg_restore --list` OK en Étape 4 (Préflight) |
| RP-120-02 | Rollback schéma V15 au-delà de `1.2.0` non trivial | Maintenue — sans impact sur ce déploiement (aucune migration) |
| RP-120-03 | `c1e9c73` exclu de `1.2.0` | **Levée après `PRODUCTION_DEPLOYED` `1.2.1`** |

## 5. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — traçabilité Staging complète, STG-ISOL-01 PASS live, réserve RP-121-01 explicite |
| Enterprise Architect | **GO** — correctif frontend pur, aucune migration, rollback trivial (`sha-5bf187af` sans pg_restore), RLS/Keycloak/Compose inchangés |
| DevSecOps Lead | **GO sous réserve** — CI SUCCESS, SonarQube vert, backup pré-déploiement obligatoire (RP-121-01) |
| Release Manager | **GO sous réserve** — candidat recevable, preuves Staging complètes, condition backup maintenue |
| Product Owner | **GO** — correctif dashboard validé ; périmètre `c1e9c73` uniquement confirmé |

## 6. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint** pour la release `1.2.1`.
- `PRODUCTION_DEPLOYED` : non atteint — déploiement à exécuter selon le plan
  Préflight → Déploiement → Validation finale → Hypercare.

### Condition bloquante avant déploiement

**RP-121-01** — backup Production vérifié (`pg_dump -Fc` + `pg_restore --list` OK).
Aucune autre condition : pas de confirmation de digests à la pull (rollback trivial),
déploiement ciblé `nginx` seul.

### Service cible du déploiement

**`nginx` uniquement** — le service `api` reste sur `sha-5bf187af` (aucun changement backend
entre `1.2.0` et `1.2.1`). Postgres, Keycloak et le monitoring sont inchangés.

### Prochaines étapes

| Étape | Document à créer |
|---|---|
| Préflight + backup Production | `preflight-backup-v1.2.1-report.md` |
| Déploiement technique (`nginx` seul) | `deploiement-technique-v1.2.1-report.md` |
| Validation finale + smoke 47/0 | `validation-finale-v1.2.1-report.md` |
| Hypercare + clôture | `plan-etape-hypercare-v1.2.1.md` / `cloture-release-v1.2.1.md` |
| CHANGELOG — promouvoir `[Non publié]` → `[1.2.1]` | Après `PRODUCTION_DEPLOYED` |
