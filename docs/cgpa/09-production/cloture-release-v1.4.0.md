# Clôture Release `1.4.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-01 |
| Heure CDO GO | 06:51 UTC (après T+24 PASS) |
| Release | `1.4.0` |
| Tag Production | `sha-98afa99a` |
| Commit applicatif | merge commit PR #115 (Sprint 5 Lot B) |
| `PRODUCTION_DEPLOYED` | 2026-06-30 ~15:12 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.4.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| CI PR #115 (Sprint 5 Lot B) | 2026-06-30 | 100% PASS après fix V18 + SchemaMigrationTest 15→18 | `main` |
| Gate Staging Sprint 5 Lot B | 2026-06-30 | GO — `STAGING_DEPLOYED` ; STG-ISOL-01 PASS live (×2) ; Flyway 18/18 ; smoke 47/0 | `docs/project-state.md` |
| Gate Production Sprint 5 | 2026-06-30 | GO sous réserve — `PRODUCTION_READY` ; RP-140-01 bloquante | `docs/project-state.md` |
| Préflight + backup | 2026-06-30 15:06 UTC | PASS — backup 312 Kio, 730 entrées, RP-140-01 levée | `docs/prod-state.md` §0E |
| Déploiement technique | 2026-06-30 ~15:12 UTC | PASS — `sha-98afa99a` actif, 4/4 healthy, Flyway V16/V17/V18 appliquées | `docs/prod-state.md` §0E |
| Smoke Production | 2026-06-30 ~15:15 UTC | **47 PASS / 0 FAIL** | `docs/prod-state.md` §0E |
| Hypercare T0 | 2026-06-30 15:12 UTC | PASS | `docs/project-state.md` |
| Hypercare T+12 anticipé | 2026-06-30 15:25 UTC | PASS | `docs/project-state.md` |
| Hypercare T+24 | 2026-07-01 06:38 UTC | PASS | `docs/project-state.md` |

## 2. Périmètre livré

**PR #110 — Correctifs UX post-Sprint 4 (`fix(ux): navbar profil bailleur + message 409 quittance actionnable`)**
- Lien profil bailleur ajouté à la navbar (était présent dans le header mais absent de la nav).
- Message d'erreur 409 rendu actionnable (adresse manquante sur quittance/avis d'échéance).
- Affectations patrimoine (É-01) : section rendue visible dans l'interface.

**PR #113 — Sprint 5 Lot A+C (`fix(ux): Sprint 5 Lot A+C — alertes NON_LUE, À VENIR, suppression bruit inscription`)**
- Lot A — Alertes : filtre sur `statut === 'NON_LUE'` uniquement ; suppression du badge statut redondant.
- Lot C — Échéances : affichage des statuts `A_VENIR` ; suppression du bruit à l'inscription.

**PR #115 — Sprint 5 Lot B (`Flyway V16/V17/V18 + paiement_statut_check`)**
- B1 — `bien.statut` : synchronisation rétroactive → `LOUE` pour les biens ayant un bail `ACTIF`.
- B2 — `patrimoine.adresse` : ajout du champ adresse (migration V16).
- B3 — `bail.devise` : support EUR/USD/CDF (migration V17).
- B4 — `StatutPaiement.A_VENIR` : nouveau statut, contrainte CHECK étendue (`paiement_statut_check`), réécriture de `generer_echeances_loyers()` (migration V18).

**PR #116 — Smoke fix (`fix(smoke): compteur Flyway 15→18`)**
- `infra/smoke/smoke-stack.sh` : compteur Flyway mis à jour 15→18 pour Sprint 5 Lot B.

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| **RP-140-01** | Backup pré-déploiement | ✅ **LEVÉE** — Préflight 2026-06-30 (`loyertracker-20260630-160619.dump`, 312 Kio, 730 entrées) |
| **RSV-T24-01** | Cron backup inactif depuis Jun 25 | ✅ **LEVÉE** — Serveur de production éteint volontairement entre les déploiements (produit non encore annoncé publiquement). Aucun trafic réel. Le cron ne peut pas s'exécuter quand l'hôte est arrêté. Backup manuel `loyertracker-20260701-074122.dump` (316K, 730 entrées) exécuté lors du T+24 avant arrêt. |
| **RSV-P140-01** | Plan d'Exécution Sprint 5 non formalisé en document | ⚠️ **NON BLOQUANT** — Décisions et jalons tracés dans `docs/project-state.md`. À formaliser post-clôture. |
| **RSV-P140-02** | `docs/release-notes-v1.4.0.md` + `CHANGELOG.md` absents | ⚠️ **NON BLOQUANT** — À créer post-clôture avant Sprint 6. |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-98afa99a` |
| Digest API | `sha256:d643ea900ae81f27ea327eb569f24879f9263ebde91ec49a3160fbcd390635a3` |
| Digest Web (nginx) | `sha256:575606758828a24a2f6d67e9d9ed8152c3fe9f7ecc412c9672555619791b2e74` |
| Flyway | V1→**V18** (18/18) |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 |
| Actuator | `{"status":"UP"}` |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | `[]` — 0 alerte active |
| `.env` | `LOYERTRACKER_TAG=sha-98afa99a` persisté, SHA-256 `1e3f9a7d…` |
| p99 latence (T+24) | 66.6 ms |
| 5xx rate | 0.0000 req/s |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.4.0` CLÔTURÉE le 2026-07-01.**

- Hypercare T0/T+12 anticipé/T+24 — tous PASS.
- Sprint 5 (Lots A, B, C) + correctifs UX confirmés en Production : Flyway V16/V17/V18 opérationnelles, `StatutPaiement.A_VENIR` fonctionnel, génération d'échéances cohérente.
- RP-140-01 levée. RSV-T24-01, RSV-P140-01, RSV-P140-02 non bloquantes — à traiter post-clôture.
- Prochaine action autorisée : Sprint 6 ou correctif selon backlog PO.
