# Rapport de migration des Sprints — CGPA v5.4

> Fichier versionné distinct de `docs/cgpa/migration/sprints-migration-report.md` (v5.3, conservé
> tel quel). Complète l'analyse v5.3 par l'impact du contrôle `STG-ISOL-01` sur la promotion
> Staging des Sprints.

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-24 |
| Projet | LoyerTracker |
| Décision | **GO sous réserve** |

## Synthèse

L'état fonctionnel des Sprints est inchangé depuis le rapport v5.3 : aucun Sprint de
développement n'est actif. Le changement introduit par v5.4 ne porte pas sur l'éligibilité
fonctionnelle des Sprints, mais sur une **condition supplémentaire de promotion Staging** :
`STG-ISOL-01` doit être `PASS` (ou exception tracée) avant toute promotion future.

## Sprints analysés (rétrospective et impact v5.4)

| Sprint | État réel (v5.3) | Déploiements Staging historiques | Impact `STG-ISOL-01` |
|--------|-------------------|-----------------------------------|------------------------|
| S01 Socle/Auth | `PRODUCTION_DEPLOYED` par héritage Gate 10 | Déployé en Staging le 2026-06-14 (lot 4b) | Antérieur à v5.4, non rejoué ; isolation de fait déjà conforme (cf. audit v5.4 §8) |
| S02 Biens/Baux/Affectations | `PRODUCTION_DEPLOYED` par héritage Gate 10 | Idem S01 | Idem |
| S03 Paiements/Garanties | `PRODUCTION_DEPLOYED` par héritage Gate 10 | Idem S01 | Idem |
| S04 Honoraires/Alertes/Audit | `PRODUCTION_DEPLOYED` par héritage Gate 10 | Idem S01 | Idem |
| Sprint 1 Patrimoine | `STAGING_READY` sous réserve (clôturé `main` PR #73) | Déployé en Staging (`sha-0adc4941` et tags antérieurs) | Déploiements antérieurs au Gate `STG-ISOL-01` formel ; couverts rétroactivement par le PASS historique (audit v5.4 §8) — non rejoué |
| Sprint 2 Patrimoine | `STAGING_READY` recommandé (clôturé `main` PR #74, smoke PR #75/#76/#77) | Déployé en Staging (`sha-75473413`, `sha-0adc4941`) | Idem Sprint 1 |
| Sprint 3 Patrimoine | Non démarré | — | S'appliquera pleinement au premier déploiement Staging post-v5.4 : `STG-ISOL-01` obligatoire avant `STAGING_DEPLOYED` |

## Application prospective (D-STG-03)

Tout futur déploiement Staging (Sprint 3 Patrimoine ou tout lot ultérieur) doit :

1. Exécuter le workflow `docs/cgpa/workflows/staging-isolation-workflow.md`.
2. Consigner le résultat `STG-ISOL-01` dans `docs/cgpa/checklists/stg-isol-01-checklist.md`.
3. Obtenir un Gate Staging incluant ce résultat avant `STAGING_DEPLOYED`.

## Réserves

- Réserves v5.3 conservées : accumulation Staging (RSV-RM-01), décision de promotion du lot
  Patrimoine `[Non publié]`.
- Nouvelle réserve v5.4 : RSV-STG-01 (confirmation live de `STG-ISOL-01`) à lever au prochain
  déploiement Staging réel, quel que soit le Sprint concerné.

## Recommandation

**GO sous réserve** : la migration v5.4 n'invalide aucun état de Sprint existant ; elle conditionne
la **prochaine** promotion Staging au Gate `STG-ISOL-01`, déjà statué `PASS` sur preuves
documentaires (`docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md`).
