# Clôture Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-06-27 |
| Heure CDO GO | 09:15 UTC (après T+24 anticipé PASS) |
| Release | `1.2.1` |
| Tag Production | `sha-47172297` |
| Commit applicatif | `c1e9c735e39c0375b907be9da3302e67f5cb10d4` |
| `PRODUCTION_DEPLOYED` | 2026-06-27 09:08 UTC |
| Statut | **RELEASE CLÔTURÉE — CDO GO** |

## 1. Récapitulatif du cycle `1.2.1`

| Étape | Date | Résultat | Document |
|---|---|---|---|
| 1 — Candidat + CI | 2026-06-27 | PASS — `sha-47172297` retenu, CI SUCCESS | `release-candidate-v1.2.1.md` |
| 2 — Gate Staging | 2026-06-27 | GO — `STAGING_DEPLOYED` ; STG-ISOL-01 PASS live ; smoke 47/0 | `gate-staging-v1.2.1-decision.md` |
| 3 — Gate Production | 2026-06-27 | GO sous réserve — `PRODUCTION_READY` ; RP-121-01 bloquante | `gate-production-v1.2.1-decision.md` |
| 4 — Préflight + backup | 2026-06-27 | PASS — backup 311 Kio, 730 entrées, RP-121-01 levée | `preflight-backup-v1.2.1-report.md` |
| 5 — Déploiement | 2026-06-27 08:57 UTC | PASS — `sha-47172297` actif, 8/8 Up, digests conformes | `deploiement-technique-v1.2.1-report.md` |
| 6 — Validation finale | 2026-06-27 09:08 UTC | `PRODUCTION_DEPLOYED` — smoke 47/0, `c1e9c73` confirmé | `validation-finale-v1.2.1-report.md` |
| Hypercare T0 | 2026-06-27 09:08 UTC | PASS | `plan-etape-hypercare-v1.2.1.md` |
| Hypercare T+12 anticipé | 2026-06-27 09:13 UTC | PASS | `plan-etape-hypercare-v1.2.1.md` |
| Hypercare T+24 anticipé | 2026-06-27 09:15 UTC | PASS | `plan-etape-hypercare-v1.2.1.md` |

## 2. Correctif livré

**`c1e9c73`** — `fix(dashboard): charge les biens même si l'inscription échoue`

- `chargerBiens()` appelé via l'opérateur RxJS `finalize` : s'exécute en succès comme en erreur.
- `chargerReferentielsBien()` lancé immédiatement en parallèle de l'inscription.
- Le dashboard bailleur affiche désormais les biens même si l'utilisateur est déjà inscrit (409).
- Comportement confirmé en Production : `POST /api/bailleurs/inscription → 409` + biens chargés.

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| **RP-120-03** | `c1e9c73` exclu de `1.2.0` | ✅ **LEVÉE** — correctif en Production depuis le 2026-06-27 |
| **RP-121-01** | Backup pré-déploiement | ✅ **LEVÉE** — Étape 4 (2026-06-27) |
| **RP-120-02** | Rollback schéma V15 non trivial | Maintenue — rollback au-delà de `1.2.0` vers `1.1.x` toujours via pg_restore |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-47172297` |
| Digest Web | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |
| Digest API | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Flyway | V1→V15 (15/15) — inchangé |
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 |
| Actuator | `{"status":"UP"}` |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | `BackupHeartbeatMissing` pré-existante (Pushgateway volatil post-reboot serveur) |
| `.env` | `LOYERTRACKER_TAG=sha-47172297` persisté |

## 5. Note opérationnelle post-clôture

**`bailleur-test@test.local` désactivé dans Keycloak (`enabled: false`) :** ce compte a été
désactivé lors du nettoyage post-smoke `1.2.0` (2026-06-26). Il doit être réactivé manuellement
avant chaque smoke Production. À intégrer dans le runbook de validation finale.

**`BackupHeartbeatMissing` :** l'alerte se résoudra automatiquement au prochain passage du cron
de backup (02:15 UTC le 2026-06-28). Aucune action requise.

## 6. Décision CDO

**Chief Delivery Officer : GO — Release `1.2.1` CLÔTURÉE le 2026-06-27.**

- Hypercare T0/T+12/T+24 anticipés — tous PASS.
- Correctif `c1e9c73` confirmé en Production.
- RP-120-03 levée.
- Prochaine action autorisée : prochain lot fonctionnel ou correctif selon backlog PO.
