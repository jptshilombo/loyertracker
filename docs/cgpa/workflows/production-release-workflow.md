# Workflow — Production Release

## Objectif

Encadrer le passage en Production d'un ensemble fonctionnel validé, sans automatiser la Production à la fin de chaque Sprint.

## Déclencheurs autorisés

- Epic terminé et validé.
- Release fonctionnelle validée.
- Hotfix validé.

## Workflow Epic vers Production

1. Identifier l'Epic candidat.
2. Vérifier que les stories de l'Epic sont terminées ou explicitement exclues.
3. Vérifier les preuves Staging représentatives.
4. Vérifier les validations fonctionnelles et métier.
5. Vérifier les risques ouverts et leur acceptation.
6. Obtenir la validation Product Owner.
7. Obtenir la validation Release Manager.
8. Exécuter le Gate Production.
9. Déployer uniquement si le Gate Production est GO ou GO sous réserve acceptée.

## Workflow Release vers Production

1. Identifier la version Semantic Versioning.
2. Stabiliser le périmètre fonctionnel.
3. Vérifier changelog et release notes.
4. Vérifier preuves Staging, DevSecOps et observabilité.
5. Vérifier le rollback Production documenté.
6. Obtenir validation PO et Release Manager.
7. Exécuter Gate Production.
8. Déployer, contrôler, puis marquer `PRODUCTION_DEPLOYED`.

## Workflow Hotfix vers Production

1. Documenter l'incident ou le risque métier.
2. Limiter le correctif au strict nécessaire.
3. Exécuter les tests critiques.
4. Tracer l'impact Staging et Production.
5. Vérifier rollback et restauration.
6. Obtenir validation PO et Release Manager.
7. Exécuter Gate Production accéléré.

Le circuit Hotfix ne supprime jamais le Gate Production ; il adapte seulement le niveau de documentation au risque.

## États projet

| État | Signification |
|------|---------------|
| `PRODUCTION_READY` | Gate Production validé, déploiement Production autorisé |
| `PRODUCTION_DEPLOYED` | Artefact déployé en Production et tracé |
