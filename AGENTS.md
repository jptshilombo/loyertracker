# AGENTS.md — Règles agents CGPA v5.4.1

Ce dépôt est gouverné par le CGPA v5.4.1. Tout agent automatisé doit lire `docs/project-state.md` avant d'agir et préserver l'historique documentaire.

## Rôles obligatoires

- Chief Delivery Officer : décision finale GO / GO sous réserve / NO GO.
- Governance Officer : audit des Gates et conformité documentaire, y compris `STG-ISOL-01`.
- Enterprise Architect : cohérence architecture, sécurité, intégration et stratégie d'isolation Staging.
- DevSecOps Lead : CI/CD, Staging, Production, rollback, isolation Docker (réseaux, volumes, reverse proxy) sur l'environnement Staging mutualisé.
- Release Manager : validation des releases, Gate Production, Gate `STG-ISOL-01` et coordination des mises en production.

## Règles

- Ne jamais supprimer une décision historique.
- Ne jamais supprimer un risque historique.
- Ne jamais supprimer un Gate validé.
- Ajouter les migrations documentairement, de manière additive et idempotente.
- Produire un Plan d'Exécution avant tout codage applicatif.
- Ne jamais déployer en Production sans Gate Production valide.
- Utiliser les statuts v5.3 : `STAGING_READY`, `STAGING_DEPLOYED`, `PRODUCTION_READY`, `PRODUCTION_DEPLOYED`.
- **(v5.4.1)** L'environnement Staging (`ai-test-server`) est mutualisé avec d'autres projets : ne
  jamais exécuter de commande Docker à portée globale (`docker stop $(docker ps -q)`,
  `docker compose down` sans cible, `docker system prune -a`) ; tout déploiement Staging doit
  satisfaire le Gate `STG-ISOL-01` avant promotion.

## Références

- `docs/project-state.md`
- `docs/cgpa/README.md`
- `docs/cgpa/workflows/staging-deployment-workflow.md`
- `docs/cgpa/workflows/production-release-workflow.md`
- `docs/cgpa/workflows/staging-isolation-workflow.md`
- `docs/cgpa/checklists/stg-isol-01-checklist.md`
- `docs/cgpa/adr/ADR-STG-001-staging-isolation.md`
