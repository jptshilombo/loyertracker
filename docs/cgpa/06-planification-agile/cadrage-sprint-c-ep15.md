# Cadrage — Sprint C (EP-15 « Gestion des personnes »)

| Champ | Valeur |
|---|---|
| Date | 2026-07-16 |
| Origine | Instruction PO du 2026-07-16 (« cadrer le Sprint C EP-15 ») |
| Portée | Documentaire uniquement — **aucun code, aucune migration, aucun déploiement** |
| Référence | `docs/cgpa/06-planification-agile/plan-execution-ep15-personnes.md` §Sprint C (approuvé au GO EP-15 du 2026-07-08) ; `docs/cgpa/06-planification-agile/addendum-backlog-ep15-personnes.md` US-113/US-114 ; ADR-16 D3/D6 |

## 1. Objet de ce cadrage

Le Plan d'Exécution EP-15 approuvé le 2026-07-08 spécifie déjà intégralement le Sprint C
(objectif, stories, livrables, risques, critères GO) — voir §Sprint C de ce plan. Ce document ne
rouvre **aucune** de ces décisions déjà tranchées (ADR-16 D3/D6, RSV-EP15-02/03). Son unique objet
est de vérifier, à la date du jour, si la **condition de démarrage explicite** du Sprint C est
remplie, conformément à la règle CGPA « aucune instruction du Sprint C avant que le Sprint B ne
soit stabilisé en Production sans anomalie ».

## 2. Rappel du périmètre (inchangé)

| # | Story | Contenu |
|---|-------|---------|
| US-113 | Bascule `Bail → Locataire` (migration **V25 non additive**) | Backfill 1 `Locataire` par bail historique (sans déduplication, RSV-EP15-02 accepté) ; `bail.locataire_id` rendu `NOT NULL` ; suppression des colonnes `locataire_nom`/`locataire_email` ; endpoints bail existants exigent désormais un `locataireId` valide (404/403/409) |
| US-114 | Adaptation de l'effacement RGPD | `RgpdService.anonymiserLocataire()` recible sur `Locataire` (et non plus `Bail`) ; suite de tests RGPD existante rejouée sans régression |

**Hors périmètre** (rappel, inchangé) : toute nouvelle fonctionnalité Gestionnaire/Locataire ;
correction de l'asymétrie `BienService.archiver()` (RSV-EP15-04, hors EP-15).

**Risque non additif assumé** : RSV-EP15-03 — la migration V25 supprime des colonnes ; le seul
chemin de rollback est la restauration d'un backup (pas de rollback applicatif), même discipline
que V20 (Sprint 9 Garantie ledger). Le Préflight de la release qui portera le Sprint C devra donc
vérifier un backup disponible **avant et après** la migration.

## 3. Vérification de la condition de démarrage

> Condition inscrite au Plan d'Exécution : *« Sprint C ne peut démarrer qu'après confirmation que
> V24 (Sprint B) est stable en Production depuis au moins un cycle de release complet (aucune
> anomalie remontée) — critère GO explicite requis. »*

| Critère | État constaté au 2026-07-16 |
|---|---|
| V24 (Sprint B) en Production | ✅ Oui — déployée avec `1.10.0` (`sha-c9200a51`), `PRODUCTION_DEPLOYED` le 2026-07-15 ~13:41 UTC |
| Hypercare `1.10.0` complète (T0/T+12/T+24) | ⏳ **Partielle** — T0 PASS (2026-07-15), T+12 PASS (2026-07-16, rattrapage), **T+24 restant à instruire** (cible 2026-07-16 ~12:39 UTC) |
| Clôture de release `1.10.0` (décision CDO) | ❌ **Non prononcée** — `docs/cgpa/09-production/cloture-release-v1.10.0.md` n'existe pas encore |
| Cycle de release complet sans anomalie | ❌ **Non encore établi** — ne peut être constaté qu'après clôture |

Aucune anomalie n'a été remontée sur V24/Sprint B à ce stade (T0 et T+12 PASS, aucun critère de
suspension atteint) — mais le critère du Plan d'Exécution exige un **cycle de release complet**,
pas seulement l'absence d'incident à mi-parcours.

## 4. Verdict de cadrage

**NO-GO (pas encore) pour l'instruction du Sprint C.** Le périmètre est confirmé stable et ne
nécessite aucune nouvelle décision (contrairement au Sprint A qui attendait K1) ; **seule la
condition de démarrage temporelle reste à satisfaire**. Aucun codage, aucune branche de
développement Sprint C ne doit être ouvert avant que les deux jalons suivants soient francs :

1. Checkpoint T+24 de l'hypercare `1.10.0` instruit (PASS) ;
2. Décision CDO de clôture de la release `1.10.0` (`cloture-release-v1.10.0.md`), confirmant un
   cycle de release complet sans anomalie sur V24.

## 5. Prochaine étape

Dès ces deux jalons francs, un **GO explicite du PO** sur l'instruction du Sprint C (démarrage du
codage US-113/US-114) reste requis avant tout travail de développement — ce cadrage ne constitue
pas cette autorisation.
