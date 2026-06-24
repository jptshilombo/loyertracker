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

## Isolation Staging (STG-ISOL-01, CGPA v5.4)

> Sur tout environnement Staging mutualisé (plusieurs projets sur le même hôte). Checklist
> détaillée : `docs/cgpa/checklists/stg-isol-01-checklist.md`. Workflow :
> `docs/cgpa/workflows/staging-isolation-workflow.md`.

- [ ] Nom de projet Compose explicite et unique vérifié.
- [ ] Réseaux et volumes dédiés au projet vérifiés (namespace Docker).
- [ ] Absence de conflit de ports avec les autres projets hébergés vérifiée.
- [ ] Reverse proxy mutualisé : publication par nom DNS confirmée, aucune modification de la
  configuration des autres projets.
- [ ] Absence de commande Docker globale dans le pipeline et les procédures de déploiement vérifiée.
- [ ] Contrôle `STG-ISOL-01` exécuté : résultat **PASS** ou **FAIL** consigné.
- [ ] Si `FAIL` : Gate Staging **NO GO**, sauf exception explicite, motivée, datée et inscrite au
  registre des décisions par le Release Manager.

## Décision

- [ ] Décision GO, GO sous réserve ou NO GO formulée.
- [ ] `STG-ISOL-01` = `PASS` (ou exception tracée) requis pour toute décision GO/GO sous réserve.
- [ ] Statut `STAGING_READY` renseigné si le Gate est validé.
- [ ] Date de déploiement Staging renseignée après déploiement.
- [ ] Statut `STAGING_DEPLOYED` renseigné après déploiement effectif.
- [ ] Éligibilité Production indiquée.
