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
7. **Exécuter le contrôle d'isolation Staging `STG-ISOL-01`** (CGPA v5.4, environnement mutualisé) —
   cf. `docs/cgpa/workflows/staging-isolation-workflow.md` et
   `docs/cgpa/checklists/stg-isol-01-checklist.md`.
8. Exécuter le Gate Staging (intègre le résultat `STG-ISOL-01`).
9. Statuer GO, GO sous réserve ou NO GO. Un `STG-ISOL-01` `FAIL` non excepté impose `NO GO`.
10. Déployer en Staging si le Gate Staging est GO ou GO sous réserve acceptée.
11. Exécuter les smoke tests Staging.
12. Mettre à jour `docs/staging-state.md` (y compris l'inventaire des ressources mutualisées
    §11), le rapport Sprint et `docs/project-state.md`.
13. Marquer `STAGING_DEPLOYED` si le déploiement est effectif.

## Règles LoyerTracker

- Tout Sprint validé doit être candidat Staging.
- La clôture technique d'un Sprint ne déclenche jamais la Production.
- Les déploiements Staging utilisent uniquement des images GHCR taguées `sha-<8>`.
- Les réserves Staging doivent être datées, assignées et suivies.
- L'hôte Staging (`ai-test-server`) est **mutualisé** : tout déploiement doit satisfaire
  `STG-ISOL-01` (D-STG-01 à D-STG-05, CGPA v5.4) avant promotion.

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
