# Clôture Release `1.3.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-06-29 |
| Heure CDO GO | 14:29 UTC (après T+24 anticipé PASS) |
| Release | `1.3.0` |
| Tag Production | `sha-a42d860d` |
| Commit applicatif | `a42d860d5a10b80b85d5a94d79c3680ef06bacdc` |
| `PRODUCTION_DEPLOYED` | 2026-06-29 14:31 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.3.0`

| Étape | Date | Résultat | Document |
|---|---|---|---|
| Gate Staging Sprint 4 | 2026-06-27 | GO — `STAGING_DEPLOYED` ; STG-ISOL-01 PASS live (×2) ; E6 PASS ; smoke 47/0 | `gate-staging-sprint4-v5.3-decision.md` |
| Gate Production | 2026-06-27 | GO sous réserve — `PRODUCTION_READY` ; RP-130-01 bloquante | `gate-production-v1.3.0-decision.md` |
| Préflight + backup | 2026-06-29 | PASS — backup 314 Kio, 730 entrées, RP-130-01 levée | `preflight-backup-v1.3.0-report.md` |
| Déploiement technique | 2026-06-29 15:11 UTC | PASS — `sha-a42d860d` actif, 8/8 Up, digests conformes | `deploiement-technique-v1.3.0-report.md` |
| Validation finale | 2026-06-29 14:31 UTC | `PRODUCTION_DEPLOYED` — smoke 47/0, nettoyage complet | `validation-finale-v1.3.0-report.md` |
| Hypercare T0 | 2026-06-29 14:31 UTC | PASS | `plan-etape-hypercare-v1.3.0.md` |
| Hypercare T+12 anticipé | 2026-06-29 14:27 UTC | PASS | `plan-etape-hypercare-v1.3.0.md` |
| Hypercare T+24 anticipé | 2026-06-29 14:29 UTC | PASS | `plan-etape-hypercare-v1.3.0.md` |

## 2. Périmètre livré

**PR #82 — Sprint 4 UI Patrimoine**
- Interface bailleur pour créer, afficher et révoquer les affectations au niveau patrimoine (Section A).
- Interface d'exceptions fines `INCLUSION`/`EXCLUSION` par bien, conditionnée à une affectation patrimoine active et alignée sur RS-04 (Section B).
- Modèle frontend étendu (`Affectation`, `AffectationPayload`, `listerAffectationsPatrimoine()`).
- Tests dashboard Sprint 4 ajoutés.

**PR #83 — Remédiation audit CGPA v5.4.1**
- Activation Dependabot + mise à jour Angular DevKit/CLI `20.3.30` + override `http-proxy-middleware 3.0.7` : `npm audit` passe de 3 High à **0 High/Critical**.
- Correction clone superficiel Frontend CI (`fetch-depth: 0`) — blame SonarQube restauré.
- Couverture confirmation archivage ajoutée.

**PR #96 — Correctif É-01**
- `GET /api/patrimoines/{id}/affectations` : endpoint backend manquant, détecté en Staging lors de E6 et corrigé avant promotion Production.
- `AffectationRepository.findByPatrimoineIdOrderByDateDebutDesc()`, `AffectationService.historiquePatrimoine()`, `AffectationController`.
- Test de scope multi-bailleur + 403 (`historiqueAffectationsPatrimoineScopeParBailleur`).

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| **RP-130-01** | Backup pré-déploiement | ✅ **LEVÉE** — Préflight (2026-06-29) |
| **RP-120-02** | Rollback schéma V15 non trivial | ✅ **LEVÉE** — Drill pg_restore V15 exécuté sur staging le 2026-06-29 : exit 0, Flyway 15/15, 11 RLS, NOBYPASSRLS — `drill-rollback-v15-report.md` |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-a42d860d` |
| Digest API | `sha256:c3d89f0d6da5cfad55daa0ad921df4be0539757c8ca3384110323e7425290749` |
| Digest Web | `sha256:c30708984117717b8bad0b6447fd009b561680376c7d3a7d60ffa81e1ba8c4ba` |
| Flyway | V1→V15 (15/15) — inchangé |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 |
| Actuator | `{"status":"UP"}` |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | `[]` — 0 alerte active |
| `.env` | `LOYERTRACKER_TAG=sha-a42d860d` persisté, permissions 600 |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.3.0` CLÔTURÉE le 2026-06-29.**

- Hypercare T0/T+12/T+24 anticipés — tous PASS.
- Sprint 4 UI Patrimoine, remédiation audit CGPA v5.4.1 et correctif É-01 confirmés en Production.
- RP-130-01 levée.
- Prochaine action autorisée : prochain lot fonctionnel ou correctif selon backlog PO.
