# Rapport de migration des Sprints actifs — CGPA v5.3

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Projet | LoyerTracker |
| Décision | **GO sous réserve** |

## Synthèse

Le projet n'a pas de Sprint de développement actif identifié comme en cours dans `docs/project-state.md`. Le dernier incrément actif côté documentation est le lot Patrimoine `[Non publié]`, avec Sprint 1 et Sprint 2 clôturés côté `main`.

## Sprints analysés

| Sprint | État réel | Éligibilité Staging | Éligibilité Production | Décision v5.3 |
|--------|-----------|---------------------|------------------------|---------------|
| S01 Socle/Auth | Terminé, intégré, inclus en `1.0.0` | Déjà couvert par staging historique | Déjà en production `1.0.0` | `PRODUCTION_DEPLOYED` par héritage Gate 10 |
| S02 Biens/Baux/Affectations | Terminé, intégré, inclus en `1.0.0` | Déjà couvert par staging historique | Déjà en production `1.0.0` | `PRODUCTION_DEPLOYED` par héritage Gate 10 |
| S03 Paiements/Garanties | Terminé, intégré, inclus en `1.0.0` | Déjà couvert par staging historique | Déjà en production `1.0.0` | `PRODUCTION_DEPLOYED` par héritage Gate 10 |
| S04 Honoraires/Alertes/Audit | Terminé, intégré, inclus en `1.0.0` | Déjà couvert par staging historique | Déjà en production `1.0.0` | `PRODUCTION_DEPLOYED` par héritage Gate 10 |
| Sprint 1 Patrimoine | Clôturé côté `main` via PR #73 | Éligible Staging, promotion dédiée requise | Non éligible sans Gate Production | `STAGING_READY` sous réserve de décision de promotion |
| Sprint 2 Patrimoine | Clôturé côté `main` via PR #74, smoke réaligné PR #75/#77, correctif V14 PR #76 | Éligible Staging après Gate Staging v5.3 | Non éligible Production sans Epic/Release validé | `STAGING_READY` recommandé, Production différée |
| Sprint 3 Patrimoine | Non démarré | Non éligible | Non éligible | À cadrer |

## Réserves

- La clôture technique Sprint 1/2 Patrimoine ne vaut pas déploiement Staging automatique.
- La production reste `1.0.0`; les éléments Patrimoine sont dans `[Non publié]`.
- Un Gate Staging v5.3 dédié est requis avant de marquer `STAGING_DEPLOYED` pour le lot Patrimoine.

## Recommandation

**GO sous réserve** : autoriser la migration v5.3, puis planifier un Gate Staging pour le lot Patrimoine avant toute promotion.
