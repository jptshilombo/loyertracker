# Rapport d'Exécution — Frontend S03 + correctifs QA (Paiements & Garanties)

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker · 0.1.0-SNAPSHOT |
| Phase CGPA | 7 — Développement · Gate 07 (non statué) |
| Sprint | S03 (suite) — EP-04 Paiements & garanties, volet IHM |
| Stories | US-31 (pointage), US-32 (garanties) côté frontend ; + correctifs backend issus de la QA |
| Branche | `feat/s03-frontend-paiements-garanties` |
| Date | 2026-06-09 · Agent : Claude Code |
| Plan de référence | Plan d'Exécution Frontend S03 (Niveau 3), approuvé par le PO le 2026-06-09 |

## 1. Périmètre exécuté

Deux lots, conformément au plan approuvé et aux trois arbitrages du PO (EN_RETARD automatisé ;
RECU ≥ attendu imposé ; frontend = pointage + garanties).

### Lot A — correctifs backend (issus de la QA du lot backend S03)

- **EN_RETARD automatique** : fonction SQL `marquer_loyers_en_retard()` (migration **V7**),
  `SECURITY DEFINER` propriété de `loyertracker_batch`, `GRANT EXECUTE` à `loyertracker_api`
  (même patron que V6). Règle étroite et idempotente : seuls les `IMPAYE` dont
  `date_exigibilite < current_date` basculent ; jamais `PARTIEL`/`RECU`, jamais de retour arrière.
- **Chaînage batch** : `GenerationEcheancesService.marquerEnRetard()` ; le scheduler quotidien et
  le déclencheur manuel `POST /api/batch/echeances` enchaînent génération **puis** marquage. Le DTO
  de réponse devient `{echeancesCreees, loyersEnRetard}`.
- **Cohérence RECU** : `PaiementService.pointer` rejette désormais (400) un `RECU` dont le montant
  reçu est `< montant attendu` (symétrique au contrôle `PARTIEL` préexistant).

### Lot B — frontend (US-31/32)

- **Service** `core/s03/s03-api.service.ts` (+ spec) : paiements (`listerPaiements`, `pointer`,
  `declencherEcheances`) et garanties (`listerGaranties`, `deposerGarantie`, `restituer`), aligné
  sur `S02ApiService` (origine `/api`, observables typés).
- **Composants standalone** (signals, `ReactiveFormsModule`, templates inline) :
  - `paiements/paiements-bien.component.ts` : historique des loyers (période, attendu, reçu, reste
    dû, statut coloré) + pointage. Validation client miroir du backend (PARTIEL : 0 < reçu <
    attendu ; RECU : reçu ≥ attendu). Bouton « Générer les échéances » exposé seulement si
    `peutDeclencher` (bailleur).
  - `garanties/garanties-bail.component.ts` : dépôt + liste + restitution (TOTALE, ou PARTIELLE
    depuis DETENU avec retenue + motif obligatoires).
- **Intégration** : panneaux montés dans les deux dashboards via la sélection bien/bail déjà
  présente. Le déclenchement du batch n'est offert qu'au bailleur (cohérent avec
  `@PreAuthorize hasRole('BAILLEUR')`).

## 2. Conformité au plan

Aucune dérive. Les trois arbitrages PO sont respectés. Le déclenchement manuel des échéances
réalise désormais génération + marquage en un seul appel (réaliste vis-à-vis du job quotidien), ce
qui a conduit à ajuster une assertion du test d'intégration backend existant (échéances échues →
EN_RETARD), changement assumé et documenté.

## 3. Anomalie d'environnement (hors code)

Le cache Angular `.angular/cache` contenait des fichiers appartenant à `root` (vestige d'un run
privilégié antérieur), provoquant des `EACCES` au lancement de Karma. Contourné en renommant le
dossier (gitignoré) pour qu'un cache propre, possédé par l'utilisateur, soit recréé. Aucun impact
sur le code ni la CI.

## 4. Fichiers

**Backend créés** : `db/migration/V7__s03_loyers_en_retard.sql`.
**Backend modifiés** : `batch/GenerationEcheancesService.java`, `batch/EcheancesScheduler.java`,
`batch/BatchController.java`, `paiements/PaiementService.java`, tests
`s03/S03PaiementsGarantiesIntegrationTest.java`, `db/SchemaMigrationTest.java`.

**Frontend créés** : `core/s03/s03-api.service.ts` (+ `.spec`),
`paiements/paiements-bien.component.ts`, `garanties/garanties-bail.component.ts`.
**Frontend modifiés** : `bailleur/dashboard/dashboard.component.ts`,
`gestionnaire/dashboard/dashboard.component.ts`.

## 5. Tests & résultats

- **Backend** `mvn verify` : **BUILD SUCCESS — 46 tests, 0 échec** (44 + 2 S03 : RECU insuffisant →
  400 ; loyers échus → EN_RETARD idempotent et respectueux des pointages). `SchemaMigrationTest`
  valide **7** migrations.
- **Frontend** : `ng lint` OK ; `ng build` OK ; `ng test` headless **18 tests, 0 échec** (14 + 4
  sur `S03ApiService`).

## 6. DoD

- [x] Code conforme CDC/plan · [x] Tests backend (intégration + autorisation) · [x] Tests frontend
  (service) · [x] Validation client miroir du backend · [x] Aucun secret en clair · [x] Migration
  Flyway versionnée (V7) · [x] Lint/build/tests verts · [x] Déployable `docker compose up`.

## 7. Reste à faire (hors lot)

- Conversion des tests d'intégration au double datasource (fidélité RLS, commun S02/S03).
- Smoke test runtime du flux S03 sous le rôle `loyertracker_api` sur la stack complète.
- S04 : honoraires (US-40), alertes/batch (US-50→52), consultation de l'audit (US-62).

## 8. Décision attendue

Acceptation du lot Frontend S03 + correctifs QA (GO / GO sous réserve). Le merge dans `main` reste
décision de gate du PO.
