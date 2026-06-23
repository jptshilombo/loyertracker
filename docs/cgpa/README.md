# Documentation CGPA — LoyerTracker

Ce dossier contient les livrables de gouvernance LoyerTracker alignés sur CGPA v5.3.

## Point d'entrée

- État projet vivant : `docs/project-state.md`
- État Staging : `docs/staging-state.md`
- État Production : `docs/prod-state.md`
- Rapport de migration v5.3 : `docs/cgpa/migration/migration-report-v5.3.md`

## Référentiel cible

Référentiel local : `/home/ubuntu/setup-cgpa/docs/cgpa/CGPA-v5.3.md`.

La migration v5.3 est additive : aucun Gate validé, aucune décision historique et aucun risque historique ne sont supprimés.

## Workflows v5.3

- `docs/cgpa/workflows/staging-deployment-workflow.md`
- `docs/cgpa/workflows/production-release-workflow.md`

## Checklists v5.3

- `docs/cgpa/checklists/gate-staging-checklist.md`
- `docs/cgpa/checklists/gate-production-checklist.md`

## Règle de promotion

- Sprint validé -> Gate Staging -> `STAGING_READY` -> déploiement -> `STAGING_DEPLOYED`.
- Epic, Release ou Hotfix validé -> Gate Production -> `PRODUCTION_READY` -> déploiement -> `PRODUCTION_DEPLOYED`.
- Aucun Sprint ne déclenche automatiquement la Production.
