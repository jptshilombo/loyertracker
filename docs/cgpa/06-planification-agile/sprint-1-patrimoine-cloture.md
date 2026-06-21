# Décision de clôture — Sprint 1 Patrimoine

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-21 |
| Périmètre | Sprint 1 — Patrimoines & modèle de données |
| Stories couvertes | US-80 (patrimoines), US-81 (typologie), US-82 (rattachement bien → patrimoine) |
| Référence d'exécution | PR #72 / commit `74fe51c` intégré dans `origin/main` (`6eeeb6e`) |
| Statut proposé | **GO technique — Sprint 1 clôturable** |

## 1. Verdict

Le Sprint 1 Patrimoine est **clôturable en GO technique** : le modèle `Patrimoine`, la typologie `TypeBien`, le rattachement obligatoire des biens à un patrimoine, la migration de données V12 et les contrôles RLS/non-régression sont intégrés dans `origin/main` via la PR #72.

La poursuite vers le Sprint 2 reste soumise à un point de contrôle séparé, conformément au plan d'exécution : le GO Sprint 1 ne vaut pas autorisation automatique de coder les affectations patrimoine.

## 2. Critères de clôture Sprint 1

| Critère | Statut | Preuve |
|---------|--------|--------|
| 100 % des biens existants rattachés à un patrimoine, 0 orphelin | **OK** | Migration `V12__patrimoine_type_bien.sql` + tests d'intégration Patrimoine PR #72 |
| 100 % des `Bien.type` migrés ou arbitrés | **OK** | Introduction du référentiel `TypeBien` + mapping par défaut dans V12 |
| Suite de tests existante sans régression | **OK** | Validation locale complète du 2026-06-21 : backend `mvn -f backend/pom.xml verify` — 78 tests, 0 failure/error/skipped ; frontend Karma — 41 tests SUCCESS |
| RLS `ENABLE` + `FORCE` vérifiée sur Patrimoine | **OK** | Tests d'intégration Patrimoine sous rôle applicatif `loyertracker_api`, même patron de fidélité que la remediation RLS antérieure |
| Build frontend production | **OK** | `npm run build -- --configuration production` — bundle généré avec succès |
| Lint frontend | **OK** | `npm run lint` — all files pass linting |
| Sécurité secrets | **OK** | Gitleaks Docker : 139 commits scannés, no leaks found |
| SCA dépendances runtime frontend | **OK** | `npm audit --omit=dev` : 0 vulnerabilities ; Trivy fs `frontend` HIGH/CRITICAL ignore-unfixed : 0 vulnérabilité |
| Images conteneurs API/Web | **OK** | `docker build` API + Web OK ; Trivy image API/Web HIGH/CRITICAL ignore-unfixed : 0 vulnérabilité |
| CI distante `main` | **OK** | GitHub Actions sur `6eeeb6e` : CI success + CodeQL success le 2026-06-21 |

## 3. Validations exécutées le 2026-06-21

- Backend : `mvn -f backend/pom.xml verify`
  - Résultat : **BUILD SUCCESS**
  - Tests : **78 run, 0 failure, 0 error, 0 skipped**
  - Flyway : **12 migrations validées/appliquées en Testcontainers jusqu'à V12**
  - JaCoCo : **All coverage checks have been met**
- Frontend : `npm run lint`
  - Résultat : **All files pass linting**
- Frontend : `npm run build -- --configuration production`
  - Résultat : **Application bundle generation complete**
- Frontend : `npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage`
  - Résultat : **41 SUCCESS**
  - Couverture : statements 75.83 %, branches 57.44 %, functions 71.15 %, lines 74.89 %
- Sécurité : Gitleaks via Docker
  - Résultat : **139 commits scannés, no leaks found**
- Sécurité : Trivy fs frontend
  - Résultat : **0 vulnérabilité HIGH/CRITICAL runtime**
- Sécurité : Trivy images API/Web
  - Résultat : **0 vulnérabilité HIGH/CRITICAL** sur `loyertracker-api:local-validation` et `loyertracker-web:local-validation`
- CI distante : `gh run list --branch main`
  - Résultat : **CI success** et **CodeQL success** sur `6eeeb6e`

## 4. Points d'attention non bloquants

- `npm audit --audit-level=high` remonte des vulnérabilités **dev-only** dans la chaîne Angular/Vite/Webpack ; elles ne touchent pas le runtime (`npm audit --omit=dev` et Trivy fs runtime à 0). À surveiller lors des prochaines mises à jour Angular.
- Le warning Angular sur `js-sha256` (dépendance transitive Keycloak) reste un warning d'optimisation CommonJS, non bloquant.
- La cache locale Trivy est ignorée via `.gitignore` (`.trivy-cache/`) pour éviter de polluer les commits.

## 5. Décision de suite

- **Sprint 1 Patrimoine : GO technique / clôturable.**
- **Sprint 2 Patrimoine : non démarré dans cette décision.** Le démarrage Sprint 2 doit être confirmé explicitement, même si les arbitrages RM-98 / RS-04 / RS-05 / RS-06 sont déjà documentés dans le plan d'exécution.
