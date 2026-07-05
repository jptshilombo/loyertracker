# Clôture Release `1.8.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-05 |
| Heure CDO GO | ~10:02 UTC (après checkpoint combiné T+12/T+24 PASS) |
| Release | `1.8.0` |
| Tag Production | `sha-2c5f43c7` |
| Commit applicatif | merge commit PR #173 (`2c5f43c7` — correctif RSV-S10-01, inclut le Sprint 10 EP-12b/PR #168) |
| `PRODUCTION_DEPLOYED` | 2026-07-04 ~16:45 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.8.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| PR #168 (Sprint 10 EP-12b US-95/96/97) fusionnée | 2026-07-04 | CI 7/7 PASS (merge `1d1c2a5d`) | `main` |
| Gate Staging Sprint 10 | 2026-07-04 ~10:59 UTC | GO — `STAGING_DEPLOYED` (`sha-1d1c2a5d`), 59/0 + 20 contrôles US-95/96/97, 1 réserve RSV-S10-01 | `gate-staging-sprint10-v5.4.1-decision.md` |
| Levée RSV-S10-01 | 2026-07-04 14:42 UTC | PR #173 mergée (`2c5f43c7`) — tri stable `date_mouvement, cree_le, id` | `gate-staging-sprint10-v5.4.1-decision.md` §9 |
| Gate Production `1.8.0` | 2026-07-04 | **NO GO initial** (RSV-S10-02 : candidat jamais stagé) → arbitrage PO **S1** → redéploiement Staging `sha-2c5f43c7` + preuve discriminante du correctif → **GO — `PRODUCTION_READY`** | `gate-production-v1.8.0-decision.md` |
| Préflight + backup | 2026-07-04 ~16:07–16:15 UTC | PASS — `loyertracker-20260704-170836.dump` (317 Kio, 740 entrées, SHA-256 vérifié) ; §4.2 4/4 | `preflight-backup-v1.8.0-report.md` |
| Déploiement technique | 2026-07-04 ~16:45 UTC | PASS — `sha-2c5f43c7` actif, **V21 appliquée** (21/21, `paiement.garantie_movement_id` additive), digests conformes avant/après, 4/4 healthy | `deploiement-technique-v1.8.0-report.md` |
| Smoke Production | 2026-07-04 ~16:30 UTC | **59 PASS / 0 FAIL au premier passage** — première release de l'historique sans correctif préalable au smoke ; nettoyage transactionnel 0 résidu | `validation-finale-v1.8.0-report.md` |
| Hypercare T0 | 2026-07-04 ~16:35 UTC | PASS | `plan-etape-hypercare-v1.8.0.md` |
| Hypercare T+12/T+24 combiné | 2026-07-05 09:41 UTC (≈ T+17) | PASS — T+12 en rattrapage (hôte volontairement éteint pendant sa fenêtre), T+24 anticipé ~7 h sur décision PO (précédent `1.7.0`) ; aucun critère de suspension atteint | `plan-etape-hypercare-v1.8.0.md` |

## 2. Périmètre livré

**Sprint 10 EP-12b — Garantie usage métier (US-95/96/97, ADR-14 §5) + correctif RSV-S10-01**

- Migration **V21** : `paiement.garantie_movement_id` (FK nullable vers `garantie_movement` +
  index) — lien entre un mouvement `RETENUE_LOYER` et le paiement qu'il couvre. Additive :
  **rollback applicatif seul redevenu viable** (contraste explicite avec V20/RSV-S9-03).
- **US-95** : `POST .../garanties/{id}/retenue-loyer` — retenue explicite, jamais automatique ;
  transition du paiement vers `RECU`/`PARTIEL`, recalcul honoraires, gardes 400/409.
- **US-96** : `POST .../garanties/{id}/complement` — motif obligatoire, audit
  `COMPLEMENT_GARANTIE`.
- **US-97** : `GET .../mouvements` + export CSV (échappé anti formula-injection) ; UI
  triable/filtrable ; export RGPD incluant le ledger.
- Correctif `Garantie.restituerPartiel` : calcul depuis `soldeActuel` (critique dès qu'une
  retenue/complément existe).
- Correctif RSV-S10-01 (PR #173) : `garantie_movement.cree_le` mappé en lecture seule, tri du
  ledger stable et chronologique `date_mouvement, cree_le, id` (US-97 + export RGPD), test
  d'intégration ajouté. Preuve discriminante en Staging : l'ancien tri aurait rendu
  `RETENUE_LOYER, DEPOT_INITIAL, COMPLEMENT` là où le nouveau rend l'ordre chronologique.

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| **RSV-S10-01** | Ordre intra-jour du ledger non déterministe (tie-break UUID) | ✅ **LEVÉE le 2026-07-04** — PR #173, preuve discriminante rejouée en Staging sur données recréées |
| **RSV-S10-02** | Candidat `sha-2c5f43c7` jamais déployé en Staging (delta = commit `71a7a73`) | ✅ **LEVÉE le 2026-07-04** — option S1 exécutée : redéploiement Staging ciblé du candidat, `STG-ISOL-01` PASS avant/après, smoke 59/0, vérification fonctionnelle rejouée |
| **RSV-S9-03** | Rollback applicatif seul non viable pour tout retour antérieur à `1.7.0` (V20) | ⚠️ **ACCEPTÉE PO — permanente** (héritée, consignée) ; sans objet pour V21, qui est additive |
| **OBS-S10-01** | Lignes backfillées V20 partageant un `cree_le` identique (même transaction) → tie-break UUID résiduel intra-timestamp | 🟡 **OUVERTE — cosmétique, non bloquante** : 0 cas en Production ; à statuer post-clôture (critère métier secondaire dans un sprint ultérieur, ou acceptation en l'état) |
| Heartbeat backup absent (cf. RSV-T24-01 historique) | Métrique absente au checkpoint T+12/T+24 | ✅ **QUALIFIÉE sans impact** — pushgateway purgé au boot (~09:34 UTC) + cron backup (02:15 UTC) non joué hôte éteint ; pattern récurrent `1.4.0`–`1.7.0`, exclu des critères de suspension par le plan d'hypercare ; résorption au prochain passage du cron |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-2c5f43c7` |
| Digest API | `sha256:bab66aa35d9b70d045be284cd0132746e36377110dd29a3079f1ca821a2b45a5` |
| Digest Web (nginx) | `sha256:9c8915f0eca279bf75c548d7a4846d48c266a9d44be7c9b405bd526847cd3f87` |
| Flyway | V1→**V21** (21/21) |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 (aucun restart sur toute l'hypercare) |
| Actuator | `{"status":"UP"}` |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | `[]` — 0 alerte active |
| `.env` | `LOYERTRACKER_TAG=sha-2c5f43c7` persisté (backup `.env.bak-pre-1.8.0`, 600) |
| p99 latence (checkpoint) | ~61 ms |
| 5xx rate | 0 |
| Invariant garantie | `solde_actuel = Σ (credit − debit)` — **3/3 PASS** (2100,00 / 600,00 / 600,00) |
| Paiements liés V21 | 0 — aucune activité métier réelle depuis le déploiement (attendu, produit non annoncé) |
| `bailleur-test` | désactivé (`enabled: false`), vérifié au checkpoint |
| Site public | `https://loyertracker.loyerpro.org` → 200 |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.8.0` CLÔTURÉE le 2026-07-05.**

- Hypercare T0 PASS + checkpoint combiné T+12 (rattrapage) / T+24 (anticipé) PASS — écarts de
  fenêtre qualifiés et tranchés par le PO (hôte volontairement éteint la nuit, produit non
  annoncé publiquement, précédent `1.7.0`).
- US-95/96/97 confirmées en Production : V21 opérationnelle, invariant du ledger respecté sur
  les 3 garanties réelles, tri chronologique stable du ledger prouvé de façon discriminante en
  Staging (S1).
- RSV-S10-01 et RSV-S10-02 levées. RSV-S9-03 acceptée permanente (héritée). OBS-S10-01 reste
  ouverte en observation cosmétique, à statuer hors hypercare.
- Première release de l'historique avec smoke Production 59/0 au premier passage sans correctif
  préalable (défauts récurrents anticipés : compteur Flyway PR #171, synchronisation du dépôt au
  Préflight).
- Prochaines actions autorisées : statuer OBS-S10-01 ; traiter les 2 alertes Dependabot
  signalées sur `main` (1 modérée, 1 basse) ; puis Sprint suivant du backlog PO selon le
  parcours gouverné (`STG-ISOL-01` puis Gate Staging).
