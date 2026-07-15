# Gate Staging v5.4.1 — Sprints A+B (EP-15, Gestion des personnes)

| Champ | Valeur |
|---|---|
| Date | 2026-07-15 |
| Candidat | merge PR #209 `4d8a760` + PR #217 documentaire `c9200a5`, tag `sha-c9200a51` |
| Rollback | `sha-75646d8f` |
| Environnement | `ai-test-server`, `https://loyertracker.staging.loyerpro.org` |
| Décision | **GO — `STAGING_DEPLOYED`** |

## Conditions d'entrée

| Critère | Statut | Preuve |
|---|---|---|
| Sprint A (Gestionnaire, US-105→108) codé et vert | PASS | `mvn verify` 168/168 (session du 2026-07-08), `docs/project-state.md` §11 |
| Sprint B (Locataire, US-109→112) codé et vert | PASS | `mvn verify` 173/173 (session du 2026-07-09) |
| Revue de code PR #209 + correctifs | PASS | 2 bugs de correction détectés et corrigés avant merge (photo Gestionnaire, `dateCreation` Locataire), 2 tests de non-régression ajoutés — `docs/project-state.md` §11 (entrée 2026-07-15) |
| Revue sécurité dédiée (pattern cross-tenant `SECURITY DEFINER`) | PASS | `docs/cgpa/07-devsecops/security-review-sprints-ab-ep15.md` |
| CI et images | PASS | CI post-merge verte (7/7) sur `4d8a760` et `c9200a5` ; images `loyertracker-api`/`loyertracker-web` `sha-c9200a51` présentes sur GHCR |
| Sauvegarde pré-déploiement | PASS | `loyertracker-staging-20260715-105158.dump`, 531336 octets, SHA-256 `6465f6d7f68a66c153dc7e36026d1f2a24442c2efaa16107edd784379a14d7fa`, catalogue 777 entrées |

## STG-ISOL-01

| Contrôle | Avant | Après | Résultat |
|---|---|---|---|
| Projet Compose | `loyertracker-staging` | identique | PASS |
| Conteneurs projet | 8, tous Up, 4 healthy applicables | identiques ; seuls API/Nginx recréés | PASS |
| NPM mutualisé | running, restart=0 | running, restart=0 | PASS |
| Réseau dédié | `loyertracker-staging_loyertracker-net` | identique | PASS |
| Ports | Nginx 18080/18443 ; Pushgateway 127.0.0.1:9091 | identiques | PASS |
| Services internes | aucun port hôte API/PostgreSQL/Keycloak | identique | PASS |
| Commandes | `git pull --ff-only`, `docker compose -f docker-compose.staging.yml pull/up -d --no-deps api nginx` | ciblées, aucune commande Docker globale | PASS |
| Restart counts | 0 pour tous | 0 pour tous (y compris après vérification fonctionnelle live) | PASS |

**Verdict STG-ISOL-01 : PASS.** Aucun conteneur, réseau, volume ou reverse proxy tiers n'a été
arrêté, supprimé ou modifié. Seuls `api` et `nginx` du projet `loyertracker-staging` ont été
recréés ; `postgres`/`keycloak`/l'overlay monitoring sont restés inchangés.

## Déploiement et validation

- Dépôt hôte fast-forward `75646d8` → `c9200a5` (49 fichiers).
- `.env` : `LOYERTRACKER_TAG` `sha-75646d8f` → `sha-c9200a51` (sauvegarde `.env.bak-pre-s2-c9200a51`).
- Déploiement strictement ciblé : `api` et `nginx` recréés ; PostgreSQL, Keycloak et l'overlay
  monitoring inchangés.
- Flyway : **V23 (statut Gestionnaire) + V24 (table Locataire) appliquées, total 24/24**.
- Smoke **62 PASS / 0 FAIL** (le script ne couvre pas encore les nouveaux endpoints
  `/api/gestionnaires`/`/api/locataires` — même pattern que les garanties Sprint 9/10, complété
  ci-dessous par une vérification manuelle dédiée).

## Vérification fonctionnelle live — Gestionnaire + Locataire (48 PASS / 0 FAIL)

Le script de smoke ne couvrant pas les nouveaux endpoints, une vérification manuelle en direct sur
l'API réelle (JWT Keycloak réels via Nginx, RLS PostgreSQL réelle) a été exécutée avec le même
échafaudage kcadm (`directAccessGrants` temporaire, révoqué automatiquement en fin de script) :

- **Locataire** : création avec photo (201), **`dateCreation` correctement renseignée** (confirme
  en conditions réelles le correctif du bug #2), recherche, détection de doublon
  (`numeroPieceIdentite`), modification, archivage (200 puis 409 sur ré-archivage), restauration,
  historique (4 écritures d'audit), **isolation RLS cross-tenant confirmée (404 pour un autre
  bailleur)**.
- **Gestionnaire** : mise à jour de profil avec photo puis **mise à jour partielle sans
  `photoBase64` — photo confirmée conservée** (confirme en conditions réelles le correctif du
  bug #1), **RBAC ReBAC confirmé (403 pour un bailleur sans relation)**, cycle de vie complet
  (suspension → réactivation, **archivage bloqué en 409 tant qu'une affectation `ACTIVE` existe**,
  révocation de l'affectation, archivage accepté, restauration), recherche, détection de doublon
  (email), historique (6 écritures d'audit).
- **Bilan : 48 PASS / 0 FAIL.** Comptes de test Keycloak créés durant la vérification restent sur
  le realm (pattern déjà en place pour les comptes smoke, suffixés par un id de run) ; échafaudage
  `directAccessGrants` révoqué automatiquement.

## Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | GO : revue de code, revue sécurité dédiée, preuves et décisions ADR-16 présentes |
| Enterprise Architect | GO : RLS Locataire standard, fonctions `SECURITY DEFINER` à surface minimale, ReBAC Gestionnaire cohérents |
| DevSecOps Lead | GO : CI/images, sauvegarde, Flyway, smoke, STG-ISOL-01 et vérification fonctionnelle live tous PASS |
| Release Manager | GO Staging uniquement ; Production soumise à un Gate distinct, Sprints A+B combinés (cf. plan d'exécution) |
| Chief Delivery Officer | **GO — `STAGING_DEPLOYED`** |

## Statuts et suite

- `STAGING_READY` : atteint avant déploiement (revue de code + revue sécurité + CI verte).
- `STAGING_DEPLOYED` : atteint sur `sha-c9200a51`.
- `PRODUCTION_READY` / `PRODUCTION_DEPLOYED` : non atteints.
- Rappel plan d'exécution EP-15 : pas de promotion Production isolée du Sprint A avant le Sprint B
  (cohérence produit) — ce Gate couvre les deux combinés. Le Sprint C (bascule `Bail→Locataire`,
  V25 non additive) reste hors périmètre et ne pourra être instruit qu'après un cycle de release
  Production complet du Sprint B sans anomalie.
- Prochaine étape autorisée : instruire le Gate Production Sprints A+B (distinct). Ce document
  n'autorise aucun déploiement Production.
