# Rapport d'Exécution — S03 Paiements & Garanties (lot backend)

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker · 0.1.0-SNAPSHOT |
| Phase CGPA | 7 — Développement · Gate 07 (non statué) |
| Sprint | S03 — EP-04 Paiements & garanties |
| Stories | US-30 (génération échéances), US-31 (pointage), US-32 (garanties) |
| Branche | `feat/s03-paiements-garanties` |
| Date | 2026-06-09 · Agent : Claude Code |
| Plan de référence | Plan d'Exécution S03 (Niveau 3), approuvé par le PO le 2026-06-09 |

## 1. Périmètre exécuté

Lot **backend** (frontend reporté, conformément à l'arbitrage A). Implémentation du domaine, de l'API
et du batch par-dessus le schéma `V1` préexistant (tables `paiement`/`garantie` déjà créées).

- **US-30 — génération à terme échu (Annexe A.3)** : fonction SQL `generer_echeances_loyers()` (V6),
  service `GenerationEcheancesService`, planification `@Scheduled` quotidienne et déclencheur manuel
  `POST /api/batch/echeances` (rôle BAILLEUR).
- **US-31 — pointage (EF-30/31/32)** : `PATCH /api/biens/{bienId}/paiements/{periode}/pointage`
  (statut + montant reçu, reste dû calculé) et `GET /api/biens/{bienId}/paiements` (historique).
- **US-32 — garanties (EF-40/41/42, Annexe A.5)** : `POST`/`GET`
  `/api/biens/{bienId}/baux/{bailId}/garanties` + `POST .../{garantieId}/restitution`
  (DETENU → RESTITUE_PARTIEL → RESTITUE_TOTAL, retenue + motif).
- **Audit (BNF-05, arbitrage C)** : `AuditService` journalise `POINTER_PAIEMENT`, `CREATE_GARANTIE`,
  `RESTITUER_GARANTIE` dans `audit_log` (consultation différée à US-62).

## 2. Écart au plan signalé (CGPA — dérive maîtrisée)

Le plan envisageait, pour le job multi-bailleur, un **second datasource sous le rôle `loyertracker_batch`
(LOGIN)**. La lecture approfondie du code a révélé un patron déjà établi (V2–V4) **plus simple et de
moindre risque** : une fonction **`SECURITY DEFINER` propriété de `loyertracker_batch` (BYPASSRLS)**,
appelée par le rôle applicatif `loyertracker_api` via le datasource unique existant. Résultat : pas de
second datasource, pas de rôle LOGIN dédié, même objectif (génération idempotente multi-bailleur).
Décision prise pendant l'exécution, sans changement de périmètre ; consignée ici.

## 3. Anomalies rencontrées et corrigées

1. **Schéma Hibernate (validate)** : `paiement.periode` est `CHAR(7)` en base ; le mapping `String`
   par défaut attend `varchar`. Corrigé par `@JdbcTypeCode(SqlTypes.CHAR)` + `length = 7`.
2. **Contrat de sécurité sans BDD** : `SecurityIntegrationTest` (profil `test`, JPA exclu) ne pouvait
   plus charger le contexte (nouveaux beans JPA). Corrigé en neutralisant les services S03 par
   `@MockitoBean`, comme les autres services dépendant de JPA. `SchemaMigrationTest` mis à jour
   (6 migrations attendues au lieu de 5).

## 4. Fichiers

**Créés** : `db/migration/V6__s03_echeances_loyers.sql` ; package `paiements/` (Paiement, StatutPaiement,
PaiementRepository, PaiementDto, PointageRequest, PaiementService, PaiementController) ; package
`garanties/` (Garantie, StatutGarantie, TypeRestitution, GarantieRepository, GarantieDto,
GarantieRequest, RestitutionRequest, GarantieService, GarantieController) ; package `batch/`
(GenerationEcheancesService, EcheancesScheduler, BatchController, SchedulingConfig) ;
`audit/AuditService.java` ; `s03/S03PaiementsGarantiesIntegrationTest.java`.

**Modifiés** : `SecurityIntegrationTest.java`, `db/SchemaMigrationTest.java`.

## 5. Tests & résultats

`mvn verify` : **BUILD SUCCESS — 44 tests, 0 échec** (38 existants + 6 S03), aucune régression.
Couverture S03 (`S03PaiementsGarantiesIntegrationTest`) :

- génération à terme échu **idempotente** (3 échéances mois début→terme ; 2ᵉ passage = 0) ;
  vérif `date_exigibilite = 1er(m+1)` et montant = loyer CC ;
- pointage **RECU** (reste dû 0), **PARTIEL** (reste dû calculé), incohérence PARTIEL → 400,
  période inexistante → 404 ;
- cycle de vie garantie DETENU → RESTITUE_PARTIEL → RESTITUE_TOTAL, double restitution → 409 ;
- **autorisation** : accès financier cross-bailleur refusé (403) ; gestionnaire **affecté actif**
  autorisé puis **révoqué** refusé (403) ;
- **audit** : 1 ligne `POINTER_PAIEMENT` + 1 ligne `CREATE_GARANTIE`.

## 6. DoD

- [x] Code conforme CDC/ADR · [x] Tests unitaires/intégration · [x] Tests d'autorisation
  (0 cross-bailleur/cross-affectation) · [x] Aucun secret en clair · [x] Migration Flyway versionnée
  (V6) · [x] Endpoints documentés (Javadoc) · [x] Déployable `docker compose up`.

## 7. Reste à faire (hors lot)

- Frontend S03 (pointage / garanties) — lot suivant.
- Validation runtime sous le rôle `loyertracker_api` du flux S03 (smoke test stack complète) +
  conversion des tests d'intégration au double datasource (suivi de fidélité RLS, commun à S02).
- Honoraires (US-40) & alertes/batch (US-50→52) : S04.

## 8. Décision attendue

Acceptation du lot S03 backend (GO / GO sous réserve). Le merge dans `main` reste décision de gate du PO.
