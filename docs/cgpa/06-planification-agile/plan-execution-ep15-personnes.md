# Plan d'Exécution CGPA — EP-15 : Gestion des personnes

| Champ | Valeur |
|---|---|
| Date | 2026-07-08 |
| Origine | Instruction PO du 2026-07-08 (« Introduire EP-14 — Gestion des personnes » — renuméroté **EP-15**, EP-14 étant déjà pris par les Quittances certifiées release `1.9.0`, EP-13 réservé « Fin de bail ») |
| Backlog couvert | EP-15 — US-105 → US-114 (`addendum-backlog-ep15-personnes.md`) |
| ADR | **ADR-16** (D-PERS-001, acceptée — kickoff clos) — statut global Gestionnaire, RLS Locataire, fonction `SECURITY DEFINER` cross-tenant, bytea photo, décisions D1/D3/D5/D8/K1 tranchées par le PO |
| Release cible | À déterminer au kickoff (ce plan ne préjuge pas d'un numéro — dépend du rythme d'exécution ; **aucune promotion Production du Sprint C seul sans Sprint B stabilisé**, cf. §Ce que ce plan n'autorise pas) |
| Prérequis | Release `1.9.0` clôturée (CDO GO 2026-07-08) ✅ — aucun sprint en cours |

## Kickoff — clos

| # | Question | Décision PO | Statut |
|---|---|---|---|
| K1 | Sémantique de « créer » un Gestionnaire : aucun flux de création directe n'existe aujourd'hui (seule l'invitation crée le compte Keycloak, `AcceptationService`). Introduire un flux administratif en contournement, ou « créer » = compléter pour la première fois le profil métier d'un compte déjà créé par invitation ? | **Profil sur compte existant** — l'invitation reste l'unique voie de création technique du compte | ✅ **Tranché par le PO le 2026-07-08** |

Décisions tranchées par le PO le 2026-07-08 (ADR-16) — **non rejouées ici** : statut
Gestionnaire global (D1) ; migration 1 Locataire par bail historique, sans déduplication
automatique (D3) ; suppression des colonnes texte libre `bail.locataire_nom`/
`locataire_email` après bascule (D3) ; photo en `bytea` PostgreSQL (D5) ; K1 (profil sur
compte existant, ci-dessus). **Kickoff clos — seul le GO explicite du PO sur ce Plan
d'Exécution reste requis avant le démarrage du Sprint A.**

## Vue d'ensemble et séquencement

```
Sprint A                      Sprint B                        Sprint C
Gestionnaire (statut    →     Locataire (nouvelle       →     Bascule Bail→Locataire
global, cycle de vie)         entité, V24 additive)            (V25 non additive) + RGPD
US-105→108 (V23, livrée)      US-109→112                       US-113→114
        └────────── Gate Staging par sprint ──────────┘
                              Sprint B doit tenir en Production ≥ 1 cycle de release complet
                              avant que Sprint C ne soit instruit (rollback V25 = backup seul)
```

> **Renumérotation actée au Sprint B (2026-07-08)** : V23 ayant été intégralement consommée
> par le Gestionnaire seul (Sprint A, livrée), Locataire est porté par **V24** et la bascule
> par **V25** — aucune décision de fond changée, cf. ADR-16 D3.

**Pourquoi ce découpage** : le Gestionnaire (statut global, hors RLS) et le Locataire (nouvelle
entité RLS-scopée) sont indépendants l'un de l'autre — aucune raison de les coupler dans un
même sprint. La bascule `Bail → Locataire` (V25) est en revanche **non additive** (suppression
de colonnes, rollback applicatif non viable, ADR-16 D3/RSV-EP15-03) : elle est isolée dans un
sprint C distinct, instruit seulement après que le Sprint B (création de l'entité Locataire,
additive) a tourné sans anomalie en Production pendant au moins un cycle de release complet —
même logique de prudence que la séparation V21 (additive) / V20 (non additive, `RSV-S9-03`)
déjà pratiquée sur ce projet.

## Sprint A — Gestionnaire : statut global, cycle de vie

> **Codé et vert le 2026-07-08** — `mvn verify` 168/168, aucune régression. Reste à instruire :
> Gate Staging du Sprint A (dont `STG-ISOL-01`). Voir `CHANGELOG.md` `[Non publié]` et
> `docs/project-state.md` §11/§14 pour le détail.

| Champ | Valeur |
|---|---|
| Objectif | Le Gestionnaire gagne un profil enrichi et un cycle de vie de compte (suspension/réactivation/archivage/restauration), sans toucher au mécanisme d'affectation existant |
| Stories | **US-105** (profil + suspendre/réactiver), **US-106** (archivage conditionné cross-tenant + restauration), **US-107** (recherche + doublons), **US-108** (historique) |
| Livrables | Migration **V23** (partie Gestionnaire uniquement : colonnes `statut`/`telephone`/`photo`/`date_creation`/`date_suspension`/`date_archivage`/`observations`) ; fonction SQL `SECURITY DEFINER gestionnaire_a_affectation_active(gestionnaire_id)` (booléen uniquement, ADR-16 D4) ; nouveau `GestionnaireController` (profil, suspension, réactivation, archivage, restauration, recherche, doublons, historique) ; nouveaux points d'audit (`CREER_GESTIONNAIRE_PROFIL`, `MODIFIER_GESTIONNAIRE`, `SUSPENDRE_GESTIONNAIRE`, `REACTIVER_GESTIONNAIRE`, `ARCHIVER_GESTIONNAIRE`, `RESTAURER_GESTIONNAIRE`) ; désactivation/réactivation Keycloak (`enabled`) pilotée par l'application (première fois que ce mécanisme est déclenché applicativement, jusqu'ici manuel en exploitation) ; tests unitaires + intégration (cycle de vie, cross-tenant, RBAC « un Gestionnaire n'administre jamais un autre Gestionnaire ») |
| Hors périmètre | Locataire (Sprint B), bascule `Bail` (Sprint C) |
| Dépendances | Kickoff K1 tranché ✅ (2026-07-08) ; GO explicite du PO sur ce Plan d'Exécution |
| Risques | Fonction `SECURITY DEFINER` mal bornée (revue sécurité dédiée avant Gate Staging) ; profil partagé mutable cross-bailleur (RSV-EP15-01, accepté) |
| Critères GO (fin de sprint) | ✅ `mvn verify`/`ng test` verts sans régression ✅ RBAC : un Gestionnaire ne peut agir sur aucun autre Gestionnaire (test dédié) ✅ archivage bloqué si affectation active chez un tiers (test cross-tenant) ✅ V23 (partie Gestionnaire) rollback applicatif viable ✅ CI complète verte ✅ Gate Staging (dont `STG-ISOL-01`) — **pas de promotion Production isolée avant Sprint B** (cohérence produit : les deux entités du même Epic) |

## Sprint B — Locataire : nouvelle entité (V24 additive)

| Champ | Valeur |
|---|---|
| Objectif | Le Locataire devient une entité de domaine persistante, cloisonnée par bailleur (RLS), avec cycle de vie, recherche et historique — sans encore être référencé par `Bail` |
| Stories | **US-109** (création/modification), **US-110** (archivage/restauration), **US-111** (recherche + doublons), **US-112** (historique) |
| Livrables | Migration **V24** (table `locataire` complète, RLS `ENABLE`+`FORCE` policy `bailleur_isolation`, index `locataire(bailleur_id)`/`locataire(bailleur_id, statut)`) ; `bail.locataire_id` ajouté **nullable** (préparation Sprint C, aucun usage applicatif dans ce sprint) ; nouveau `LocataireController` (CRUD, archivage, restauration, recherche, doublons, historique) ; audit (`CREER_LOCATAIRE`, `MODIFIER_LOCATAIRE`, `ARCHIVER_LOCATAIRE`, `RESTAURER_LOCATAIRE`) ; tests unitaires + intégration (RLS cross-tenant, cycle de vie, recherche/doublons) |
| Hors périmètre | Aucun rattachement de `Bail` à `Locataire` (Sprint C) ; RGPD toujours ciblé sur `Bail` dans ce sprint (inchangé, Sprint C l'adapte) |
| Dépendances | Aucune (indépendant du Sprint A) |
| Risques | Volumétrie `photo` (bytea) à surveiller au préflight (précédent `quittance.pdf`, sans incident connu) |
| Critères GO (fin de sprint) | ✅ tests verts sans régression ✅ RLS cross-tenant prouvée (aucune fuite) ✅ V24 additive, rollback applicatif viable ✅ CI complète verte ✅ Gate Staging (dont `STG-ISOL-01`) → **Gate Production possible pour Sprints A+B combinés** (statuts Gestionnaire + entité Locataire, sans bascule `Bail` encore active) |

## Sprint C — Bascule `Bail → Locataire` (V26 non additive) & adaptation RGPD

> **Renumérotation actée au GO Sprint C (2026-07-17)** : cette bascule était désignée « V25 » au
> moment de ce plan. V25 a entretemps été consommée par une migration EP-13 (« fin de bail »,
> sans rapport, déjà en Production `1.11.0`). Elle porte donc désormais le numéro **V26** —
> aucune décision de fond modifiée, cf. `cadrage-sprint-c-ep15.md` §6.

| Champ | Valeur |
|---|---|
| Objectif | Chaque bail référence un `Locataire` structuré ; l'effacement RGPD cible la personne plutôt qu'un bail isolé |
| Stories | **US-113** (migration V26 : backfill + `NOT NULL` + suppression `locataire_nom`/`locataire_email`), **US-114** (adaptation `RgpdService`/`Bail.anonymiserLocataire()` vers `Locataire`) |
| Livrables | Migration **V26** (backfill 1 `Locataire` par bail historique sans déduplication, `bail.locataire_id NOT NULL`, `DROP COLUMN locataire_nom, locataire_email`) ; extension des endpoints existants de création/modification de bail (`locataireId` obligatoire, validations 404/403/409) ; réécriture ciblée de `RgpdService.anonymiserLocataire()` (cible `Locataire`) ; suite de tests RGPD existante rejouée sans régression ; documentation de la rupture de contrat HTTP de `Bail` (release notes) |
| Hors périmètre | Aucune nouvelle fonctionnalité Gestionnaire/Locataire — uniquement la bascule et l'adaptation RGPD |
| Dépendances | **Sprint B stabilisé en Production depuis au moins un cycle de release complet, sans anomalie remontée** (condition de démarrage explicite, cf. séquencement) |
| Risques | RSV-EP15-02 (parsing nom/prénom imprécis, accepté) ; **RSV-EP15-03 (rollback applicatif non viable, restauration de backup requise)** — Préflight de cette release doit vérifier un backup post-migration immédiatement disponible avant tout arrêt, conformément à la discipline déjà pratiquée pour V20 (même risque, migration désormais V26) |
| Critères GO (fin de sprint) | ✅ backfill à 100 % (0 bail sans `locataire_id`) ✅ tests RGPD existants verts sans régression ✅ CI complète verte ✅ Gate Staging (dont `STG-ISOL-01`, avec vérification explicite du backfill en conditions réelles) → **Gate Production distinct**, checklist habituelle renforcée (préflight backup **avant et après** migration, la restauration étant le seul chemin de rollback) |

## Gouvernance transverse

| Artefact | Échéance |
|---|---|
| ADR-16 acceptée (D1/D3/D5/D8/K1 tranchés) | ✅ 2026-07-08 |
| Addendum backlog EP-15 (US-105→114) | Produit avec ce plan |
| `CHANGELOG.md` `[Non publié]` au fil des sprints | Chaque fusion `main` |
| `docs/project-state.md` / `staging-state.md` / `prod-state.md` | Chaque Gate |
| Diagramme modèle de données (ajout `locataire`, colonnes `gestionnaire`) | Sprint A/B |
| Release notes (numéro à déterminer) | Avant Gate Production de chaque lot promu |
| Revue sécurité dédiée (fonction `SECURITY DEFINER` cross-tenant) | Avant Gate Staging Sprint A |

## Checklist de validation CGPA (avant tout codage)

- [x] `docs/project-state.md` lu, phase courante identifiée (CGPA v5.4.1, post-clôture `1.9.0`)
- [x] Aucune décision, Gate ou risque historique supprimé ou réécrit
- [x] Numérotation vérifiée sans collision (EP-15, US-105→114, ADR-16, EF-97→107, RM-100→107, ENF-91/92, V23/V24/V25)
- [x] Impact Staging/Production/Release Management analysé (aucun déploiement à ce stade ; `docs/prod-state.md` vérifié sans modification requise)
- [x] Kickoff K1 tranché par le PO (2026-07-08 — profil sur compte existant)
- [x] Plan d'Exécution approuvé (GO explicite du PO le 2026-07-08) — **Sprint A démarré**
- [ ] Sprint A/B/C instruits un par un, chacun avec son propre Gate Staging et sa propre décision Gate Production

## Ce que ce plan n'autorise pas

- Aucun codage avant l'approbation explicite de ce Plan d'Exécution par le PO (CLAUDE.md).
- Aucune instruction du Sprint C avant que le Sprint B ne soit stabilisé en Production sans
  anomalie (condition de démarrage explicite ci-dessus).
- Aucune promotion Staging/Production sans son Gate (dont `STG-ISOL-01` sur l'hôte mutualisé).
- Aucune modification des décisions historiques (ADR-01/EF-05 restent vrais pour le périmètre
  d'accès par `Affectation` ; seule une couche de statut de compte global est ajoutée, ADR-16).
- Aucune correction de l'asymétrie `BienService.archiver()` (RSV-EP15-04, hors périmètre) sous
  couvert de ce lot.
