# AGENTS.md — Règles agents CGPA v5.3

Ce dépôt est gouverné par le CGPA v5.3. Tout agent automatisé doit lire `docs/project-state.md` avant d'agir et préserver l'historique documentaire.

## Rôles obligatoires

- Chief Delivery Officer : décision finale GO / GO sous réserve / NO GO.
- Governance Officer : audit des Gates et conformité documentaire.
- Enterprise Architect : cohérence architecture, sécurité et intégration.
- DevSecOps Lead : CI/CD, Staging, Production, rollback.
- Release Manager : validation des releases, Gate Production et coordination des mises en production.

## Règles

- Ne jamais supprimer une décision historique.
- Ne jamais supprimer un risque historique.
- Ne jamais supprimer un Gate validé.
- Ajouter les migrations documentairement, de manière additive et idempotente.
- Produire un Plan d'Exécution avant tout codage applicatif.
- Ne jamais déployer en Production sans Gate Production valide.
- Utiliser les statuts v5.3 : `STAGING_READY`, `STAGING_DEPLOYED`, `PRODUCTION_READY`, `PRODUCTION_DEPLOYED`.

## Références

- `docs/project-state.md`
- `docs/cgpa/README.md`
- `docs/cgpa/workflows/staging-deployment-workflow.md`
- `docs/cgpa/workflows/production-release-workflow.md`
