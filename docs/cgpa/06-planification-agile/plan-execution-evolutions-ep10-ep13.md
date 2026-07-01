# Plan d'Exécution CGPA — Patrimoine enrichi / Devise-Money / Garantie ledger / Fin de bail (prep)

| Champ | Valeur |
|-------|--------|
| Statut | **Proposé — en attente de validation PO.** Aucun Sprint autorisé à démarrer tant que ce plan n'a pas reçu un GO explicite, conformément à la règle CGPA « Codage suspendu : Plan d'Exécution requis avant modification du code ». Chaque sprint ci-dessous reste, en plus, un point de contrôle GO/NO GO indépendant : le franchissement d'un sprint ne vaut pas approbation automatique du suivant. |
| Date | 2026-07-01 (proposé) |
| Analyse d'impact liée | `analyse-impact-evolutions-ep10-ep13.md` |
| Backlog couvert (à créer) | EP-10 (Patrimoine enrichi), EP-11 (Devise/Money), EP-12 (Garantie ledger), note d'architecture EP-13 (Fin de bail, non développée) |
| Niveau | Niveau 3 (changement structurant multi-entités : Patrimoine, Bail, Paiement, Garantie) |
| Dépendance de gouvernance | Indépendant de la clôture de `1.5.0` (Hypercare en cours) — mais aucun Sprint de ce plan ne doit être confondu avec une autorisation de déploiement Production ; chaque promotion Staging/Production suivra son propre Gate, distinct, comme pour tous les sprints précédents. |

---

## Vue d'ensemble et séquencement

```
Sprint 7 (EP-10)        Sprint 8 (EP-11)         Sprint 9 (EP-12a)          Sprint 10 (EP-12b)
Patrimoine enrichi  →   Devise / Money VO   →    Garantie : modèle      →   Garantie : usage,
(champs, migration          (VO + correctif           ledger + migration        réapprovisionnement,
adresse obligatoire)        quittance/avis)           rétroactive                historique, UI
```

**Pourquoi cet ordre :** Sprint 8 (Money) doit précéder Sprint 9/10 (Garantie) car le ledger de garantie affichera des montants (dépôts, retenues, restitutions) qui doivent être devise-aware dès leur conception, pour éviter un second correctif d'affichage juste après (cf. analyse §5, dépendance croisée identifiée). Sprint 7 (Patrimoine) est indépendant et peut être permuté avec Sprint 8 si le PO préfère livrer d'abord le correctif de devise (bug utilisateur visible) — à trancher au kickoff.

Chaque sprint se termine par : suite de tests verte (`mvn verify` / `ng test`), CI complète verte (CodeQL, SonarQube, Gitleaks, Trivy, Packaging), documentation à jour (`CHANGELOG.md`, `docs/project-state.md`), avant fusion `main`. Aucun sprint n'autorise de promotion Staging/Production — Gate distinct requis à chaque fois, comme pour EP-09.

---

## Sprint 7 — EP-10 : Patrimoine enrichi

**Objectif :** ajouter les 7 nouveaux champs optionnels et rendre `adresse` obligatoire, sans régression sur le modèle existant (EP-09).

| Élément | Détail |
|---|---|
| Stories couvertes | US-90 (champs additionnels Patrimoine), US-91 (décision : écran CRUD dédié ou extension du formulaire inline existant — **à trancher au kickoff**) |
| Pré-requis bloquant | Requête de comptage en Production : nombre de patrimoines avec `adresse IS NULL` — condition la stratégie de backfill avant de rendre la colonne `NOT NULL` |
| Livrables | Migration **V19** (`ville, commune, quartier, province_etat, pays, description, reference_interne` + backfill/`NOT NULL` sur `adresse`) ; extension `Patrimoine`/`PatrimoineDto`/`PatrimoineRequest`/`PatrimoineService` ; extension formulaire frontend (ou nouvel écran CRUD selon décision US-91) ; extension export RGPD (`RgpdService`) pour inclure les nouveaux champs ; tests unitaires + intégration étendus |
| Dépendances | Aucune — indépendant d'EP-11/EP-12 |
| Risques | Migration bloquante si des patrimoines Production ont `adresse IS NULL` (à quantifier avant codage) ; confusion avec le travail déjà livré EP-09 (voir analyse §1.1 — à ne pas refaire) |
| Critères GO (fin de sprint) | ✅ 100 % des patrimoines existants ont une `adresse` non nulle après migration, 0 échec de contrainte ✅ Suite de tests existante verte sans régression sur `PatrimoineService`/`AffectationController`/`RgpdService` ✅ Non-régression complète EP-09 (CRUD patrimoine, affectation patrimoine/bien, archivage RS-06) ✅ CI complète verte |

---

## Sprint 8 — EP-11 : Devise / `Money` Value Object

**Objectif :** introduire `Money` et corriger le bug de devise codée en dur dans les documents PDF (quittance, avis d'échéance).

| Élément | Détail |
|---|---|
| Stories couvertes | US-92 (VO `Money` + correctif `DocumentHtmlBuilder`/`QuittanceService`), US-93 (affichage devise sur paiements/honoraires frontend — **portée à confirmer avec le PO**, hors périmètre strict si non retenue) |
| Pré-requis bloquant | Décision PO sur le format d'affichage par devise (EUR/USD/CDF) avant codage (analyse §2.4) |
| Livrables | `Money.java` (VO) ; `DonneesDocument` porte des `Money` au lieu de `BigDecimal` nus ; réécriture de `DocumentHtmlBuilder.euros()` en formatage devise-aware ; réécriture assumée de `DocumentHtmlBuilderTest` (changement de comportement volontaire, documenté) ; retrait du fallback `Devise.EUR` codé en dur dans `BailDto` si confirmé mort ; typage frontend `devise: 'EUR'\|'USD'\|'CDF'` |
| Dépendances | Aucune dépendance technique dure sur Sprint 7 ; ordonnancement avant Sprint 9/10 recommandé (voir vue d'ensemble) |
| Risques | Test verrouillé sur le bug actuel à réécrire consciemment (pas une régression) ; aucune duplication de devise ne doit être introduite sur `Paiement`/`Honoraire` (revue de code dédiée) |
| Critères GO (fin de sprint) | ✅ Quittance et avis d'échéance affichent la devise réelle du bail (EUR/USD/CDF), vérifié sur les 3 devises ✅ Aucune nouvelle colonne devise dupliquée créée hors `Bail` ✅ Suite de tests étendue verte, y compris nouveaux cas USD/CDF ✅ CI complète verte |

---

## Sprint 9 — EP-12a : Garantie, modèle ledger + migration rétroactive

**Objectif :** poser `GarantieMovement`, migrer les garanties existantes en mouvements sans perte d'historique, avant d'exposer tout nouvel usage métier.

| Élément | Détail |
|---|---|
| Stories couvertes | US-94 (modèle `GarantieMovement` + RLS + migration rétroactive) |
| Pré-requis bloquant | Décision PO sur le devenir de `bail.depot_garantie` (champ dupliqué historique, analyse §3.1) ; validation du principe « `garantie.statut` reste une colonne physique de cache recalculé » pour ne pas casser le batch alertes `GARANTIE_NON_RESTITUEE` (analyse §3.3) |
| Livrables | Migration **V20** : création `garantie_movement` (pattern RLS V12), backfill rétroactif (`DEPOT_INITIAL` pour chaque garantie existante + `RESTITUTION`/`AJUSTEMENT` pour les garanties déjà `RESTITUE_PARTIEL`/`RESTITUE_TOTAL`) ; `TypeMouvementGarantie` (enum) ; `GarantieMovementRepository`/`GarantieMovementDto` ; `GarantieService` recalculant le solde depuis les mouvements ; test verrouillant l'invariant solde == somme des mouvements ; test de non-régression du batch alertes |
| Dépendances | Sprint 8 recommandé en amont (Money) pour que les montants du ledger soient devise-aware dès la conception, mais pas techniquement bloquant si le PO préfère inverser l'ordre |
| Risques | **Risque élevé** : migration rétroactive de données réelles de Production (analyse §3.5) — à tester d'abord en Staging avec un export représentatif des garanties réelles, vérification manuelle ligne à ligne avant toute fusion vers `main` |
| Critères GO (fin de sprint) | ✅ 100 % des garanties existantes ont un historique de mouvements cohérent (solde recalculé == `montant - montant_retenu` actuel, vérifié un par un en Staging) ✅ Batch alertes `GARANTIE_NON_RESTITUEE` non régressé (test dédié) ✅ RLS `ENABLE`+`FORCE` vérifiée sur `garantie_movement` ✅ CI complète verte |

---

## Sprint 10 — EP-12b : Garantie, usage métier + réapprovisionnement + historique

**Objectif :** exposer les opérations métier (utilisation manuelle en cas d'impayé, réapprovisionnement) et l'écran d'historique, en s'appuyant sur le ledger posé en Sprint 9.

| Élément | Détail |
|---|---|
| Stories couvertes | US-95 (utilisation garantie sur impayé — choix explicite oui/non + montant, mouvement `RETENUE_LOYER` relié au paiement), US-96 (réapprovisionnement, mouvement `COMPLEMENT`), US-97 (écran historique triable/filtrable/exportable), US-98 (note d'architecture Fin de bail — documentation uniquement, aucun développement) |
| Dépendances | Bloqué par Sprint 9 (le ledger doit exister) |
| Livrables | `paiement.garantie_movement_id` (FK nullable, migration **V21**) ; endpoint retenue explicite (jamais de prélèvement automatique — le gestionnaire choisit oui/non puis le montant) ; endpoint complément ; audit systématique de chaque mouvement (pattern `AuditService.enregistrer`, identique à `EFFACEMENT_LOCATAIRE`) ; export RGPD étendu à l'historique des mouvements ; nouveau composant Angular historique garantie (tri/filtre/export) ; extension `garanties-bail.component.ts` pour le flux de décision retenue ; note d'architecture Fin de bail ajoutée à l'ADR Garantie (aucun code) |
| Risques | Incohérence cross-table `bailleur_id` entre `paiement` et `garantie_movement` non vérifiable par contrainte DB — validation service-level requise (test dédié) |
| Critères GO (fin de sprint) | ✅ Aucun prélèvement automatique possible (vérifié par test : une action explicite du gestionnaire est requise) ✅ Historique reconstitue exactement le solde affiché ✅ Toutes les opérations tracées dans `audit_log` ✅ Export RGPD inclut l'historique des mouvements ✅ CI complète verte |

---

## Documentation et gouvernance transverses (à produire avant/pendant les sprints, pas après)

| Livrable | Sprint associé | Statut |
|---|---|---|
| ADR-12 — Extension champs Patrimoine | Avant Sprint 7 | À produire après validation de ce plan |
| ADR-13 — `Money` Value Object | Avant Sprint 8 | À produire après validation de ce plan |
| ADR-14 — Garantie : compte à mouvements (+ note Fin de bail) | Avant Sprint 9 | À produire après validation de ce plan |
| Addendum backlog EP-10/EP-11/EP-12 (US-90→98) | Avant Sprint 7 | À produire après validation de ce plan |
| Mise à jour ERD / modèle de domaine | Fin de chaque sprint concerné | — |
| `CHANGELOG.md` `[Non publié]` | Fin de chaque sprint | — |
| `docs/project-state.md` (bandeau de cadrage puis clôture par sprint) | Continu | — |

---

## Critères de fusion `main` communs à tous les sprints de ce plan

- `mvn verify` / `ng test` intégralement verts, sans régression sur la suite existante (EP-01→09 inclus).
- CI GitHub Actions complète verte (CodeQL Java/Kotlin + JS/TS, Backend, Frontend, Sécurité Gitleaks/SCA/Trivy, Packaging Docker).
- `CHANGELOG.md` et `docs/project-state.md` à jour avant fusion.
- Aucune suppression de décision, risque ou Gate historique (règle CGPA).
- Aucune promotion Staging/Production automatique — chaque Gate reste distinct et explicite.

---

## Ce que ce plan n'autorise pas

- Aucun codage tant que le PO n'a pas validé ce plan explicitement (GO global ou GO sprint par sprint).
- Aucun Gate Staging/Production pour ces évolutions.
- Aucune modification du backlog déjà validé (`product-backlog.md`, `addendum-patrimoine-backlog.md`) — uniquement des addenda nouveaux (EP-10→12).
- Aucun développement de l'Évolution 7 (Fin de bail) au-delà d'une note d'architecture dans l'ADR-14.
