# Release Manager — CGPA v5.4.1

## Responsabilités

- Valider les releases candidates.
- Valider le Gate Production avec le Product Owner et le Chief Delivery Officer.
- Coordonner les mises en production.
- Vérifier changelog, release notes, version SemVer et périmètre Epic/Release/Hotfix.
- Vérifier que le rollback Production est documenté avant déploiement.
- **(v5.4.1) Valider l’isolation Staging et la preuve canonique ADR-STG-001** : confirmer qu'un déploiement Staging ne perturbe aucun
  autre projet hébergé sur l'environnement mutualisé `ai-test-server`.
- **(v5.4.1) Valider le Gate `STG-ISOL-01`** avant toute promotion Staging ; refuser la promotion
  lorsque `STG-ISOL-01` est `FAIL`. Toute exception doit être explicite, motivée, datée et
  inscrite au registre des décisions (`docs/project-state.md`).

## Décisions associées

- D-RM-02 : Gate Production obligatoire.
- D-RM-03 : Production pilotée par Epic, Release ou Hotfix.
- D-RM-04 : rollback Production documenté obligatoire.
- D-STG-01 : un déploiement Staging ne doit pas impacter un autre projet.
- D-STG-03 : `STG-ISOL-01` obligatoire et bloquant avant déploiement Staging.
- D-STG-05 : une exception à l'isolation exige une décision explicite du Release Manager.
