# Clôture Release `1.9.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-08 |
| Heure CDO GO | ~14:36 UTC (après checkpoint combiné T+12/T+24 en rattrapage, PASS) |
| Release | `1.9.0` |
| Tag Production | `sha-75646d8f` |
| Commit applicatif | merge PR #205 (`3feb1d5` — Préflight) suivi du merge PR #202 (`75646d8` — Sprint 12 EP-14b) |
| `PRODUCTION_DEPLOYED` | 2026-07-06 ~17:57 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.9.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| EP-14 instruit (ADR-15, plan d'exécution, backlog US-99→104) | 2026-07-05 | Kickoff PO K1–K5 tranché ; release Production unique `1.9.0` (Sprint 11 + 12 combinés) | `ADR-15-quittances-certifiees.md`, `plan-execution-ep14-quittances-certifiees.md` |
| Sprint 11 (EP-14a) — socle quittance certifiée + PDF | 2026-07-05 | PR #183 mergée (`eddc037`), CI 7/7, SonarQube PASS (89,4 % couverture nouvelle) | `main` |
| Gate Staging Sprint 11 + Angular 22 | 2026-07-06 | GO — `STAGING_DEPLOYED` (`sha-9713fdaa`), STG-ISOL-01 PASS, smoke 59/0, rendu PDF vérifié visuellement | `gate-staging-sprint11-ep14a-v5.4.1-decision.md` |
| Gate Production Sprint 11 seul | 2026-07-06 | **NO GO par construction** (ADR-15 K5 — QR pointe vers une page publique non livrée) | `gate-production-sprint11-ep14a-decision.md` |
| Sprint 12 (EP-14b) — API/page publiques, observabilité, rate-limit | 2026-07-06 | GO PO, US-102/103/104 livrées, `mvn verify`/`ng test` verts | PR #202 |
| Revue sécurité + clôture Sprint 12 + Gate Staging Sprint 12 | 2026-07-06 | PASS/GO — `sha-75646d8f` déployé staging, STG-ISOL-01 PASS, Flyway 22/22, smoke 62/0, QR/PDF réel VALIDE | `gate-staging-sprint12-ep14b-v5.4.1-decision.md` |
| Gate Production unique `1.9.0` | 2026-07-06 | **GO — `PRODUCTION_READY`** ; RSV-PROD-EP14-01/02 levées | `gate-production-v1.9.0-decision.md` |
| Préflight + backup | 2026-07-06 ~18:13–18:16 CEST | PASS — `loyertracker-20260706-181545.dump` (325755 octets, 742 entrées, SHA-256 vérifié) | `preflight-backup-v1.9.0-report.md` |
| Déploiement technique | 2026-07-06 17:50:11–17:50:53 UTC | PASS — `sha-75646d8f` actif, V22 appliquée (22/22), digests conformes, `api`+`nginx` seuls recréés | `deploiement-technique-v1.9.0-report.md` |
| Smoke Production | 2026-07-06 ~17:52–17:57 UTC | **62 PASS / 0 FAIL au premier passage** ; nettoyage transactionnel 0 résidu | `validation-finale-v1.9.0-report.md` |
| Hypercare T0 | 2026-07-06 ~17:58 UTC | PASS | `plan-etape-hypercare-v1.9.0.md` |
| Hypercare T+12/T+24 combiné (rattrapage) | 2026-07-08 ~14:36 UTC (≈ T+45) | PASS sous surveillance — T+12 et T+24 en rattrapage (hôte volontairement éteint durant les deux fenêtres cibles) ; aucun critère de suspension atteint | `plan-etape-hypercare-v1.9.0.md` |

## 2. Périmètre livré

**EP-14 — Quittances certifiées, vérifiables et infalsifiables (Sprint 11 + Sprint 12, ADR-15)**

- Migration **V22** : `quittance` versionnée, `quittance_numerotation` par bailleur+année,
  `quittance_verification_log`, RLS FORCE, fonctions `SECURITY DEFINER`. Additive : rollback
  applicatif seul viable.
- **US-99/100/101** (Sprint 11) : émission idempotente par empreinte métier, ré-émission
  chaînée `remplacee_par`, annulation auditée, numéro `QT-YYYY-NNNNNN` jamais réutilisé, token
  HMAC-SHA256 (`QUITTANCE_HMAC_SECRET` hors dépôt + `token_kid`), QR ZXing, gabarit PDF A4
  professionnel (logo, badge certifié, cachet, thème injectable, PAdES-ready).
- **US-102** (Sprint 12) : API publique `GET /api/public/receipts/{id}` + `/download`,
  projection K2 stricte sans fuite (`paiement.mode`/`garantie_retenue` jamais exposés), échec
  indifférencié, re-hash `pdf_hash`.
- **US-103** : page Angular `/verify/receipt/:id` sans `authGuard`, `noindex`, intercepteur
  Bearer corrigé pour exclure `/api/public/`.
- **US-104** : métriques Prometheus (`quittance_verifications_total`,
  `_telechargements_total`, `_qr_invalides_total`), journal RGPD-minimal, rate-limit nginx
  `/api/public/` + `/verify/`.
- Sprint Angular 20→22 (empilé sur `main`, sans défaut propre) inclus dans la même release
  unique sur arbitrage PO (RSV-PROD-EP14-02, option A).

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| **RSV-PROD-EP14-01** | Route QR `/verify/receipt/:id` absente en Production | ✅ **LEVÉE** — Sprint 12 livré, parcours réel vérifié en Staging et confirmé en Production (smoke 62/0) |
| **RSV-PROD-EP14-02** | Sprint Angular 22 empilé sur `main`, hérite du verrou EP-14 sans défaut propre | ✅ **LEVÉE** — option A exécutée : inclus dans la release unique `1.9.0` |
| Fenêtre hypercare T+12/T+24 dépassée (hôte éteint) | Écart de fenêtre sans rapport avec la santé de la release | ✅ **QUALIFIÉE PO** — pattern récurrent `1.4.0`→`1.8.0` (produit non annoncé, hôte éteint entre opérations) ; aucune alerte reçue durant l'intervalle, aucun signal de suspension |
| Heartbeat backup absent au checkpoint | Cron `02:15` non joué hôte éteint | ✅ **QUALIFIÉE sans impact** — résorption au prochain passage du cron pendant que l'hôte est actif |
| `RSV-STG-01` (héritée) | Confirmation live `STG-ISOL-01` au prochain déploiement Staging mutualisé | ⚠️ **MAINTENUE** — sans rapport avec `1.9.0`, à reconfirmer au prochain déploiement Staging |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-75646d8f` |
| Digest API | `sha256:3c2279102fd4bf902ea82946d37d13faab8d8c98124c44b7c651666d7aa71aff` |
| Digest Web (nginx) | `sha256:f0146fa6ca87733e6aebc1f127d19db4c9bd2b233b18d198a3b02e819e319a04` |
| Flyway | V1→**V22** (22/22) |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 depuis le dernier redémarrage hôte |
| Actuator | `{"status":"UP"}` |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | `[]` — 0 alerte active |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| `.env` | `LOYERTRACKER_TAG=sha-75646d8f` persisté (backup `.env.bak-pre-1.9.0`, 600) |
| 5xx (30 min) | 0 |
| Invariant ledger (garantie) | 3/3 PASS (2100,00 / 600,00 / 600,00) |
| Quittances | 0 — aucune émission réelle depuis le go-live |
| Données métier | 2 bailleurs réels, 1 gestionnaire, 2 patrimoines, 3 biens, 3 baux, 75 paiements |
| `bailleur-test` | `enabled=false` ; `directAccessGrantsEnabled=false` (vérifié `kcadm.sh`) |
| Site public | `https://loyertracker.loyerpro.org` → 200 |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.9.0` CLÔTURÉE le 2026-07-08.**

- Hypercare T0 PASS + checkpoint combiné T+12 (rattrapage)/T+24 (rattrapage) PASS sous
  surveillance — écarts de fenêtre qualifiés par le PO (hôte volontairement éteint entre les
  opérations, produit non annoncé publiquement, pattern déjà observé sur `1.4.0`→`1.8.0`).
- US-99→104 (quittances certifiées, vérifiables et infalsifiables) confirmées en Production :
  V22 opérationnelle, invariant du ledger inchangé et cohérent, surface publique de vérification
  sans oracle ni fuite K2, aucune alerte de sécurité ou de fonctionnement.
- RSV-PROD-EP14-01 et RSV-PROD-EP14-02 levées au Gate Production. Aucune anomalie technique
  détectée entre le déploiement et la clôture (0 restart inattendu, 0 dérive de tag/digest, 0
  5xx, 0 alerte).
- `RSV-STG-01` (héritée) maintenue ouverte, sans rapport avec cette release, à reconfirmer au
  prochain déploiement Staging mutualisé.
- Prochaines actions autorisées : cadrage du prochain lot backlog PO selon le parcours gouverné
  (Plan d'Exécution → Sprint → Gate Staging incl. `STG-ISOL-01` → Gate Production).
