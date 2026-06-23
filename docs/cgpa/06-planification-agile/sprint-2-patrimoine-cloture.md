# Décision de clôture — Sprint 2 Patrimoine

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Périmètre | Sprint 2 — Affectations patrimoine & héritage ReBAC backend-first |
| Stories couvertes | US-84, début US-85 |
| Référence d'exécution | PR #74 / merge commit `ed448b8` intégré dans `origin/main` |
| Branche source | `sprint-2-patrimoine-reprise` |
| Statut | **GO technique — Sprint 2 clôturé côté `main`** |

## 1. Verdict

Le Sprint 2 Patrimoine est **clôturé en GO technique** : la pile de commits Sprint 2 a été intégrée dans `origin/main` via la PR #74 (`ed448b8`) avec CI GitHub verte.

Le périmètre livré reste strictement celui du backend-first approuvé : affectation patrimoine, héritage dynamique ReBAC, liste effective gestionnaire, garde RS-06 et non-régression des affectations bien existantes. Aucune UX avancée ni logique `EXCLUSION` complète n'est incluse ; ces éléments restent soumis à un arbitrage séparé avant Sprint 3.

## 2. Pile de commits intégrée

- `00c09df` — `feat: support patrimoine affectations`
- `9cb6f39` — `feat: enforce patrimoine affectation inheritance`
- `96565d1` — `test: block patrimoine archival with active affectation`
- `eb441f2` — `docs: record sprint 2 patrimoine validation`
- `b6a8bd9` — `refactor: reduce affectation constructor arity`
- `ed448b8` — merge PR #74 dans `main`

## 3. Critères de clôture Sprint 2

| Critère | Statut | Preuve |
|---------|--------|--------|
| Affectation patrimoine créée/révoquée par bailleur propriétaire | **OK** | Implémentation PR #74 + tests backend Sprint 2 |
| Affectation patrimoine refusée pour patrimoine archivé ou cross-bailleur | **OK** | Validations service/controller et tests d'intégration |
| Gestionnaire voit les biens actuels du patrimoine affecté | **OK** | Tests ReBAC / `AuthorizationService` |
| Gestionnaire voit automatiquement un bien ajouté après l'affectation patrimoine | **OK** | Test d'héritage dynamique patrimoine |
| `GET /api/biens` gestionnaire reflète le périmètre effectif sans doublons | **OK** | Extension de la résolution des biens affectés gestionnaire |
| `AuthorizationService` reste la source de vérité ReBAC | **OK** | Résolution centralisée et tests de non-régression |
| Archivage patrimoine avec affectation patrimoine `ACTIVE` retourne 400 | **OK** | Test `test: block patrimoine archival with active affectation` |
| Révocation explicite puis archivage fonctionne | **OK** | Scénario couvert côté service |
| Affectations bien existantes non régressées | **OK** | Suite S02/S03/S04 et tests d'autorisation conservés |
| Backend/frontend/sécurité passent | **OK** | Validation locale + checks GitHub PR #74 tous SUCCESS |

## 4. Validations locales exécutées le 2026-06-23

Référence détaillée : `docs/cgpa/06-planification-agile/sprint-2-patrimoine-rapport-validation.md`.

- Backend : `mvn -f backend/pom.xml verify`
  - Résultat : **BUILD SUCCESS**
  - Tests : **84 run, 0 failure, 0 error, 0 skipped**
  - Flyway : **13 migrations** appliquées jusqu'à V13
  - Spotless : **0 needs changes**
  - JaCoCo : **All coverage checks have been met**
- Frontend : `npm run lint`
  - Résultat : **All files pass linting**
- Frontend : `npm run build -- --configuration production`
  - Résultat : **build OK**
- Frontend : `npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox --code-coverage`
  - Résultat : **41 SUCCESS**
- Sécurité : Gitleaks Docker
  - Résultat : **147 commits scanned, no leaks found**
- SCA : Trivy FS frontend + global avec cache Maven monté
  - Résultat : **0 vulnérabilité HIGH/CRITICAL** sur `backend/pom.xml` et `frontend/package-lock.json`

## 5. Validations CI GitHub PR #74

PR : <https://github.com/jptshilombo/loyertracker/pull/74>

Checks observés après merge :

- Backend (build + tests + couverture) : **SUCCESS**
- Frontend (build + tests) : **SUCCESS**
- Sécurité (Gitleaks + SCA + Trivy) : **SUCCESS**
- Packaging Docker : **SUCCESS**
- Analyse CodeQL Java/Kotlin : **SUCCESS**
- Analyse CodeQL JavaScript/TypeScript : **SUCCESS**
- CodeQL agrégé : **SUCCESS**

## 6. Décision de suite

- **Sprint 2 Patrimoine : GO technique / clôturé côté `main`.**
- **Promotion staging : non décidée par cette clôture.** Elle nécessite une décision de promotion dédiée, avec tag GHCR immuable issu de `main` et smoke staging.
- **Sprint 3 Patrimoine : non démarré.** Les exceptions fines complètes (`EXCLUSION`) et l'UX avancée de périmètre effectif doivent faire l'objet d'un point de contrôle PO séparé.
- **Release production : inchangée.** La production reste en `1.0.0`; le lot Patrimoine Sprint 2 demeure en `[Non publié]` jusqu'à décision de release/promotion.
