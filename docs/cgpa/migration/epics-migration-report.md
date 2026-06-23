# Rapport de migration des Epics — CGPA v5.3

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Projet | LoyerTracker |
| Décision | **GO sous réserve** |

## Epics historiques du backlog initial

| Epic | État | Production | Commentaire |
|------|------|------------|-------------|
| EP-01 Socle & infrastructure | Terminé | Déployé | Inclus en `1.0.0` |
| EP-02 Comptes, auth & délégation | Terminé | Déployé | Inclus en `1.0.0` |
| EP-03 Biens, baux & affectation/rotation | Terminé | Déployé | Inclus en `1.0.0` |
| EP-04 Paiements & garanties | Terminé | Déployé | Inclus en `1.0.0` |
| EP-05 Honoraires | Terminé | Déployé | Inclus en `1.0.0` |
| EP-06 Moteur d'alertes & batch | Terminé | Déployé | Inclus en `1.0.0` |
| EP-07 Tableaux de bord & cloisonnement | Partiellement terminé | Partiellement déployé | Dashboards minimaux et audit couverts ; dashboards avancés restent à cadrer |
| EP-08 RGPD, tests sécu & durcissement | Partiellement terminé | Partiellement déployé | Tests sécu/durcissement présents ; export/effacement RGPD non terminé |

## Epics / lots post-go-live

| Epic / lot | État | Prêt Production | Blocage |
|------------|------|-----------------|---------|
| Quittances de loyer | Terminé et mergé | Candidat | Nécessite décision Release/Gate Production si inclusion dans prochaine release |
| Patrimoine Sprint 1 | Terminé et mergé | Non | Promotion Staging et validation fonctionnelle requises |
| Patrimoine Sprint 2 | Terminé et mergé | Non | Promotion Staging et décision Epic/Release requises |
| Patrimoine Sprint 3 | Non démarré | Non | Cadrage PO requis (`EXCLUSION`, UX avancée) |

## Évaluation v5.3

- Epics terminés et en production : `PRODUCTION_DEPLOYED` par héritage Gate 10 pour la release `1.0.0`.
- Epics post-go-live : prêts à entrer dans le workflow `Epic -> Production` uniquement après preuves Staging.
- Aucun Epic bloqué techniquement ; les blocages sont des décisions de promotion et de périmètre.

## Recommandation

**GO sous réserve** : la migration v5.3 est conforme, mais la prochaine production doit être pilotée par Epic ou Release avec Gate Production explicite.
