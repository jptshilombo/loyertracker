# Clôture Release `1.7.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-04 |
| Heure CDO GO | ~10:45 UTC (après checkpoint combiné T+12/T+24 PASS) |
| Release | `1.7.0` |
| Tag Production | `sha-6a358eb6` |
| Commit applicatif | merge commit PR #152 (Sprint 9 EP-12a, US-94) |
| `PRODUCTION_DEPLOYED` | 2026-07-03 ~13:35 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.7.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| PR #152 (Sprint 9 EP-12a US-94) fusionnée | 2026-07-02 18:28 UTC | CI 100 % PASS | `main` |
| Gate Staging Sprint 9 | 2026-07-03 | GO — `STAGING_DEPLOYED` | `docs/project-state.md` |
| Gate Production `1.7.0` | 2026-07-03 | GO sous réserve acceptée — `PRODUCTION_READY` sous condition A1 | `gate-production-v1.7.0-decision.md` |
| Préflight + backup | 2026-07-03 ~13:13 UTC | PASS — `loyertracker-20260703-131331.dump` (316 Kio, 730 entrées) ; **RSV-PROD-S9-01 levée** (option A1 : 2 garanties reconstituées avant migration) | `preflight-backup-v1.7.0-report.md` |
| Déploiement technique | 2026-07-03 ~13:35 UTC | PASS — `sha-6a358eb6` actif, V20 appliquée (ledger + backfill rétroactif + drop `bail.depot_garantie`), 4/4 healthy | `deploiement-technique-v1.7.0-report.md` |
| Smoke Production | 2026-07-03 ~13:30 UTC | **59 PASS / 0 FAIL**, nettoyage transactionnel complet | `validation-finale-v1.7.0-report.md` |
| Hypercare T0 | 2026-07-03 ~13:45 UTC | PASS | `plan-etape-hypercare-v1.7.0.md` |
| Hypercare T+12/T+24 combiné | 2026-07-04 10:18 UTC | PASS — T+12 en rattrapage (hôte volontairement éteint pendant sa fenêtre), T+24 anticipé ~3 h 27 sur décision PO (précédents `1.3.0`/`1.4.0`) | `plan-etape-hypercare-v1.7.0.md` |

## 2. Périmètre livré

**PR #152 — Sprint 9 EP-12a Garantie ledger (US-94, ADR-14/D-GAR-001)**
- Table `garantie_movement` (migration **V20**) : journal append-only des mouvements de garantie
  (8 types), isolée par RLS `bailleur_id`.
- `garantie.solde_actuel` : cache transactionnel recalculé de façon synchrone à chaque mouvement —
  invariant central `solde_actuel = Σ (credit − debit)`.
- Backfill rétroactif : chaque garantie existante a généré les mouvements reconstituant son
  historique (aucune perte de traçabilité).
- `bail.depot_garantie` supprimée — devient une valeur dérivée du ledger (`BailDto.depotGarantie`
  calculé). Champ « Dépôt » retiré du formulaire de création de bail.
- Aucun usage métier nouveau exposé par cette release (retenue typée, complément, historique UI —
  livrés côté `main` par le Sprint 10/EP-12b, PR #168, **hors périmètre `1.7.0`**).

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| **RSV-PROD-S9-01** | 2 baux réels avec `depot_garantie` sans ligne `garantie` (auraient affiché 0 après migration) | ✅ **LEVÉE** — option A1 exécutée au Préflight (2 garanties reconstituées), vérifiée en conditions réelles après déploiement |
| **RSV-S7-8-01** | Vérification visuelle USD/CDF non faite en Production | ✅ **LEVÉE le 2026-07-03** — USD réel + CDF synthétique vérifiés sur `ai-test-server`, nettoyés |
| **RP-160-03** | `CHANGELOG.md` `[Non publié]` mélangeant 3 releases | ✅ **LEVÉE le 2026-07-03** — scindé en `[1.5.0]`/`[1.6.0]`/`[1.7.0]` |
| **RSV-S9-03** | Aucun rollback applicatif seul viable (V20 supprime `bail.depot_garantie`) | ⚠️ **ACCEPTÉE PO — permanente pour ce schéma** : seul un restore complet du backup pré-déploiement permet un retour arrière ; consignée, non levable |
| `BackupHeartbeatMissing` (cf. RSV-T24-01 historique) | Alerte active au checkpoint T+12/T+24 | ✅ **QUALIFIÉE sans impact** — pushgateway purgé par le boot de l'hôte (~09:09 UTC) et cron backup (02:15 UTC) non joué hôte éteint ; dernier backup réel ~21 h < seuil 26 h ; résorption au prochain passage du cron |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-6a358eb6` |
| Digest API | `sha256:485c8574cca057d4e00f3c0de640faf4ad8b378c302604b76a752563eb98dfba` |
| Digest Web (nginx) | `sha256:70ae97f2eda455b5c9640cc33aeb6ea4abda9131222b3f948a5ea29768bca5c5` |
| Flyway | V1→**V20** (20/20) |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 |
| Actuator | `{"status":"UP"}` |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | 1 alerte `BackupHeartbeatMissing` — qualifiée (§3), hors critères de suspension |
| `.env` | `LOYERTRACKER_TAG=sha-6a358eb6` persisté (backup `.env.bak-pre-1.7.0`) |
| p99 latence (checkpoint) | ~24,7 ms |
| 5xx rate | 0 |
| Invariant garantie | `solde_actuel = Σ mouvements` — **3/3 PASS** (2100,00 / 600,00 / 600,00) |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.7.0` CLÔTURÉE le 2026-07-04.**

- Hypercare T0 PASS + checkpoint combiné T+12 (rattrapage) / T+24 (anticipé) PASS — écarts de
  fenêtre qualifiés et tranchés par le PO (hôte volontairement éteint la nuit, produit non
  annoncé publiquement).
- US-94 confirmée en Production : V20 opérationnelle, backfill vérifié, invariant du ledger
  respecté sur les 3 garanties réelles, `depotGarantie` dérivé cohérent.
- RSV-PROD-S9-01, RSV-S7-8-01, RP-160-03 levées. RSV-S9-03 acceptée permanente (consignée).
- Prochaine action autorisée : promotion du Sprint 10 (EP-12b, déjà sur `main` via PR #168)
  selon le parcours gouverné — Gate `STG-ISOL-01` puis Gate Staging v5.3, ou autre priorité
  du backlog PO.
