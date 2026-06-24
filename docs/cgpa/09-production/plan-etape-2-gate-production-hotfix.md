# Plan détaillé — Étape 2 : Gate Production accéléré du Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Statut | **Exécutée le 2026-06-24 — GO sous réserve acceptée** |
| Plan directeur | `docs/cgpa/09-production/plan-execution-hotfix-production.md` |
| Candidat | `docs/cgpa/09-production/release-candidate-v1.1.1-hotfix.md` |
| Version | `1.1.1` |
| Artefact | `sha-0adc4941` |
| Production actuelle | `1.1.0` — `sha-05424aa3` |

## 1. Objectif

Statuer formellement le Gate Production accéléré du Hotfix `1.1.1` selon une décision
**GO**, **GO sous réserve** ou **NO GO**, sans exécuter de sauvegarde, de préflight ou de
déploiement.

Un Gate valide pourra établir `PRODUCTION_READY`. Il n'établira jamais
`PRODUCTION_DEPLOYED`.

## 2. Conditions d'entrée

L'exécution du Gate est interdite tant que les éléments suivants ne sont pas disponibles :

- Étape 1 clôturée avec décision **candidat recevable** ;
- commit et digests GHCR figés ;
- dossier candidat et release notes `1.1.1` disponibles ;
- preuves CI, CodeQL, sécurité et SonarQube vertes ;
- preuves Staging sur `sha-0adc4941` ;
- rollback applicatif vers `sha-05424aa3` documenté ;
- validation du présent plan par le PO.

## 3. Périmètre du Gate

### Inclus

- Hotfix patrimoine/bien ;
- création du patrimoine par défaut à l'inscription ;
- formulaire bien avec patrimoine et type ;
- jackson-databind 2.21.4 ;
- artefacts API et Web `sha-0adc4941`.

### Exclus

- bug CORS Compose ;
- commits postérieurs au candidat ;
- Sprint 3 Patrimoine ;
- toute migration SQL ou évolution d'infrastructure ;
- toute commande sur l'hôte Production.

## 4. Rôles et responsabilités

| Rôle | Responsabilité pendant le Gate |
|---|---|
| Governance Officer | Audite la checklist, la traçabilité, les réserves et la cohérence documentaire |
| DevSecOps Lead | Confirme artefacts, preuves CI/CD, sécurité, observabilité et rollback |
| Enterprise Architect | Confirme l'absence de dérive architecture/données et l'alignement Staging/Production |
| Release Manager | Valide le candidat, le périmètre, les release notes et les conditions de promotion |
| Product Owner | Confirme l'acceptation fonctionnelle du Hotfix et des réserves |
| Chief Delivery Officer | Rend la décision finale GO / GO sous réserve / NO GO |

## 5. Contrôles prévus

### 5.1 Identification

- Type de mouvement : Hotfix.
- Version : `1.1.1`.
- Commit : `0adc4941f854304a3f7412b04294615b05403707`.
- Images et digests conformes au dossier candidat.
- Source : Staging `ai-test-server`.
- Cible : Production dédiée `loyertracker-prod-server`.

Tout écart d'identité d'artefact impose **NO GO**.

### 5.2 Preuves Staging

Vérifier :

- `sha-0adc4941` effectivement déployé ;
- 4/4 services healthy ;
- smoke 47/0 ;
- parcours navigateur réel avec création de bien 201 ;
- nettoyage post-test ;
- `STG-ISOL-01 = PASS`.

`RSV-STG-01` reste ouverte. Elle peut être acceptée comme réserve non bloquante, car elle porte
sur le renouvellement de la preuve live d'isolation et non sur un défaut constaté du candidat.

### 5.3 Validation fonctionnelle

- Incident initial et cause documentés.
- Hotfix limité au strict nécessaire.
- Tests du flux d'inscription et du formulaire bien disponibles.
- Validation PO du parcours Production déjà tracée.
- Validation spécifique du Gate à obtenir pendant son exécution.
- Release notes et changelog disponibles.

### 5.4 DevSecOps et sécurité

- CI `28089960893` SUCCESS.
- CodeQL `28089960897` SUCCESS.
- Backend, frontend, Gitleaks, SCA, Trivy et Packaging Docker verts.
- Quality Gates SonarQube backend/frontend `OK`.
- Digests GHCR immuables identifiés.
- Aucune migration Flyway V15+.
- Aucun changement Compose, infrastructure ou secret.
- Observabilité Production existante et inchangée.

Un check requis rouge, absent ou non attribuable au candidat impose **NO GO**.

### 5.5 Analyse du rollback

Vérifier :

- tag précédent `sha-05424aa3` disponible ;
- rollback applicatif par redéploiement ciblé ;
- absence de rollback de schéma nécessaire ;
- responsable : DevSecOps Lead sous coordination Release Manager ;
- déclencheurs : service unhealthy, smoke KO, erreur critique d'authentification, régression du
  flux bien/onboarding ou alerte sécurité ;
- sauvegarde pré-déploiement obligatoire à l'Étape 3 malgré l'absence de migration.

Le Gate ne réalise pas le backup et ne teste pas le rollback.

### 5.6 Risques et réserves

| Risque ou réserve | Traitement proposé au Gate |
|---|---|
| `RSV-STG-01` — preuve live d'isolation à renouveler | Réserve non bloquante, maintenue ouverte |
| Bug CORS Compose distinct | Acceptable uniquement s'il reste explicitement hors périmètre et inchangé |
| Commits postérieurs à `0adc494` | Interdiction de substitution silencieuse |
| Backup non encore produit | Normal à cette étape ; obligation bloquante de l'Étape 3 |
| Rollback non exécuté pour ce Hotfix | Acceptable sous réserve du tag précédent vérifié et du préflight dédié |

## 6. Critères de décision

### GO

Tous les critères sont satisfaits sans réserve ayant un impact sur la sécurité, les données ou
la capacité de rollback.

### GO sous réserve

Autorisé uniquement pour une réserve :

- non bloquante ;
- datée et assignée ;
- sans effet sur l'identité de l'artefact ;
- sans effet sur les tests, la sécurité, les données ou le rollback ;
- acceptée explicitement par PO, Release Manager et Chief Delivery Officer.

### NO GO

Obligatoire si :

- artefact ou digest incohérent ;
- preuve Staging insuffisante ou attachée à un autre tag ;
- check CI/CodeQL/SonarQube/Sécurité requis non vert ;
- changement non expliqué dans le périmètre ;
- rollback non défini ou tag précédent indisponible ;
- secret exposé ;
- validation PO ou Release Manager absente ;
- `STG-ISOL-01 = FAIL` non excepté.

## 7. Mutations autorisées pendant l'exécution

Uniquement :

- création de `docs/cgpa/09-production/gate-production-v1.1.1-hotfix-decision.md` ;
- mise à jour de la checklist Gate Production avec preuves ;
- mise à jour du dossier candidat, release notes et Project State ;
- marquage `PRODUCTION_READY` si et seulement si le Gate est GO ou GO sous réserve acceptée.

Interdits :

- accès Production en écriture ;
- sauvegarde ;
- pull d'image sur Production ;
- commande Docker ;
- modification de secret ;
- marquage `PRODUCTION_DEPLOYED`.

## 8. Preuves de sortie attendues

- checklist complète avec statut et preuve pour chaque critère ;
- avis des cinq rôles ;
- réserves datées, assignées et acceptées ou rejetées ;
- décision finale motivée ;
- document de Gate dédié ;
- statut `PRODUCTION_READY` cohérent avec la décision ;
- confirmation explicite qu'aucun déploiement n'a été effectué.

## 9. Point de décision après le Gate

- **GO / GO sous réserve accepté** : autorise uniquement la production du plan détaillé de
  l'Étape 3 — préflight Production et sauvegarde.
- **NO GO** : interdit toute suite ; retour à la correction du candidat ou des preuves.

## 10. Résultat d’exécution

Le Gate a été exécuté selon ce plan. Décision formelle : `docs/cgpa/09-production/gate-production-v1.1.1-hotfix-decision.md`.

- Décision : **GO sous réserve acceptée**.
- `PRODUCTION_READY` : atteint.
- `PRODUCTION_DEPLOYED` : non atteint.
- Aucun accès Production, backup ou déploiement exécuté.

## 11. Prochaine action autorisée

Produire le plan détaillé de l’Étape 3 — préflight Production et sauvegarde.
