# Rapport de validation CI/CD — CGPA v5.3

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Projet | LoyerTracker |
| Décision | **GO sous réserve** |

## GitHub Actions

Workflows détectés :

- `.github/workflows/ci.yml`
- `.github/workflows/codeql.yml`

## Contrôles détectés

| Domaine | État | Preuve |
|---------|------|--------|
| Build backend | Présent | `mvn -B verify` |
| Tests unitaires backend | Présents | Maven Surefire |
| Tests d'intégration backend | Présents | Testcontainers PostgreSQL, Flyway, RLS |
| Couverture backend | Présente | JaCoCo, artefact publié |
| SonarQube backend | Présent, bloquant | `mvn sonar:sonar`, `sonar.qualitygate.wait=true` |
| Build frontend | Présent | `npm run build -- --configuration production` |
| Tests frontend | Présents | Karma Chrome Headless avec couverture |
| Lint frontend | Présent | ESLint |
| SonarQube frontend | Présent, bloquant | `sonarqube-scan-action@v6` |
| Secrets | Présent | Gitleaks |
| SCA | Présent | OWASP Dependency-Check informatif, Trivy bloquant |
| Scan images | Présent | Trivy API et Web |
| Artefacts | Présents | JaCoCo, dependency-check report, images GHCR |
| Packaging | Présent | Docker API/Web, GHCR sur `main` |
| CodeQL | Présent | Java/Kotlin + JS/TS |
| Rollback | Documenté | Tags GHCR immuables `sha-<8>`, runbook et backup/restore |

## Environnements

| Environnement | Validation |
|---------------|------------|
| Development | `docker-compose.yml`, `.env.example`, certificats dev |
| Staging | `docker-compose.staging.yml`, `docs/staging-state.md`, smoke script |
| Production | `docker-compose.prod.yml`, `docs/prod-state.md`, Gate 10 GO |

## Écarts

| Écart | Impact | Action |
|-------|--------|--------|
| Rollback production significatif non rejoué pour une release ultérieure | Résidu d'exploitation | Planifier au prochain changement de version |
| Déploiement automatique CD jusqu'à Production absent | Acceptable CGPA | Production reste contrôlée par Gate Production manuel |
| Gate Staging v5.3 du lot Patrimoine non encore exécuté | Promotion différée | Créer décision dédiée avant `STAGING_DEPLOYED` |

## Décision

**GO sous réserve**. La chaîne CI/CD satisfait les exigences CGPA v5.3 pour build, tests, DevSecOps, artefacts et rollback documenté. Les réserves portent sur la gouvernance de promotion des prochains lots, non sur la qualité du pipeline.
