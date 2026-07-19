# Plan d'Exécution — Remédiation SonarQube PR #235

| Champ | Valeur |
|---|---|
| Date | 2026-07-19 |
| Statut | **Approuvé — instruction explicite du PO reçue le 2026-07-19** |
| Périmètre | PR #235 — Sprint N EP-16, fondation des notifications |
| Déclencheur | Quality Gate backend en échec sur le commit `ee18fcc` |
| Hors périmètre | Staging, Production, Twilio réel, migration supplémentaire, changement fonctionnel |

## Diagnostic factuel

Le job GitHub Actions `Backend (build + tests + couverture)` a terminé en échec pendant l'analyse
SonarQube. Le Quality Gate courant expose :

- `new_coverage` : **83,3 %**, seuil 80 % — PASS ;
- `new_duplicated_lines_density` : **0,0 %**, seuil maximal 3 % — PASS ;
- `new_violations` : **2**, seuil maximal 0 — FAIL.

Les deux violations nouvelles sont :

1. `java:S125` dans `NotificationEvent` : le commentaire du constructeur JPA protégé est interprété
   comme du code commenté ;
2. `java:S107` dans `NotificationPreferenceService.definir(...)` : méthode publique à dix
   paramètres, au-dessus de la limite de sept.

## Exécution autorisée

1. Remplacer le commentaire ambigu du constructeur JPA par une formulation documentaire qui ne
   ressemble pas à du code désactivé.
2. Introduire un objet de commande typé pour regrouper les paramètres de définition d'une
   préférence, puis adapter `NotificationPreferenceService.definir(...)` sans changer les règles
   de consentement ni la persistance.
3. Exécuter Spotless et `mvn verify`.
4. Pré-valider le Quality Gate contre l'instance SonarQube réelle.
5. Synchroniser la branche avec `main` par merge non destructif, résoudre les conflits le cas
   échéant et rejouer les vérifications pertinentes.
6. Mettre à jour additivement l'historique documentaire, committer, pousser et surveiller la CI
   GitHub jusqu'à son état terminal.

## Critères de clôture

- aucune nouvelle violation SonarQube backend ;
- couverture nouvelle supérieure ou égale à 80 % et duplication nouvelle inférieure ou égale à
  3 % ;
- `mvn verify` vert ;
- branche PR #235 synchronisée avec `main` ;
- checks GitHub Actions terminaux et verts, Packaging Docker inclus ;
- aucun déploiement ni changement de statut Staging/Production.
