# Rapport de migration des Epics — CGPA v5.4

> Fichier versionné distinct de `docs/cgpa/migration/epics-migration-report.md` (v5.3, conservé
> tel quel). Ajoute la lecture `STG-ISOL-01` à l'évaluation de chaque Epic / lot.

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-24 |
| Projet | LoyerTracker |
| Décision | **GO sous réserve** |

## Epics historiques du backlog initial

| Epic | État | Production | Lecture v5.4 |
|------|------|------------|--------------|
| EP-01 Socle & infrastructure | Terminé | Déployé (`1.0.0`) | Hôte Production dédié, hors périmètre `STG-ISOL-01` ; Staging amont couvert rétroactivement |
| EP-02 Comptes, auth & délégation | Terminé | Déployé (`1.0.0`) | Idem |
| EP-03 Biens, baux & affectation/rotation | Terminé | Déployé (`1.0.0`) | Idem |
| EP-04 Paiements & garanties | Terminé | Déployé (`1.0.0`) | Idem |
| EP-05 Honoraires | Terminé | Déployé (`1.0.0`) | Idem |
| EP-06 Moteur d'alertes & batch | Terminé | Déployé (`1.0.0`) | Idem |
| EP-07 Tableaux de bord & cloisonnement | Partiellement terminé | Partiellement déployé | Inchangé par v5.4 (dette UX, non liée à l'isolation) |
| EP-08 RGPD, tests sécu & durcissement | Partiellement terminé | Partiellement déployé | Inchangé par v5.4 |

## Epics / lots post-go-live

| Epic / lot | État | Prêt Production | Lecture v5.4 (`STG-ISOL-01`) |
|------------|------|-----------------|---------------------------------|
| Quittances de loyer | Terminé et mergé | Candidat | Déployé en Staging avant formalisation v5.4 ; couvert rétroactivement, aucune nouvelle action |
| Patrimoine Sprint 1 | Terminé et mergé | Non (promotion Staging dédiée requise) | Tout redéploiement Staging futur de ce lot devra exécuter `STG-ISOL-01` explicitement |
| Patrimoine Sprint 2 | Terminé et mergé | Non (décision Epic/Release requise) | Idem |
| Patrimoine Sprint 3 | Non démarré | Non | S'appliquera au premier déploiement Staging de ce lot |

## Évaluation v5.4

- Aucun Epic n'est techniquement bloqué par `STG-ISOL-01` : l'architecture Staging (namespace,
  réseau, volumes, ports, reverse proxy) satisfait déjà les exigences v5.4 (audit v5.4 §8, Gate
  `STG-ISOL-01` PASS).
- La gouvernance v5.4 ajoute une **condition de promotion** (et non une condition fonctionnelle)
  : tout Epic candidat à une nouvelle promotion Staging doit s'appuyer sur un Gate `STG-ISOL-01`
  PASS daté pour ce déploiement précis.
- Aucun Epic historique n'est rejoué rétroactivement contre `STG-ISOL-01` : les déploiements
  Staging antérieurs au 2026-06-24 restent couverts par le constat de PASS rétrospectif de
  l'audit v5.4.

## Recommandation

**GO sous réserve** : la migration v5.4 est conforme pour l'ensemble des Epics existants ;
la prochaine promotion Staging d'un Epic post-go-live (Patrimoine Sprint 3 ou ultérieur) doit
exécuter explicitement le contrôle `STG-ISOL-01` et lever la réserve RSV-STG-01 (confirmation
live) à cette occasion.
