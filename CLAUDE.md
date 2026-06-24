# CLAUDE.md — Règles Claude Code CGPA v5.4.1

Claude Code agit comme copilote stratégique et technique dans le cadre CGPA v5.4.1.

## Avant toute action

1. Lire `docs/project-state.md`.
2. Identifier la phase courante et les Gates validés.
3. Vérifier l'impact sur Staging, Production et Release Management.
4. Préserver l'historique des décisions, risques et Gates.

## Responsabilités v5.3 (conservées)

- Respecter la Release Management Policy.
- Vérifier le Gate Staging avant promotion Staging.
- Vérifier le Gate Production avant toute mise en Production.
- Maintenir `docs/project-state.md`, `docs/staging-state.md` et `docs/prod-state.md`.
- Tracer toute réserve datée et assignée.

## Responsabilités v5.4

- L'environnement Staging (`ai-test-server`) est mutualisé avec d'autres projets : vérifier le
  Gate `STG-ISOL-01` avant toute promotion Staging.
- Ne jamais proposer ou exécuter une commande Docker à portée globale
  (`docker stop $(docker ps -q)`, `docker compose down` sans cible, `docker system prune -a`)
  sur cet hôte.
- Maintenir l'inventaire des ressources Staging mutualisées (`docs/staging-state.md` §11).

## Interdictions

- Supprimer ou réécrire une décision historique.
- Supprimer un risque historique.
- Supprimer un Gate validé.
- Confondre clôture Sprint et autorisation Production.
- Démarrer un Sprint ou un Hotfix sans plan ou justification de gouvernance.
- Déployer en Staging mutualisé sans vérifier `STG-ISOL-01`.

## Documents de référence

- `docs/cgpa/README.md`
- `docs/cgpa/migration/migration-report-v5.3.md`
- `docs/cgpa/migration/migration-report-v5.4.md`
- `docs/cgpa/migration/migration-report-v5.4.1.md`
- `docs/cgpa/adr/ADR-STG-001-staging-isolation.md`
- `docs/cgpa/checklists/gate-staging-checklist.md`
- `docs/cgpa/checklists/gate-production-checklist.md`
- `docs/cgpa/checklists/stg-isol-01-checklist.md`
