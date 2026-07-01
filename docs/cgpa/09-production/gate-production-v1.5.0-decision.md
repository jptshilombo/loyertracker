# Gate Production — Release `1.5.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-01 |
| Type | Release MINOR — Sprint 6 RGPD (US-70) + durcissement CSP Nginx (US-72) |
| Version | `1.5.0` |
| Commit applicatif | `08b366fa9a6ffa6c1e973be5b23861135918315b` |
| Tag candidat | `sha-08b366fa` |
| Digest GHCR API | `sha256:865dd686f76c90d514a26056ed7d6ad248ad5dd6c46d8776e88c68a144d80520` |
| Digest GHCR Web | `sha256:a7c74954700f300da1e5b40f104087da4c3bb629f0269aba0c1703b07d612b3e` |
| Production actuelle | `1.4.0` — `sha-98afa99a` |
| Rollback disponible | `sha-98afa99a` — applicatif seul, sans `pg_restore` |
| Décision | **GO sous réserve** |
| Statut | **`PRODUCTION_READY`** |

## 1. Objet

Statuer l'autorisation Production de la release `1.5.0`, composée des évolutions du Sprint 6
RGPD & durcissement sécurité (US-70, US-71, US-72 — PR #123), validées en Staging le 2026-07-01
sous le tag `sha-08b366fa`.

## 2. Périmètre

### Inclus

**PR #123 — Sprint 6 RGPD + CSP** (merge commit `08b366f`)
- `feat(rgpd)` : `RgpdController` + `RgpdService` + `ExportBailleurDto` —
  `GET /api/bailleurs/export` (export scopé bailleur), `DELETE
  /api/biens/{bienId}/baux/{bailId}/locataire` (anonymisation locataire).
- `feat(baux)` : `Bail.anonymiserLocataire()`, mise à jour `BailRepository`.
- `feat(nginx)` : durcissement CSP (`script-src 'self'`, `font-src 'self'`, `object-src 'none'`,
  `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`).
- `test(rgpd)` : `RgpdIntegrationTest` (236 lignes, Testcontainers) ; extension
  `SecurityIntegrationTest`.
- US-71 (tests cross-bailleur) : considérée Done sans développement nouveau — couverture
  existante (`SecurityIntegrationTest`, tests S02-S04) jugée suffisante par le plan Sprint 6.

### Exclus

- PR #124 (`docs(staging)` — décision Gate Staging Sprint 6) et PR #125 (`feat(smoke)` —
  extension `infra/smoke/smoke-stack.sh` aux 2 nouveaux endpoints RGPD) : commits postérieurs
  au candidat, **documentaires et outillage de test uniquement**, aucun changement de code
  applicatif (`backend/`, `frontend/`, `infra/nginx/nginx.conf` non touchés). Le candidat
  Production reste figé à `sha-08b366fa`, tag exact vérifié en Staging.
- Aucune modification Keycloak, Compose, réseau ou volume.
- Aucune migration Flyway (V18 reste le rang maximal — identique à `1.4.0`).

## 3. Checklist Gate Production (CGPA v5.4.1)

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Release identifié | ✅ | §2 ci-dessus — MINOR SemVer, nouvelles fonctionnalités RGPD + durcissement CSP |
| Version SemVer identifiée | ✅ | `1.5.0` — MINOR (nouveaux endpoints additifs, aucune rupture) |
| Commit et artefact identifiés | ✅ | `08b366f` applicatif ; tag `sha-08b366fa` ; digests immuables vérifiés |
| Environnement source | ✅ | Staging `ai-test-server` (`https://loyertracker.staging.loyerpro.org`) |
| Environnement cible | ✅ | Production `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | ✅ | `sha-08b366fa` déployé le 2026-07-01 |
| Services Staging | ✅ | 8/8 `(healthy)`/`Up` ; restart count = 0 |
| Smoke Staging | ✅ | **59 PASS / 0 FAIL** (47 historiques + 12 RGPD ajoutées §RSV-S6-01) |
| Flyway Staging | ✅ | 18/18 — aucune migration supplémentaire par rapport à `1.4.0` |
| `STG-ISOL-01` | ✅ **PASS** | Constaté après déploiement — 8 conteneurs `loyertracker-staging-*`, `nginx-proxy-manager` intact, restart=0 |
| Vérification comportementale US-70/US-72 | ✅ **PASS** | Export live (200, JSON scopé, isolation cross-tenant confirmée) ; effacement live (403 gestionnaire, 204 bailleur, anonymisation confirmée, `audit_log` tracé) ; CSP live (toutes directives cibles présentes) |
| Accumulation Staging | ✅ | Candidat figé à `sha-08b366fa` ; commits postérieurs (`9ca3a12`, `13e37c0`) documentaires/outillage uniquement — aucun commit applicatif post-Gate Staging |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Sprint 6 validé | ✅ | Plan `docs/cgpa/07-devsecops/sprint6-plan.md` — toutes étapes ✅ FAIT, réserve RSV-S6-01 levée |
| Release notes disponibles | ✅ | `docs/release-notes-v1.5.0.md` |
| Changelog disponible | ✅ | `CHANGELOG.md` section `[Non publié]` contient Sprint 6 — à promouvoir en `[1.5.0]` après `PRODUCTION_DEPLOYED` |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---|---|---|
| Build CI stable | ✅ | `sha-08b366fa` (PR #123) — SUCCESS toutes jobs : Backend, Frontend, CodeQL Java/Kotlin + JS/TS, Sécurité (Gitleaks/SCA/Trivy), Packaging Docker |
| Tests backend | ✅ | `RgpdIntegrationTest` (236 lignes) + suite complète PASS, 0 FAIL ; `mvn verify` SUCCESS |
| Gitleaks / SCA / Trivy | ✅ | Job Sécurité SUCCESS dans CI `08b366f` |
| SonarQube | — | Non explicitement vérifié dans ce Gate (aucune alerte Quality Gate remontée en CI, cf. §5 réserve) |
| Migrations Production | ✅ | **Aucune** — Flyway reste à V18 ; aucun script V19+ entre `1.4.0` et `1.5.0` |
| Observabilité | ✅ | Dispositif Production existant inchangé (5 cibles Prometheus, Alertmanager, Pushgateway) |
| Secrets | ✅ | Aucun secret versionné ; `.env` hors dépôt ; images GHCR privées ; digests immuables confirmés |

### Rollback

| Critère | Statut | Note |
|---|---|---|
| Tag rollback applicatif | ✅ | `sha-98afa99a` (Production `1.4.0`) |
| Responsable rollback | ✅ | DevSecOps Lead, coordination Release Manager |
| Migration V19+ et rollback | ✅ | **Aucune migration supplémentaire** entre `1.4.0` et `1.5.0` — rollback applicatif seul suffisant, sans `pg_restore` |
| Rollback Production | ✅ | `sha-98afa99a` disponible sur GHCR ; procédure : `LOYERTRACKER_TAG=sha-98afa99a docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx` |

## 4. Réserves et conditions

| ID | Nature | Traitement |
|----|--------|------------|
| RP-150-01 | Backup Production non encore créé | **Bloquant** — levé uniquement après `pg_dump -Fc` + `pg_restore --list` OK en Préflight |
| RP-150-02 | SonarQube Quality Gate non explicitement revérifié pour ce Gate (cf. §3) | Non bloquant — CI `sha-08b366fa` intégralement verte (dont l'étape SonarQube si présente dans le pipeline standard), aucune alerte remontée |

## 5. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — traçabilité Staging complète (STG-ISOL-01 PASS, smoke 59/0, vérification comportementale live des 2 endpoints), réserve RP-150-01 explicite |
| Enterprise Architect | **GO** — 2 endpoints additifs (1 GET read-only scopé RLS, 1 DELETE anonymisation transactionnelle), aucune migration, rollback trivial (`sha-98afa99a` sans `pg_restore`), RLS/Keycloak/Compose inchangés |
| DevSecOps Lead | **GO sous réserve** — CI SUCCESS toutes jobs, 0 High/Critical sécurité, backup pré-déploiement obligatoire (RP-150-01) |
| Release Manager | **GO sous réserve** — candidat recevable `sha-08b366fa`, preuves Staging complètes (smoke 59/0 + vérification comportementale RGPD/CSP), condition backup maintenue |
| Product Owner | **GO** — Sprint 6 RGPD/CSP validé, scope effacement (locataire uniquement) validé le 2026-07-01 |

## 6. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint** pour la release `1.5.0`.
- `PRODUCTION_DEPLOYED` : non atteint — déploiement à exécuter selon le plan
  Préflight → Déploiement → Validation finale → Hypercare.

### Condition bloquante avant déploiement

**RP-150-01** — backup Production vérifié (`pg_dump -Fc` + `pg_restore --list` OK).
Aucune autre condition.

### Services cibles du déploiement

**`api` et `nginx`** — le backend reçoit 2 nouveaux endpoints RGPD et le frontend reçoit la CSP
durcie. Postgres, Keycloak et le monitoring sont inchangés.

### Prochaines étapes

| Étape | Document à créer |
|---|---|
| Préflight + backup Production | `preflight-backup-v1.5.0-report.md` |
| Déploiement technique (`api` + `nginx`) | `deploiement-technique-v1.5.0-report.md` |
| Validation finale + smoke Production | `validation-finale-v1.5.0-report.md` |
| Hypercare + clôture | `plan-etape-hypercare-v1.5.0.md` / `cloture-release-v1.5.0.md` |
| CHANGELOG — promouvoir `[Non publié]` → `[1.5.0]` | Après `PRODUCTION_DEPLOYED` |
