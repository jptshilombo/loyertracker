# Rapport d'Exécution — S04 Lot 1 (Honoraires de gestion)

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker · 0.1.0-SNAPSHOT |
| Phase CGPA | 7 — Développement · Gate 07 (non statué) |
| Sprint | S04 — EP-05 Honoraires & pilotage, lot 1 (honoraires) |
| Stories | US-40 (honoraires de gestion, EF-50/51/52) |
| Branche | `feat/s04-honoraires` |
| Date | 2026-06-10 · Agent : Claude Code |
| Plan de référence | Plan d'Exécution S04 (Niveau 3), approuvé par le PO le 2026-06-10 (défauts A–D) |

## 1. Périmètre exécuté

Backend uniquement (arbitrage A), lot 1 sur 2 (arbitrage D). Calcul des honoraires de gestion par
affectation et période, déclenché à la fois en synchrone au pointage (arbitrage C) et par batch.

- **Migration V8** `db/migration/V8__s04_honoraires.sql` : fonction SQL
  `calculer_honoraires(p_bien_id uuid DEFAULT NULL)`, `SECURITY DEFINER` propriété de
  `loyertracker_batch` (BYPASSRLS), `GRANT EXECUTE` à `loyertracker_api` — même patron que V6/V7.
  Upsert idempotent sur `uq_honoraire_periode (affectation_id, periode)` (V1, EF-51) :
  - **POURCENTAGE** : `round(montant_honoraires / 100 × loyer encaissé, 2)` ;
  - **FORFAIT** : `montant_honoraires` (fixe) ;
  - calcul borné à la fenêtre de l'affectation `[date_debut, date_fin]` et aux affectations `ACTIVE` ;
  - **gel à PAYE (EF-52)** garanti en base : `DO UPDATE ... WHERE honoraire.statut <> 'PAYE'` ;
  - idempotence stricte : `AND honoraire.montant IS DISTINCT FROM EXCLUDED.montant` (un re-run sans
    changement renvoie 0) ;
  - `p_bien_id` NULL = recalcul complet (batch) ; non-null = recalcul ciblé (hook). Le `bailleur_id`
    étant dérivé de l'affectation, le ciblage ne crée aucune fuite inter-tenant.
- **Package `honoraires/`** : `Honoraire` (entity, `periode` CHAR(7)), `StatutHonoraire`
  (DU/EN_ATTENTE/PAYE), `HonoraireRepository` (lecture par bien via jointure affectation, sous RLS),
  `HonoraireDto`, `StatutRequest`, `HonoraireService`, `HonoraireController`.
- **Hook synchrone** : `PaiementService.pointer` appelle `HonoraireService.recalculerPourBien(bienId)`
  dans la même transaction/tenant après chaque pointage (l'encaissement modifie l'assiette
  POURCENTAGE).
- **Endpoints** :
  - `GET /api/biens/{bienId}/honoraires` — bailleur propriétaire **ou** gestionnaire affecté actif
    (ReBAC `@authz.peutAccederBien`) ;
  - `PATCH /api/honoraires/{id}/statut` — transition de statut **réservée au bailleur**
    (`hasRole('BAILLEUR')`, gestionnaire → 403) ; honoraire figé si déjà PAYE (409) ; action
    journalisée `VALIDER_HONORAIRE`.
- **Batch** : `POST /api/batch/honoraires` (BAILLEUR) et recalcul quotidien ajouté au
  `EcheancesScheduler` (filet de sécurité, après loyers/retards).

## 2. Conformité au plan & écarts assumés

Conforme aux arbitrages A–D. Deux précisions par rapport au plan :

1. **Validation entièrement réservée au bailleur** : le plan listait « PAYE réservé au BAILLEUR ».
   La transition de statut est restée **intégralement** BAILLEUR-only (plus stricte) — c'est le
   bailleur qui pilote le cycle de paiement des honoraires dus à son gestionnaire ; le gestionnaire
   consulte mais ne modifie pas. Choix plus sûr, cohérent avec EF-52.
2. **Défense en profondeur au-delà de la RLS** : `changerStatut` vérifie explicitement
   `honoraire.bailleurId == tenant` (fail-closed 404). Cet endpoint ne porte pas de `bienId` (donc
   pas de garde ReBAC) ; le contrôle applicatif protège le cloisonnement même là où les tests
   d'intégration (connexion superutilisateur) n'exercent pas la RLS — et reste une 2ᵉ couche en
   production.

## 3. Fichiers

**Créés** : `db/migration/V8__s04_honoraires.sql` ; package `honoraires/` (`Honoraire.java`,
`StatutHonoraire.java`, `HonoraireRepository.java`, `HonoraireDto.java`, `StatutRequest.java`,
`HonoraireService.java`, `HonoraireController.java`) ; test
`s04/S04HonorairesIntegrationTest.java`.
**Modifiés** : `paiements/PaiementService.java` (hook), `batch/BatchController.java` (endpoint),
`batch/EcheancesScheduler.java` (recalcul quotidien), `db/SchemaMigrationTest.java` (8 migrations),
`SecurityIntegrationTest.java` (mock `HonoraireService`).

## 4. Tests & résultats

- `mvn verify` : **BUILD SUCCESS — 49 tests, 0 échec** (46 + 3 S04). Spotless OK ; gate de
  couverture JaCoCo (`com.loyertracker.securite`) : « All coverage checks have been met ».
  `SchemaMigrationTest` valide **8** migrations (V8 appliquée).
- `S04HonorairesIntegrationTest` :
  - `honorairesPourcentageRecalculesAuPointagePuisFigesAPaye` — 10 % × 850 = 85,00 au pointage ;
    après validation PAYE, un nouvel encaissement plus faible ne modifie plus le montant (gel) ;
  - `honorairesForfaitFixesEtBatchIdempotent` — FORFAIT 50,00 indépendant du loyer encaissé ; 2ᵉ
    passage du batch → 0 (idempotent) ;
  - `validationReserveeAuBailleurEtCloisonnement` — gestionnaire consulte (200) mais ne valide pas
    (403) ; bailleur tiers : consultation refusée (ReBAC 403), validation introuvable (404).

## 5. DoD

- [x] Code conforme CDC/plan · [x] Tests d'intégration (calcul, idempotence, gel, autorisation,
  cloisonnement) · [x] Audit des écritures (VALIDER_HONORAIRE) · [x] Aucun secret en clair ·
  [x] Migration Flyway versionnée (V8) · [x] Spotless / couverture / build verts · [x] `ddl-auto=validate`
  satisfait (table V1 inchangée).

## 6. Reste à faire (hors lot 1)

- **S04 Lot 2** : alertes automatiques (US-50/51 — LOYER_EN_RETARD, FIN_BAIL, GARANTIE_NON_RESTITUEE ;
  PREAVIS reporté, arbitrage B), scoping des alertes (US-52), consultation de l'audit (US-62).
- Suivis reconduits : conversion des tests d'intégration au double datasource (fidélité RLS) ;
  smoke test runtime sous le rôle `loyertracker_api` ; frontend S04 (plan ultérieur).

## 7. Décision attendue

Acceptation du lot 1 Honoraires (GO / GO sous réserve) et autorisation d'ouvrir la PR. Le merge dans
`main` reste décision de gate du PO.
