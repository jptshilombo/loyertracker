# Rapport d'Exécution — S04 Lot 2 (Alertes & consultation de l'audit)

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker · 0.1.0-SNAPSHOT |
| Phase CGPA | 7 — Développement · Gate 07 (non statué) |
| Sprint | S04 — EP-05 Honoraires & pilotage, lot 2 (alertes + audit) |
| Stories | US-50, US-51, US-52 (alertes & scoping), US-62 (consultation audit) |
| Branche | `feat/s04-alertes-audit` |
| Date | 2026-06-10 · Agent : Claude Code |
| Plan de référence | Plan d'Exécution S04 (Niveau 3), approuvé par le PO le 2026-06-10 (défauts A–D) |

## 1. Périmètre exécuté

Backend uniquement (arbitrage A), lot 2 sur 2 (arbitrage D). **PREAVIS reporté** (arbitrage B).

### Migration V9 `db/migration/V9__s04_alertes.sql`

- **`generer_alertes()`** `SECURITY DEFINER` (owner `loyertracker_batch`, `GRANT EXECUTE` à
  `loyertracker_api`), multi-bailleur et idempotente via `ON CONFLICT (type, bien_id, periode) WHERE
  statut='NON_LUE' DO NOTHING` (index `uq_alerte_nonlue`, EF-65). Trois types :
  - **LOYER_EN_RETARD** (EF-60) : loyer passé `EN_RETARD` (V7) ; `periode` = celle du loyer ;
  - **FIN_BAIL** (EF-61) : bail `ACTIF` dont le terme est atteint sous 60 jours ;
  - **GARANTIE_NON_RESTITUEE** (EF-63) : garantie `DETENU` > 30 jours après la fin d'un bail `CLOS`.
  - **Gotcha periode NULL traité** : `NULL ≠ NULL` empêcherait l'anti-doublon ; une `periode`
    non-nulle est **dérivée** (mois du terme du bail) pour FIN_BAIL et GARANTIE — tout `(type,
    bien_id, periode)` est complet.
- **`alertes_gestionnaire(gid)` (SETOF alerte)** et **`alerte_bailleur_pour_gestionnaire(id, gid)`**
  `SECURITY DEFINER` : le gestionnaire n'ayant pas de tenant propre, ces fonctions bornent sa lecture
  / son marquage à ses affectations **ACTIVES** (US-52), même patron que les prédicats ReBAC de V3.

### Code applicatif

- **Package `alertes/`** : `Alerte` (entity), `TypeAlerte`, `StatutAlerte`, `AlerteRepository`,
  `AlerteDto`, `AlerteService`, `AlerteController`.
  - `GET /api/alertes` (BAILLEUR + GESTIONNAIRE) : le bailleur voit tout son tenant (RLS) ; le
    gestionnaire uniquement ses biens affectés actifs (via la fonction SECURITY DEFINER).
  - `PATCH /api/alertes/{id}/lecture` : marquage « lue » dans le périmètre de l'acteur (garde de
    cloisonnement applicative en plus de la RLS ; fail-closed 404 hors périmètre).
- **Audit (US-62)** : `audit/AuditLog` (mapping lecture seule, `details` JSONB non exposé),
  `AuditLogRepository`, `AuditDto`, `AuditController` `GET /api/audit` **réservé au BAILLEUR**
  (gestionnaire → 403) ; `AuditService.consulter` positionne le tenant puis lit sous RLS.
- **Batch** : `POST /api/batch/alertes` (BAILLEUR) ; nouveau `AlertesScheduler` `@07:00`
  (après le job loyers de 06:30, pour intégrer les bascules EN_RETARD du jour).

## 2. Conformité au plan & écarts assumés

Conforme aux arbitrages A–D. Précisions :

1. **Scoping gestionnaire & marquage** : implémentés via deux fonctions `SECURITY DEFINER` (le
   gestionnaire n'a pas de tenant RLS propre). Le marquage « lue » par un gestionnaire repositionne
   le tenant sur le bailleur de l'alerte **après** vérification d'accès (fail-closed 404).
2. **Défense en profondeur** : `AlerteService.marquer` vérifie `alerte.bailleurId == tenant` en plus
   de la RLS — pertinent car les tests d'intégration tournent en superutilisateur (RLS non exercée).
3. **`destinataire_id`** renseigné avec le `bailleur_id` (l'index anti-doublon ne le porte pas ; le
   scoping est calculé à la lecture).

## 3. Fichiers

**Créés** : `db/migration/V9__s04_alertes.sql` ; package `alertes/` (`Alerte`, `TypeAlerte`,
`StatutAlerte`, `AlerteRepository`, `AlerteDto`, `AlerteService`, `AlerteController`) ;
`audit/AuditLog.java`, `audit/AuditLogRepository.java`, `audit/AuditDto.java`,
`audit/AuditController.java` ; `batch/AlertesScheduler.java` ; test
`s04/S04AlertesAuditIntegrationTest.java`.
**Modifiés** : `audit/AuditService.java` (méthode `consulter` + deps `TenantContext`/repo),
`batch/BatchController.java` (endpoint alertes), `db/SchemaMigrationTest.java` (9 migrations),
`SecurityIntegrationTest.java` (mock `AlerteService`).

## 4. Tests & résultats

- `mvn verify` : **BUILD SUCCESS — 53 tests, 0 échec** (49 + 4 S04). Spotless OK ; couverture
  JaCoCo : « All coverage checks have been met ». `SchemaMigrationTest` valide **9** migrations.
- `S04AlertesAuditIntegrationTest` :
  - `loyerEnRetardEtFinBailGeneresAntiDoublonEtLecture` — 3 LOYER_EN_RETARD + 1 FIN_BAIL ; 2ᵉ
    passage → 0 (anti-doublon EF-65) ; marquage « lue » ;
  - `garantieNonRestitueeGeneree` — bail CLOS + garantie DETENU > 30 j → 1 GARANTIE_NON_RESTITUEE ;
  - `gestionnaireNeVoitQueLesAlertesDeSesBiensAffectes` — scoping US-52 (bien affecté vs non affecté) ;
  - `auditConsultableParBailleurInterditAuGestionnaire` — bailleur 200 (POINTER_PAIEMENT tracé),
    gestionnaire 403.

## 5. DoD

- [x] Code conforme CDC/plan · [x] Tests d'intégration (génération, anti-doublon, scoping, audit) ·
  [x] Aucun secret en clair · [x] Migration Flyway versionnée (V9) · [x] Spotless / couverture /
  build verts · [x] `ddl-auto=validate` satisfait (tables V1 inchangées).

## 6. Reste à faire (hors lot 2)

- **PREAVIS** (US-50) : reporté — critère d'acceptation à figer avec le PO avant implémentation.
- Suivis reconduits : conversion des tests d'intégration au double datasource (fidélité RLS) ;
  smoke test runtime sous le rôle `loyertracker_api` ; **frontend S04** (honoraires, alertes, audit)
  à planifier (plan ultérieur).

## 7. Décision attendue

Acceptation du lot 2 Alertes & Audit (GO / GO sous réserve) et autorisation d'ouvrir la PR. Le merge
dans `main` reste décision de gate du PO. À l'issue des deux lots, S04 backend est complet (hors
PREAVIS reporté) ; reste à arbitrer le périmètre du frontend S04.
