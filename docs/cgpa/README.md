# Documentation CGPA — LoyerTracker

Ce dossier contient les livrables de gouvernance LoyerTracker alignés sur CGPA v5.4.1.

## Point d'entrée

- État projet vivant : `docs/project-state.md`
- État Staging : `docs/staging-state.md`
- État Production : `docs/prod-state.md`
- Rapport de migration v5.3 : `docs/cgpa/migration/migration-report-v5.3.md`
- Rapport de migration v5.4 : `docs/cgpa/migration/migration-report-v5.4.md`
- Rapport de migration v5.4.1 : `docs/cgpa/migration/migration-report-v5.4.1.md`

## Référentiel cible

Référentiel local : `/home/ubuntu/setup-cgpa/docs/cgpa/CGPA-v5.4.1.md` (évolution additive de
`CGPA-v5.3.md`, conservé comme référence historique).

La migration v5.4 est additive : aucun Gate validé, aucune décision historique et aucun risque historique ne sont supprimés.

## Workflows v5.3 (conservés)

- `docs/cgpa/workflows/staging-deployment-workflow.md`
- `docs/cgpa/workflows/production-release-workflow.md`

## Workflows v5.4.1

- `docs/cgpa/workflows/staging-isolation-workflow.md` — contrôle d'isolation Staging (`STG-ISOL-01`)

## Checklists v5.3 (conservées)

- `docs/cgpa/checklists/gate-staging-checklist.md`
- `docs/cgpa/checklists/gate-production-checklist.md`

## Checklists v5.4.1

- `docs/cgpa/checklists/stg-isol-01-checklist.md`

## Règle de promotion

- Sprint validé -> Gate Staging (incl. `STG-ISOL-01`) -> `STAGING_READY` -> déploiement -> `STAGING_DEPLOYED`.
- Epic, Release ou Hotfix validé -> Gate Production -> `PRODUCTION_READY` -> déploiement -> `PRODUCTION_DEPLOYED`.
- Aucun Sprint ne déclenche automatiquement la Production.
- L'environnement Staging (`ai-test-server`) est mutualisé avec d'autres projets : tout
  déploiement Staging doit satisfaire `STG-ISOL-01` (CGPA v5.4.1).
