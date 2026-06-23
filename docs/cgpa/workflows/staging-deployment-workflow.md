# Workflow — Sprint vers Staging

## Objectif

Encadrer le déploiement obligatoire en Staging de tout Sprint validé, sans confondre Staging et Production.

## Déclencheur

Le workflow est déclenché lorsqu'un Sprint est clôturé et que son incrément est validé selon le Plan d'Exécution approuvé.

## Séquence

1. Clôturer le Sprint.
2. Produire ou mettre à jour le rapport d'exécution Sprint.
3. Vérifier les stories terminées, reportées et exclues.
4. Vérifier DEVSECOPS-07 pour l'artefact candidat.
5. Vérifier migrations, secrets, dépendances et images si applicables.
6. Identifier le rollback Staging.
7. Exécuter le Gate Staging.
8. Statuer GO, GO sous réserve ou NO GO.
9. Déployer en Staging si le Gate Staging est GO ou GO sous réserve acceptée.
10. Exécuter les smoke tests Staging.
11. Mettre à jour `docs/staging-state.md`, le rapport Sprint et `docs/project-state.md`.
12. Marquer `STAGING_DEPLOYED` si le déploiement est effectif.

## Règles LoyerTracker

- Tout Sprint validé doit être candidat Staging.
- La clôture technique d'un Sprint ne déclenche jamais la Production.
- Les déploiements Staging utilisent uniquement des images GHCR taguées `sha-<8>`.
- Les réserves Staging doivent être datées, assignées et suivies.

## États projet

| État | Signification |
|------|---------------|
| `STAGING_READY` | Gate Staging validé, déploiement Staging autorisé |
| `STAGING_DEPLOYED` | Artefact déployé en Staging et tracé |

## Sortie attendue

- Rapport Sprint complété.
- Décision Gate Staging tracée.
- Date de déploiement Staging renseignée.
- Validation PO et Release Manager lorsque requise.
- Éligibilité Production indiquée.
