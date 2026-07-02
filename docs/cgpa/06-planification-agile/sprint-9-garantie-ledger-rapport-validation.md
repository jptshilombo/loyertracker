# Sprint 9 — Garantie : modèle ledger + migration rétroactive (EP-12a) — Rapport de validation

| Champ | Valeur |
|-------|--------|
| Statut | **✅ Implémenté et validé localement — GO technique.** Reste : PR dédiée + CI GitHub avant fusion `main`. |
| Date | 2026-07-02 |
| Stories couvertes | US-94 (modèle `GarantieMovement` + RLS + migration rétroactive) |
| Décision liée | ADR-14 (D-GAR-001, statut **Acceptée**), Plan d'Exécution `plan-execution-evolutions-ep10-ep13.md` |

## 1. Décision de kickoff (2026-07-02)

Arbitrage PO tranché avant codage (ADR-14 §8, seul prérequis bloquant du sprint) : **`bail.depot_garantie` devient une valeur dérivée du ledger**, plus jamais saisie ni stockée sur `bail`.

Conséquences d'implémentation (détail : ADR-14 §8) :
- Colonne `bail.depot_garantie` **supprimée** (migration V20) — plus de double source de vérité.
- `BailRequest.depotGarantie` retiré : le dépôt ne se saisit plus à la création du bail (aucune
  `Garantie` n'existe encore à cet instant), mais via le flux « Ajouter garantie » déjà existant.
- `BailDto.depotGarantie` reste exposé (API inchangée pour les consommateurs existants) mais
  devient **calculé** : somme de `garantie.montant` pour les garanties rattachées au bail — un
  bail fraîchement créé affiche `0`. Cette simplification (plutôt qu'une requête sur
  `garantie_movement`) est documentée comme valable tant que `COMPLEMENT` n'est pas exposé métier
  (Sprint 10) ; à revisiter à ce moment-là.

## 2. Livrables techniques

### Migration V20 (`garantie_movement` + backfill)

- Table `garantie_movement` (RLS `ENABLE`+`FORCE`, policy `bailleur_isolation`, même patron que
  V12) : `id, bailleur_id, garantie_id, date_mouvement, type, debit, credit, solde_apres, motif,
  utilisateur, commentaire, reference_document, cree_le`.
- `TypeMouvementGarantie` (CHECK, pas d'ENUM natif — convention du projet) : `DEPOT_INITIAL,
  COMPLEMENT, RETENUE_LOYER, RETENUE_CHARGES, RETENUE_REPARATION, RESTITUTION, AJUSTEMENT,
  ANNULATION`.
- `garantie.solde_actuel` : cache transactionnel (NUMERIC, `CHECK >= 0`), recalculé de façon
  synchrone à chaque mouvement — `garantie.statut` reste inchangé, aucune rupture du batch
  d'alertes `GARANTIE_NON_RESTITUEE` (lecture directe SECURITY DEFINER, V9/V10).
- **Backfill rétroactif**, pour chaque garantie existante :
  - `DEPOT_INITIAL` (credit = montant) — toujours.
  - `AJUSTEMENT` (debit = montant_retenu) si `montant_retenu > 0` — retenue historique, motif
    conservé, type précis inconnu pour les données pré-existantes.
  - `RESTITUTION` (debit = solde restant) si `statut = RESTITUE_TOTAL` — ramène le solde à 0.
  - Invariant vérifié : `DETENU`/`RESTITUE_PARTIEL` → solde = `montant - montant_retenu` ;
    `RESTITUE_TOTAL` → solde = `0`.
- `ALTER TABLE bail DROP COLUMN depot_garantie`.

### Backend

- `TypeMouvementGarantie` (enum), `GarantieMovement` (entité, append-only — jamais mutée après
  création), `GarantieMovementRepository`, `GarantieMovementDto`.
- `Garantie` : ajout `soldeActuel` (cache), `restituerTotal()`/`restituerPartiel(...)` ajustent
  désormais le solde en plus du statut. `GarantieDto` expose `soldeActuel`.
- `GarantieService` : `creer()` et `restituer()` journalisent désormais un mouvement (dans la
  même transaction, via `enregistrerMouvement(...)`) en plus de l'audit déjà existant — chaque
  mouvement est lui-même audité (`AuditService.enregistrer`, entité `garantie_movement`).
- `GarantieRepository.sommeMontantDeposeParBail(bailId)` : valeur dérivée pour
  `BailDto.depotGarantie`.
- `Bail`/`BailRequest`/`BailDto`/`BailService`/`RgpdService` : mis à jour pour retirer la saisie
  et le stockage de `depotGarantie`, désormais calculé (`BigDecimal.ZERO` à la création, somme
  des garanties rattachées pour l'historique et l'export RGPD).

### Frontend

- `BailPayload` (`s02-api.service.ts`) : `depotGarantie` retiré (n'est plus envoyé à la création).
  `Bail` (lecture) inchangé — le champ reste affiché, désormais calculé côté backend.
- Formulaires « Nouveau bail » (dashboards bailleur et gestionnaire) : champ « Dépôt » retiré (le
  dépôt se déclare via le flux « Ajouter garantie » existant, après création du bail).

### Tests

- `S03PaiementsGarantiesIntegrationTest.ledgerGarantieEnregistreChaqueMouvementEtSoldeCoherent` :
  cycle complet création → restitution partielle → restitution totale, vérifie à chaque étape le
  mouvement enregistré (type, débit/crédit), `solde_actuel`, l'audit associé, et l'invariant
  `solde == somme(credit) - somme(debit)`.
- `GarantieLedgerBackfillMigrationTest` (nouveau, package `com.loyertracker.db`) : migration
  arrêtée à V19, insertion de 3 garanties représentant les 3 états métier (`DETENU`,
  `RESTITUE_PARTIEL`, `RESTITUE_TOTAL`), migration menée à son terme (V20), vérification
  ligne à ligne du backfill (mouvements générés, solde recalculé, invariant, suppression de
  `bail.depot_garantie`) — équivalent automatisé de la vérification manuelle demandée par le Plan
  d'Exécution avant tout déploiement Staging réel.
- `SchemaMigrationTest` : compteur de migrations mis à jour (19 → 20).
- Payloads de test existants (`S02BiensBauxAffectationsIntegrationTest`,
  `S03PaiementsGarantiesIntegrationTest`, `S04HonorairesIntegrationTest`,
  `S04AlertesAuditIntegrationTest`, `DocumentGenerationIntegrationTest`, `RgpdIntegrationTest`,
  `BailVentilationTest`) mis à jour pour ne plus envoyer/attendre `depotGarantie` à la création
  d'un bail.

## 3. Validation locale

| Suite | Résultat |
|---|---|
| Backend (`mvn -o verify`) | **128/128** tests, Spotless propre, JaCoCo (couverture cloisonnement) OK |
| Frontend (`ng build`) | ✅ build production sans erreur |
| Frontend (`ng test --browsers=ChromeHeadless`) | **63/63** tests |
| `ng lint` | Non exécuté dans cet environnement (outillage local indisponible) — à confirmer en CI |

## 4. Non-régression

- Batch d'alertes `GARANTIE_NON_RESTITUEE` (`S04AlertesAuditIntegrationTest`) : **non régressé**
  (5/5) — confirme que la lecture directe de `garantie.statut` par le batch `SECURITY DEFINER`
  continue de fonctionner sans modification, condition posée par ADR-14 §3.
- Cycle de vie garantie existant (`cycleVieGarantie`, transitions DETENU→RESTITUE_PARTIEL→
  RESTITUE_TOTAL, rejet 409 sur double restitution totale) : **non régressé**.
- Isolation cross-bailleur sur garantie (`accesFinancierRefuseCrossBailleur`) : **non régressé**.
- Traçabilité audit (`ecrituresFinancieresJournalisees`) : **non régressé** — les nouveaux audits
  de mouvement (`DEPOT_INITIAL`, `AJUSTEMENT`, `RESTITUTION`) s'ajoutent sans casser les
  assertions filtrées par action existantes.
- RLS : `garantie_movement` suit le même patron `bailleur_isolation` que toutes les tables
  métier — aucune dérogation.

## 5. Critères GO (fin de sprint) — statut

| Critère | Statut |
|---|---|
| 100 % des garanties existantes ont un historique de mouvements cohérent (solde recalculé == montant - montant_retenu actuel, sauf RESTITUE_TOTAL → 0) | ✅ Vérifié automatiquement (`GarantieLedgerBackfillMigrationTest`, 3 scénarios + invariant) |
| Batch alertes `GARANTIE_NON_RESTITUEE` non régressé | ✅ |
| RLS `ENABLE`+`FORCE` vérifiée sur `garantie_movement` | ✅ (même patron que `patrimoine`/V12) |
| CI complète verte | À confirmer sur la PR |

## 6. Périmètre non couvert par ce sprint (Sprint 10, US-95→98)

Conformément au Plan d'Exécution, ce sprint pose le **modèle** et migre l'historique — il n'expose
aucun nouvel usage métier :
- Pas de distinction typée des retenues (`RETENUE_LOYER`/`CHARGES`/`REPARATION`) : la restitution
  partielle existante utilise `AJUSTEMENT` (motif libre), en attendant le flux de décision
  explicite US-95.
- Pas de `COMPLEMENT` utilisable (réapprovisionnement, US-96).
- Pas d'écran d'historique des mouvements (US-97) ni d'endpoint dédié — seuls le modèle et la
  persistance existent ; `BailDto.depotGarantie` reste calculé depuis `garantie.montant` en
  attendant que `COMPLEMENT` soit exposé.
- Note d'architecture Fin de bail (US-98) : déjà couverte par ADR-14 §7 (aucun développement).

## 7. Reste à faire avant clôture définitive du sprint

- Ouvrir la PR dédiée, obtenir CI complète verte (CodeQL, SonarQube, Gitleaks, Trivy, Packaging).
- Vérification manuelle du backfill en Staging avec les données réelles (le Plan d'Exécution
  demande une vérification « ligne à ligne » sur les garanties réelles avant fusion `main`,
  au-delà de la couverture automatisée ci-dessus) — à réaliser au Gate Staging de ce sprint,
  non couvert par ce rapport.
