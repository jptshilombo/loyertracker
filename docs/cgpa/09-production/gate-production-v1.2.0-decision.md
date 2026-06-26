# Gate Production — Release `1.2.0`

| Champ | Valeur |
|---|---|
| Date | 2026-06-26 |
| Type | Release mineure |
| Version | `1.2.0` |
| Commit candidat | `5bf187af79218377b2f7db7800961725088d31a5` |
| Artefact | API et Web `sha-5bf187af` |
| Production actuelle | `1.1.1` — `sha-0adc4941` |
| Rollback disponible | `sha-0adc4941` (application) + backup pré-déploiement (schéma) |
| Décision | **GO sous réserve** |
| Statut | **`PRODUCTION_READY`** |

## 1. Objet

Statuer l'autorisation Production de la release `1.2.0`, composée du Sprint 3 Patrimoine
(US-85, migration V15, RS-04) et du correctif CORS Compose, validés conjointement en Staging
le 2026-06-25 sous le tag `sha-5bf187af`.

## 2. Périmètre

### Inclus

- **Sprint 3 Patrimoine — US-85** : exceptions fines `INCLUSION`/`EXCLUSION` par affectation
  bien, migration **V15** (`affectation.type_exception`, backfill idempotent, réécriture à
  priorité de `gestionnaire_affecte_actif`/`biens_affectes_gestionnaire`, correctif
  `calculer_honoraires`), garde **RS-04** (EXCLUSION orpheline rejetée 400).
- **Correctif CORS Compose** : `APP_CORS_ALLOWED_ORIGIN` et `APP_INVITATION_BASE_URL` câblés
  au service `api` dans `docker-compose.yml` et `docker-compose.prod.yml` (via héritage).

### Exclus

- UI/frontend des exceptions INCLUSION/EXCLUSION (backend-only, différée — décision PO
  2026-06-24).
- Correctif cascade dashboard (`c1e9c73`) — post-Gate Staging ; cf. §3 ci-dessous.
- Commits documentaires post-`5bf187af` (`77c5ac6`, `c487dc6`, `f0921e5`, `5e71f6c`).

## 3. Commit post-Gate Staging — décision CDO

Le commit `c1e9c73` (`fix(dashboard): charge les biens même si l'inscription échoue`) a été
poussé sur `main` le 2026-06-26, après la clôture du Gate Staging du 2026-06-25. Il est
purement Angular/frontend (aucun backend, aucune migration, aucun changement de sécurité) et
la CI est verte (`sha-c1e9c73`, runs `28250214160` et `28250214177` — SUCCESS).

| Option | Artefact | Couverture Staging | Risque |
|--------|----------|--------------------|--------|
| **A — retenu** | `sha-5bf187af` | Staging complet 47/0 | Nul |
| B | `sha-c1e9c73` | Staging non exécuté pour `c1e9c73` | Faible (Angular-only) mais hors conformité CGPA |

**Décision CDO : Option A** — `sha-5bf187af` est l'artefact de Production `1.2.0`. Le
correctif `c1e9c73` sera validé en Staging puis promu en `1.2.1`.

## 4. Checklist Gate Production

### Identification

| Critère | Statut | Preuve |
|---------|--------|--------|
| Périmètre Release identifié | OK | §2 ci-dessus |
| Version SemVer identifiée | OK | `1.2.0` — MINOR (nouvelle fonctionnalité US-85) + PATCH (CORS) |
| Commit et artefact identifiés | OK | `5bf187af79218377b2f7db7800961725088d31a5`, tag `sha-5bf187af` |
| Environnement source | OK | Staging `ai-test-server` (`https://loyertracker.staging.loyerpro.org`) |
| Environnement cible | OK | Production `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |

### Preuves Staging

| Critère | Statut | Preuve |
|---------|--------|--------|
| Candidat déployé en Staging | OK | `sha-5bf187af` déployé le 2026-06-25 |
| Services Staging | OK | 4/4 `(healthy)` ; 8/8 conteneurs `Up` |
| Smoke Staging | OK | **47 PASS / 0 FAIL** — V15 (15 migrations), CORS vars injectées, parcours métier complet, isolation cross-tenant |
| Vérification navigateur | OK | HTTPS → 200, Angular → 200, `/api/actuator/health` UP, Keycloak OIDC → 200, JWT obtenu, données réelles (patrimoines, biens, alertes, paiements) |
| Défauts bloquants | OK | Aucun — 401 observés en session expirée, non liés à `sha-5bf187af` |
| `STG-ISOL-01` | **PASS** | Preuve live 2026-06-25 : 8 conteneurs `loyertracker-staging-*` avant et après — **RSV-STG-01 levée** |
| Accumulation Staging | OK | Candidat figé à `5bf187af` ; commits post-Staging hors périmètre (§3) |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---------|--------|--------|
| Stories terminées et validées | OK | US-85 (4/4 combinaisons RM-98, RS-04, non-régression S02/S03/S04) ; CORS lot autorisé CDO post-hypercare |
| Validation Product Owner | OK | Kickoff Sprint 3 GO PO 2026-06-25 ; lot CORS autorisé CDO 2026-06-25 |
| Validation Release Manager | GO sous réserve | Artefact, périmètre, rollback et migration vérifiés |
| Release notes | OK | `docs/release-notes-v1.2.0.md` |
| Changelog | OK | Section `[Non publié]` dans `CHANGELOG.md` |

### DevSecOps et sécurité

| Critère | Statut | Preuve |
|---------|--------|--------|
| Build stable | OK | CI `5bf187af` : CodeQL ✅ + pipeline complet ✅ (Gate Staging 2026-06-25) |
| Tests unitaires | OK | `mvn verify` 99 tests / 0 échec (Sprint 3 + CORS) |
| Tests d'intégration | OK | 4/4 combinaisons RM-98, 2 tests HTTP RS-04 |
| Gitleaks / SCA / Trivy | OK | Gitleaks 168 commits / no leaks, Trivy SCA 0 HIGH/CRITICAL |
| SonarQube backend | OK | PR #81 : `new_violations` 0, `new_coverage` 93,1 %, Quality Gate verte |
| Secrets | OK | Aucun secret versionné ; `.env` hors dépôt ; aucun secret exposé dans les Compose |
| Migration V15 | OK | Additive (colonne nullable, backfill idempotent) — cf. §Rollback pour la procédure |
| Observabilité | OK | Dispositif Production existant inchangé ; 5 cibles Prometheus, Alertmanager configuré |

### Rollback

| Critère | Statut | Preuve / Note |
|---------|--------|---------------|
| Tag rollback applicatif | OK | `sha-0adc4941` (Production `1.1.1`) |
| Digests rollback | OK | API `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016`, Web `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` |
| Responsable rollback | OK | DevSecOps Lead, coordination Release Manager |
| Migration V15 et rollback | ⚠ **Condition** | V15 additive (colonne nullable) ; rollback applicatif seul interdit : Flyway valide 15 migrations au démarrage de `sha-0adc4941` → erreur de validation. Procédure de rollback complète = **restauration du backup pré-déploiement** (pg_restore) puis redéploiement `sha-0adc4941`. Le backup doit être vérifié (`pg_restore --list`) avant tout déploiement — condition Étape Préflight. |
| Backup pré-déploiement | Condition Étape Préflight | À créer et vérifier avant le déploiement (`pg_dump`, `pg_restore --list`) |

## 5. Réserves et conditions

| ID | Nature | Traitement |
|----|--------|------------|
| RP-120-01 | Backup Production non encore créé | **Bloquant** — levé uniquement après `pg_dump` + `pg_restore --list` OK en Étape Préflight |
| RP-120-02 | Rollback schéma non trivial (V15) | Accepté — procédure de restauration backup documentée (§Rollback) ; schéma V15 additive et réversible via backup |
| RP-120-03 | `c1e9c73` exclu de `1.2.0` | Accepté — correctif promu `1.2.1` après Staging (§3) |

## 6. Avis des rôles

| Rôle | Avis |
|------|------|
| Governance Officer | **GO sous réserve** — traçabilité Staging complète, STG-ISOL-01 live PASS, réserves explicites |
| Enterprise Architect | **GO** — V15 additive non-destructive, backfill idempotent, CORS câblage Compose pur, RLS inchangé |
| DevSecOps Lead | **GO sous réserve** — CI verte, SonarQube verte, backup/préflight obligatoire avant déploiement |
| Release Manager | **GO sous réserve** — candidat et rollback valides ; condition backup et procédure rollback schéma maintenues |
| Product Owner | **GO** — US-85 et CORS validés, périmètre backend-only confirmé |

## 7. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint** pour la release `1.2.0`.
- `PRODUCTION_DEPLOYED` : non atteint — déploiement à exécuter selon le plan Préflight → Déploiement → Validation finale → Hypercare.

### Conditions bloquantes avant déploiement

1. **RP-120-01** — backup Production vérifié (`pg_dump` + `pg_restore --list`).
2. Confirmer les digests GHCR de `sha-5bf187af` au moment du `docker pull` (immuabilité).
3. Déploiement ciblé `api` + `nginx` uniquement (postgres, keycloak, monitoring inchangés).

### Prochaines étapes

| Étape | Référence |
|-------|-----------|
| Préflight + backup Production | À créer : `preflight-backup-v1.2.0-report.md` |
| Déploiement technique | À créer : `deploiement-technique-v1.2.0-report.md` |
| Validation finale + smoke (V15) | À créer : `validation-finale-v1.2.0-report.md` |
| Hypercare 24 h (T0 / T+12 / T+24) | À créer : `plan-etape-2-hypercare-production-v1.2.0.md` |
| Dossier de clôture | À créer : `cloture-release-v1.2.0.md` |
| Changelog — promouvoir `[Non publié]` → `[1.2.0]` | Après `PRODUCTION_DEPLOYED` |
