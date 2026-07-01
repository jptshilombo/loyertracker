# Plan d'Exécution — Release `1.4.0` (Sprint 5)

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-30 |
| Statut | **RELEASE CLÔTURÉE — CDO GO 2026-07-01 06:51 UTC — T0/T+12 anticipé/T+24 PASS** |
| Type | Release MINOR — nouvelles fonctionnalités métier + migrations SQL |
| Version cible | `1.4.0` |
| Tag Production | `sha-98afa99a` (merge commit PR #115) |
| Production précédente | `1.3.0` — `sha-a42d860d` |
| Rollback disponible | `sha-a42d860d` (applicatif seul) + pg_restore V15 si rollback schéma requis |
| Autorisation de départ | Clôture `1.3.0` — CDO GO (2026-06-29) |

## 1. Objet et périmètre

### Objet

La release `1.4.0` livre le Sprint 5 complet : corrections UX post-Sprint 4 (Lots A, C et PR #110)
et nouvelles fonctionnalités métier sur les biens, les baux et les paiements (Lot B — Flyway V16/V17/V18).

### Périmètre

**PR #110 — Correctifs UX post-Sprint 4**
- Lien profil bailleur présent dans la navbar.
- Message d'erreur 409 quittance actionnable (adresse manquante).
- Section affectations patrimoine (É-01) visible dans l'interface.

**PR #113 — Sprint 5 Lot A+C (UX alertes et inscription)**
- Lot A : filtre `statut === 'NON_LUE'` sur les alertes ; nettoyage badge et style.
- Lot C : suppression du bruit à l'inscription ; affichage des statuts `A_VENIR` dans la liste des échéances.

**PR #115 — Sprint 5 Lot B (backend + migrations V16/V17/V18)**
- B1 — `bien.statut` sync rétroactive → `LOUE` pour biens avec bail `ACTIF` (migration V16).
- B2 — `patrimoine.adresse` : nouveau champ adresse sur le patrimoine (migration V16).
- B3 — `bail.devise` : support EUR/USD/CDF sur le bail (migration V17).
- B4 — `StatutPaiement.A_VENIR` : nouveau statut, contrainte CHECK étendue, réécriture de
  `generer_echeances_loyers()` pour produire `A_VENIR` sur les échéances futures (migration V18).

**PR #116 — Smoke fix**
- `infra/smoke/smoke-stack.sh` : compteur Flyway aligné 15→18.

### Migrations Flyway

| Version | Contenu | Rollback schéma |
|---|---|---|
| V16 | `bien.statut` rétroactif + `patrimoine.adresse` | Modéré — pg_restore requis |
| V17 | `bail.devise` (EUR/USD/CDF) | Modéré — pg_restore requis |
| V18 | `StatutPaiement.A_VENIR` + `paiement_statut_check` + `generer_echeances_loyers()` | Non trivial — pg_restore requis |

## 2. Étapes exécutées

---

### Étape 1 — CI PR #115 et intégration `main`

**Objectif :** corriger les échecs CI sur PR #115 et intégrer à `main`.

**Actions :**
- Diagnostic CI : `S03PaiementsGarantiesIntegrationTest` → `paiement_statut_check` ne couvrait pas `A_VENIR`.
- Fix : ajout `ALTER TABLE paiement DROP CONSTRAINT / ADD CONSTRAINT` en tête de V18, avant tout DML.
- Fix : `SchemaMigrationTest` — compteur 15→18.
- Fix smoke via PR #116 (branche protégée).
- CI 100% verte, PR #115 mergée.

**Statut : EXÉCUTÉE — PR #115 mergée, CI 100% PASS, PR #116 mergée.**

---

### Étape 2 — Gate Staging Sprint 5 Lot B

**Objectif :** déployer `sha-98afa99a` sur `ai-test-server`, STG-ISOL-01 live, smoke 47/0.

**Actions :**
- STG-ISOL-01 pré-déploiement : 9 conteneurs `loyertracker-staging-*`, `nginx-proxy-manager` intact.
- Déploiement : `api` + `nginx` recréés, `postgres`/`keycloak` non redémarrés.
- Flyway : V16/V17/V18 appliquées → 18/18 success.
- Smoke interne port 18443 : **47 PASS / 0 FAIL**.
- STG-ISOL-01 post-déploiement : 9 conteneurs identiques, aucun autre projet affecté.

**Statut : EXÉCUTÉE le 2026-06-30 — GO, `STAGING_DEPLOYED`. Décision : `docs/project-state.md`.**

---

### Étape 3 — Gate Production Sprint 5

**Objectif :** autoriser la promotion en Production.

**Actions :**
- Vérification checklist Gate Production CGPA v5.4.1.
- Artefact `sha-98afa99a` retenu, CI SUCCESS, digests GHCR immuables.
- Rollback : `sha-a42d860d` (applicatif) + pg_restore V15 si rollback schéma.
- Réserve bloquante RP-140-01 (backup pré-déploiement).

**Statut : EXÉCUTÉE le 2026-06-30 — GO sous réserve, `PRODUCTION_READY`. Décision : `docs/project-state.md`.**

---

### Étape 4 — Préflight + backup Production

**Objectif :** état sain de la Production et backup pré-déploiement.

**Actions :**
- 4/4 healthy, restart=0, `LOYERTRACKER_TAG=sha-a42d860d` confirmé, Flyway 15/15.
- Prometheus 5/5 up, Alertmanager 0 alerte (après levée `BackupHeartbeatMissing`).
- Capacité hôte : 32 Gio libres, 2,1 Gio RAM, charge 0,11.
- Backup : `loyertracker-20260630-160619.dump` (312 Kio, SHA-256 `60b1fd74…`, globals `ee660fc5…`),
  permissions 600, `pg_restore --list` 730 entrées.
- RP-140-01 **LEVÉE**.

**Statut : EXÉCUTÉE le 2026-06-30 16:06 UTC — PASS, RP-140-01 LEVÉE.**

---

### Étape 5 — Déploiement technique `1.4.0`

**Objectif :** promouvoir `sha-98afa99a` en Production.

**Actions :**
- `git pull --ff-only origin main` sur prod server → HEAD = `a40a8ad`.
- `docker compose pull api nginx` → images `sha-98afa99a` tirées.
- `docker compose up -d --no-deps api nginx` → `api` + `nginx` recréés ; `postgres`/`keycloak`/monitoring inchangés.
- Flyway logs : « Successfully applied 3 migrations to schema "public", now at version v18 ».
- 4/4 (healthy), restart=0. Actuator `{"status":"UP"}`.
- `.env` persisté : `LOYERTRACKER_TAG=sha-98afa99a`, SHA-256 `1e3f9a7d…`.

**Statut : EXÉCUTÉE le 2026-06-30 ~15:12 UTC — PASS.**

---

### Étape 6 — Validation finale, hypercare et clôture

**Objectif :** smoke 47/0, `PRODUCTION_DEPLOYED`, hypercare, CDO GO.

**Actions :**
- `bailleur-test@test.local` réactivé temporairement (était désactivé), smoke exécuté, redésactivé.
- Smoke Production : **47 PASS / 0 FAIL** (~15:15 UTC).
- `PRODUCTION_DEPLOYED` atteint.
- Hypercare T0 PASS (15:12 UTC), T+12 anticipé PASS (15:25 UTC), T+24 PASS (2026-07-01 06:38 UTC).
- Backup T+24 : `loyertracker-20260701-074122.dump` (316K, 730 entrées).
- CDO GO → **RELEASE `1.4.0` CLÔTURÉE le 2026-07-01**.

**Statut : EXÉCUTÉE — `PRODUCTION_DEPLOYED` + CLÔTURÉE. Dossier : `cloture-release-v1.4.0.md`.**

---

## 3. Registre des documents du cycle `1.4.0`

| Document | Statut |
|---|---|
| `plan-execution-sprint5.md` (ce fichier) | ✅ Créé 2026-07-01 — CLÔTURÉ |
| `preflight-backup-v1.4.0-report.md` | ✅ Synthétisé dans `docs/prod-state.md` §0E |
| `cloture-release-v1.4.0.md` | ✅ Créé 2026-07-01 — CDO GO |
| `docs/release-notes-v1.4.0.md` | ✅ Créé 2026-07-01 — RSV-P140-02 |
| `CHANGELOG.md` — section `[1.4.0]` | ✅ Mis à jour 2026-07-01 — RSV-P140-02 |

## 4. Réserves

| ID | Description | Statut |
|----|-------------|--------|
| RP-140-01 | Backup pré-déploiement | ✅ LEVÉE — préflight 2026-06-30 |
| RSV-T24-01 | Cron backup inactif | ✅ LEVÉE — serveur éteint volontairement (produit non public) |
| RSV-P140-01 | Plan d'Exécution Sprint 5 | ✅ LEVÉE — ce document |
| RSV-P140-02 | Release notes + CHANGELOG | ✅ LEVÉE — `release-notes-v1.4.0.md` + CHANGELOG mis à jour |
