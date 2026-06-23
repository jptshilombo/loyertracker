# Checklist — Gate Staging

## Identification

- [ ] Sprint identifié.
- [ ] Plan d'Exécution approuvé.
- [ ] Rapport d'exécution Sprint disponible.
- [ ] Commit, artefact ou image candidat identifié.
- [ ] Environnement Staging identifié.

## Critères Sprint

- [ ] Stories terminées listées.
- [ ] Stories exclues ou reportées listées.
- [ ] Écarts au plan acceptés.
- [ ] Validation Product Owner obtenue ou réserve tracée.
- [ ] Validation Release Manager obtenue ou réserve tracée.

## Contrôles DevSecOps

- [ ] Build stable.
- [ ] Tests unitaires requis exécutés.
- [ ] Tests d'intégration requis exécutés.
- [ ] Contrôles secrets/SCA/SAST/images exécutés.
- [ ] SonarQube vérifié si applicable.
- [ ] Migrations DB vérifiées si applicables.
- [ ] Secrets non exposés.

## Déploiement Staging

- [ ] Rollback Staging identifié.
- [ ] Tag immuable `sha-<8>` ou artefact équivalent identifié.
- [ ] Smoke tests Staging prévus.
- [ ] `docs/staging-state.md` prêt à être mis à jour.

## Décision

- [ ] Décision GO, GO sous réserve ou NO GO formulée.
- [ ] Statut `STAGING_READY` renseigné si le Gate est validé.
- [ ] Date de déploiement Staging renseignée après déploiement.
- [ ] Statut `STAGING_DEPLOYED` renseigné après déploiement effectif.
- [ ] Éligibilité Production indiquée.
