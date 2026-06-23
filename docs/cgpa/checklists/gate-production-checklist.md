# Checklist — Gate Production

## Identification

- [ ] Périmètre Production identifié : Epic, Release ou Hotfix.
- [ ] Version Semantic Versioning identifiée.
- [ ] Artefact, commit ou image identifié.
- [ ] Environnement source identifié.
- [ ] Environnement cible Production identifié.

## Preuves Staging

- [ ] Éléments candidats déployés ou vérifiés en Staging.
- [ ] Statut Staging renseigné.
- [ ] Smoke tests Staging exécutés ou réserves acceptées.
- [ ] Défauts bloquants résolus ou risque accepté.
- [ ] Accumulation Staging analysée.

## Validation fonctionnelle

- [ ] Epic terminé et validé, ou Release fonctionnelle validée, ou Hotfix validé.
- [ ] Validation Product Owner obtenue.
- [ ] Validation Release Manager obtenue.
- [ ] Release notes disponibles.
- [ ] Changelog disponible.

## Contrôles techniques et DevSecOps

- [ ] Build ou artefact Production vérifiable.
- [ ] Tests critiques OK.
- [ ] Contrôles DevSecOps applicables disponibles.
- [ ] SonarQube vérifié si applicable.
- [ ] Migrations Production préparées si applicables.
- [ ] Observabilité minimale définie.
- [ ] Secrets Production non exposés.

## Rollback

- [ ] Stratégie de rollback documentée.
- [ ] Responsable rollback identifié.
- [ ] Conditions de déclenchement du rollback définies.
- [ ] Procédure de restauration testée ou réserve explicitement acceptée.
- [ ] Données et migrations prises en compte.

## Décision

- [ ] Décision GO, GO sous réserve ou NO GO formulée.
- [ ] Réserves documentées, datées et assignées.
- [ ] Statut `PRODUCTION_READY` renseigné si le Gate est validé.
- [ ] Date de déploiement Production renseignée après déploiement.
- [ ] Statut `PRODUCTION_DEPLOYED` renseigné après déploiement effectif.
