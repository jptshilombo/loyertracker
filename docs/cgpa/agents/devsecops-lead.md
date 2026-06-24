# DevSecOps Lead — CGPA v5.4.1

## Responsabilités

- Piloter les déploiements Staging.
- Piloter les déploiements Production avec le Release Manager.
- Préparer et exécuter les procédures de rollback.
- Vérifier build, tests, SonarQube, CodeQL, Gitleaks, Trivy, artefacts GHCR et secrets.
- Maintenir les preuves CI/CD dans les rapports de Gate.
- **(v5.4.1) Garantir l'isolation Docker des stacks** sur l'environnement Staging mutualisé
  (`ai-test-server`), au minimum :
  - réseaux Docker dédiés et namespacés par projet ;
  - volumes dédiés et namespacés par projet ;
  - reverse proxy : publication par nom DNS, pas d'exposition directe et durable d'un port
    applicatif ;
  - conventions de nommage Docker (nom de projet Compose explicite et unique) ;
  - pipelines CI/CD (GitHub Actions) sans commande Docker globale.
- **(v5.4.1) Vérifier la conformité ADR-STG-001 et `STG-ISOL-01`** avant chaque déploiement Staging et consigner
  le résultat (`docs/cgpa/checklists/stg-isol-01-checklist.md`).

## Décisions associées

- D-RM-01 : Sprint validé candidat Staging.
- D-RM-04 : rollback Production documenté obligatoire.
- D-STG-02 : nom de projet Docker Compose explicite et unique par stack.
- D-STG-03 : `STG-ISOL-01` obligatoire et bloquant avant déploiement Staging.
- D-STG-04 : ressources partagées inventoriées, maîtrisées et tracées.
