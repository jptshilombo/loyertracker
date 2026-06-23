# Audit initial de migration CGPA v5.3 — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Projet | LoyerTracker |
| Référentiel cible | CGPA v5.3 (`/home/ubuntu/setup-cgpa/docs/cgpa/CGPA-v5.3.md`) |
| Version détectée avant migration | CGPA v5.2 dans `docs/project-state.md` |
| Décision d'audit | **GO sous réserve** |

## Version actuelle détectée

Le projet est gouverné par CGPA avec un `framework.current_version` à `5.2`. Les documents indiquent une reprise additive `3.0.1 -> 5.0.1 -> 5.2`, puis une production réelle `1.0.0` mise en ligne le 2026-06-20.

Documents CGPA existants détectés :

- `docs/project-state.md` : présent, historique riche, version CGPA `5.2`.
- `docs/staging-state.md` : présent, Gate Staging Readiness GO et historique staging.
- `docs/prod-state.md` : présent, production LIVE, Gate 10 GO.
- `docs/cgpa/01` à `10` : présents selon l'ancienne arborescence projet.
- `docs/cgpa/07-devsecops`, `09-production`, `10-mise-en-production` : présents avec Gates 06A, 07A, 09 et 10.
- `.github/workflows/ci.yml` et `.github/workflows/codeql.yml` : présents.

## Phases et Gates déjà validés

| Élément | État détecté |
|---------|--------------|
| Phases 00 à 06 | Validées historiquement |
| Phase courante | Phase 7 — Développement |
| Gate 06 | GO le 2026-06-06 |
| Gate Staging Readiness | GO le 2026-06-14, enrichi GO le 2026-06-19 |
| Gate 06A | GO le 2026-06-16 |
| Gate 07A | GO sous réserve le 2026-06-19 |
| Gate 09 | GO sous réserve le 2026-06-19 |
| Gate 10 | GO le 2026-06-20 |

Aucun Gate validé ne doit être rejoué ou supprimé. La migration v5.3 est donc additive.

## Environnements disponibles

| Environnement | État |
|---------------|------|
| Development | Disponible via `docker-compose.yml`, `.env.example`, stack locale |
| Staging | Disponible, exposé sur `https://loyertracker.staging.loyerpro.org`, déploiements GHCR par tag immuable |
| Production | Disponible, LIVE sur `https://loyertracker.loyerpro.org`, release `1.0.0` |

## CI/CD et DevSecOps

GitHub Actions couvre :

- backend Maven `verify`, JaCoCo, SonarQube bloquant ;
- frontend `npm ci`, lint, build, Karma avec couverture, SonarQube bloquant ;
- Gitleaks, OWASP Dependency-Check informatif, Trivy bloquant sur dépendances et images ;
- packaging Docker GHCR sur `main` avec tags `sha-<8>` ;
- CodeQL Java/Kotlin et JavaScript/TypeScript.

Rollback : documenté par redéploiement de tag immuable et restauration PostgreSQL. Le drill de rollback production significatif reste à réaliser lors de la prochaine release.

## Écarts avec CGPA v5.3

| Écart | Impact | Traitement |
|-------|--------|------------|
| `framework.current_version` encore à `5.2` | Traçabilité v5.3 absente | Mise à jour additive vers `5.3` |
| Statuts `STAGING_READY`, `STAGING_DEPLOYED`, `PRODUCTION_READY`, `PRODUCTION_DEPLOYED` non formalisés dans `project-state.md` | Release Management incomplet | Ajout d'un bloc v5.3 |
| Workflows v5.3 Sprint -> Staging, Epic -> Production et Hotfix -> Production absents | Gouvernance de promotion incomplète | Création des workflows |
| Checklists Gate Staging / Gate Production v5.3 absentes | Gates v5.3 non outillés | Création des checklists |
| Décisions D-RM-01 à D-RM-04 absentes | Politique Release Management non tracée | Ajout au registre v5.3 |
| Risques RSV-RM-01 à RSV-RM-04 absents | Registre risques RM incomplet | Ajout au registre v5.3 |
| Discipline UX/UI v5.3 non formalisée | Projet avec UI déjà en production, Gate 02A non historique | Ajout d'un écart accepté et d'un plan UX proportionné |
| `AGENTS.md`, `CLAUDE.md`, `docs/cgpa/README.md` absents à la racine projet | Règles agents non accessibles depuis la racine | Création alignée v5.3 |

## Risques de migration

| Risque | Niveau | Mitigation |
|--------|--------|------------|
| Perte d'historique par réécriture documentaire | Élevé | Migration additive uniquement ; aucun document historique supprimé |
| Confusion entre Gate 09/10 historiques et Gate Production v5.3 | Moyen | Gate Production v5.3 mappé sur Gate 09 + Gate 10 existants pour `1.0.0` |
| Application rétroactive stricte du Gate 02A UX/UI | Moyen | Écart accepté : projet existant déjà livré ; ajout d'un plan UX pour les futurs lots UI |
| Production déjà LIVE avant formalisation v5.3 | Moyen | Conservation du Gate 10 GO, ajout des statuts Production v5.3 a posteriori |
| Accumulation d'éléments Patrimoine en Staging/Non publié | Moyen | Ajout RSV-RM-01 et rapport sprints/epics |

## Recommandation

**GO sous réserve** pour la migration CGPA v5.3.

Réserves non bloquantes :

- formaliser Gate 02A UX/UI pour les futurs développements UI ;
- exécuter un drill de rollback production lors de la prochaine release significative ;
- décider explicitement la promotion Staging/Production du lot Patrimoine `[Non publié]`.
