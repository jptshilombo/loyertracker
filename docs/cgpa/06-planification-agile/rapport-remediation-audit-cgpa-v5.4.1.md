# Rapport de remédiation — Audit CGPA v5.4.1

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| Décision d'entrée | **GO sous réserve** |
| Branche | `codex/remediation-audit-cgpa-v5.4.1` |
| Pull request | [PR #83](https://github.com/jptshilombo/loyertracker/pull/83) — draft |
| Périmètre environnement | Dépôt et GitHub uniquement |
| Déploiement | **Aucun déploiement Staging ou Production** |

## 1. Résumé exécutif

Les recommandations de l'audit CGPA v5.4.1 ont été appliquées sur la PR #83. Les contrôles
locaux et GitHub Actions sont verts. La protection de `main`, Dependabot et les mises à jour de
sécurité automatiques sont actifs. Les corrections couvrent la gouvernance, la supply chain,
CodeQL, le rollback documentaire et les écarts UX/UI ciblés.

La décision de sortie est **GO technique pour fusion de la PR #83, sous réserves de clôture** :

1. fusionner la PR #83 après revue ;
2. vérifier sur `main` la fermeture de l'alerte CodeQL SQL #2 ;
3. vérifier sur `main` la disparition des trois alertes Dependabot de sévérité haute.

Cette décision ne confère aucun statut `STAGING_READY`, `STAGING_DEPLOYED`,
`PRODUCTION_READY` ou `PRODUCTION_DEPLOYED`. Toute promotion reste soumise à un Gate Staging
avec `STG-ISOL-01` live, puis à un Gate Production distinct.

## 2. Remédiations réalisées

### Gouvernance et documentation

- Plan d'Exécution produit avant les changements applicatifs.
- État du Sprint 4, PR #82, version `1.2.1` et absence d'autorisation de promotion clarifiés.
- Commandes de rollback `1.2.1` corrigées pour combiner systématiquement la base Compose et
  l'overlay Production.
- Historique des décisions, risques et Gates préservé ; aucune suppression ni rejeu de Gate.

### DevSecOps, CI/CD et supply chain

- Protection stricte de `main` activée : PR obligatoire, branche à jour, conversations résolues,
  administrateurs inclus, force-push et suppression interdits.
- Checks requis : Backend, Frontend, Sécurité, Packaging Docker et CodeQL pour Java/Kotlin et
  JavaScript/TypeScript.
- Le dépôt n'ayant qu'un seul collaborateur, le nombre d'approbations obligatoires est fixé à
  zéro afin d'éviter un verrouillage opérationnel. La revue par PR et tous les checks restent
  obligatoires.
- Dependabot, les alertes de vulnérabilités et les correctifs de sécurité automatiques activés.
- Actions GitHub et images Docker épinglées par SHA ou digest immuable.
- Configuration Dependabot ajoutée pour Maven, npm, GitHub Actions et Docker.

### Sécurité

- Concaténation SQL du test `SchemaMigrationTest` remplacée par une requête paramétrée. L'alerte
  CodeQL #2 restera visible sur la branche par défaut jusqu'à la fusion de la correction.
- Alerte CodeQL #1 CSRF qualifiée `won't fix` avec justification : API OAuth2 Resource Server
  stateless, authentification exclusivement par Bearer JWT explicite, sans session ni cookie
  d'authentification.
- Dépendances Angular de build mises à jour et `http-proxy-middleware` forcé en `3.0.7`.
- Audit de la branche : **0 vulnérabilité critique ou haute** ; dette résiduelle de 5 modérées et
  5 faibles, limitée à la chaîne de build/développement.

### UX/UI

- Lien d'évitement et cible `main` ajoutés.
- Focus clavier visible, prise en charge de `prefers-reduced-motion` et responsive renforcé.
- États dynamiques annoncés avec `aria-live`.
- Aide et erreurs de saisie UUID associées au champ.
- Confirmation explicite avant archivage et révocation, avec test du chemin d'annulation.

## 3. Validation

| Contrôle | Résultat |
|---|---|
| Frontend lint | PASS |
| Frontend build Production | PASS ; avertissement CommonJS historique `js-sha256` |
| Frontend tests | **53/53 PASS** avec couverture |
| Backend `mvn verify` | PASS |
| Compose dev, staging, production et monitoring | PASS statique |
| `git diff --check` | PASS |
| GitHub Actions PR `28287572272` | PASS intégral |
| GitHub Actions push `28287571532` | PASS intégral |
| SonarQube frontend/backend | Quality Gates PASS |
| CodeQL Java/Kotlin et JavaScript/TypeScript | PASS |
| Gitleaks, SCA, Trivy et packaging Docker | PASS |

## 4. Avis des rôles CGPA

| Rôle | Avis |
|---|---|
| Governance Officer | GO sous réserve de fusion et preuve post-merge |
| Enterprise Architect | GO ; aucune dérive de contrat, schéma ou isolation |
| DevSecOps Lead | GO ; CI verte, protection et surveillance actives |
| Release Manager | GO pour fusion uniquement ; aucune promotion d'environnement autorisée |
| Chief Delivery Officer | **GO technique sous réserves de clôture** |

## 5. Risques et dette résiduels

| ID | État | Traitement |
|---|---|---|
| RA-01 — verrouillage protection | Maîtrisé | 0 approbation imposée dans le dépôt mono-collaborateur ; checks stricts maintenus |
| RA-02 — régression confirmation | Fermé | Tests frontend du chemin d'annulation |
| RA-03 — alertes CodeQL | Partiellement fermé | CSRF qualifiée ; SQL corrigé, fermeture attendue après fusion |
| RA-04 — références flottantes | Fermé | Actions et images épinglées, CI verte |
| RA-05 — dérive documentaire | Fermé | États et rollback réalignés |
| DT-01 — dépendances build | Ouvert non bloquant | 5 modérées et 5 faibles ; suivi Dependabot |
| DT-02 — runtime Actions | Ouvert non bloquant | Deux Actions tierces utilisent encore Node 20 forcé en Node 24 par GitHub |

## 6. Décision finale

**GO technique sous réserves de clôture pour fusionner la PR #83.** Le lot de remédiation est
validé mais n'est pas déclaré intégré tant qu'il n'est pas fusionné sur `main`. Après fusion, le
Release Manager doit contrôler CodeQL et Dependabot sur la branche par défaut et consigner la
levée des deux réserves restantes. Aucun déploiement n'a été exécuté ou autorisé.
