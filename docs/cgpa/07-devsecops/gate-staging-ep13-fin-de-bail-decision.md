# Gate Staging v5.4.1 — Sprint unique EP-13 (Fin de bail)

| Champ | Valeur |
|---|---|
| Date | 2026-07-16 |
| Candidat | merge PR #227 `cba13f5` (Sprint EP-13, US-115→118), tag `sha-cba13f52` |
| Rollback | `sha-c9200a51` (EP-15 Sprints A+B) |
| Environnement | `ai-test-server` (mutualisé), `https://loyertracker.staging.loyerpro.org` (accès direct via `https://localhost:18443` pour ce Gate) |
| Décision | **GO — `STAGING_DEPLOYED`** |

## Conditions d'entrée

| Critère | Statut | Preuve |
|---|---|---|
| Kickoff K1→K6 (ADR-17) tranché par le PO | PASS | `ADR-17-fin-de-bail.md` (Acceptée), `docs/project-state.md` §11 (2026-07-16) |
| Plan d'Exécution approuvé (GO explicite du PO) | PASS | `plan-execution-ep13-fin-de-bail.md` (Approuvé) |
| Sprint codé et vert | PASS | `mvn verify` 185/185 (session du 2026-07-16) |
| CI post-merge | PASS | PR #227 — 7/7 checks verts (Backend/couverture+SonarQube, Frontend, Sécurité, Packaging Docker, CodeQL ×3). Un correctif Sonar (`java:S5778`, lambda de test) a été nécessaire avant que le Quality Gate ne passe |
| Images GHCR | PASS | `loyertracker-api`/`loyertracker-web` `sha-cba13f52` publiées (2026-07-16 ~16:13 UTC), digests confirmés |
| Sauvegarde pré-déploiement | PASS | `loyertracker-20260716-172113.dump`, 549489 octets, SHA-256 `eaad8cda8be1efb824ffcb409303e740e5e6c8c9eade83b697892eea632daf6a`, 798 entrées (`pg_restore --list`) |

## STG-ISOL-01

| Contrôle | Avant | Après | Résultat |
|---|---|---|---|
| Projet Compose | `loyertracker-staging` | identique | PASS |
| Conteneurs projet | 9 (8 `loyertracker-staging-*` + `nginx-proxy-manager`), tous Up | identiques ; seuls `api`/`nginx` recréés | PASS |
| NPM mutualisé (`nginx-proxy-manager`) | running, restart=0 | running, restart=0 | PASS |
| Réseau dédié | `loyertracker-staging_loyertracker-net` | identique | PASS |
| Volumes dédiés | `loyertracker-staging_postgres-data`, `_prometheus-data` | identiques | PASS |
| Ports | Nginx 18080/18443 ; Pushgateway `127.0.0.1:9091` | identiques | PASS |
| Services internes | aucun port hôte API/PostgreSQL/Keycloak | identique | PASS |
| Commandes exécutées | `git pull --ff-only`, `docker compose -f docker-compose.staging.yml pull/up -d --no-deps api nginx` | ciblées, aucune commande Docker globale | PASS |
| Restart counts | 0 pour tous | 0 pour tous (y compris après vérification manuelle live) | PASS |

**Verdict STG-ISOL-01 : PASS.** Aucun conteneur, réseau, volume ou reverse proxy tiers arrêté,
supprimé ou modifié. Seuls `api` et `nginx` du projet `loyertracker-staging` ont été recréés ;
`postgres`/`keycloak`/l'overlay monitoring sont restés inchangés (Up 4 h, restart=0 tout du long).

## Déploiement et validation

- Dépôt hôte fast-forward `c9200a5` → `cba13f5` (31 fichiers).
- `.env` : `LOYERTRACKER_TAG` `sha-c9200a51` → `sha-cba13f52` (sauvegarde
  `.env.bak-pre-ep13-cba13f52`).
- Déploiement strictement ciblé : `api` et `nginx` recréés ; PostgreSQL, Keycloak et l'overlay
  monitoring inchangés.
- Digests confirmés sur l'hôte : API `sha256:72411f5ca7f116520871eb5d1ef1439b2d338bbcd5922359eb7dbd9398ae37d0`,
  Web `sha256:63347a957bc235b870bacfdf94777163b19ac7c39a2362b93f43b1f6a40044c8` — identiques à GHCR.
- Flyway : **V25 appliquée (`ep13 fin de bail`), total 25/25**. Colonne
  `bail.date_cloture_effective` et fonction `generer_alertes()` confirmées présentes.
- `/healthz` → 200.
- Smoke **62 PASS / 0 FAIL** (script non étendu aux nouveaux endpoints
  `.../cloture`/`.../reouverture` — même pattern que Garantie Sprint 9/10 et Locataire/Gestionnaire
  Sprints A+B — complété ci-dessous par une vérification manuelle dédiée).

## Vérification fonctionnelle live — Clôture/réouverture de bail (22 PASS / 0 FAIL)

Le script de smoke ne couvrant pas les nouveaux endpoints, une vérification manuelle en direct sur
l'API réelle (JWT Keycloak réel via Nginx, RLS PostgreSQL réelle) a été exécutée avec le même
échafaudage kcadm (`directAccessGrants` temporaire, révoqué automatiquement en fin de script) :

- **US-115 (clôture avec avertissements)** : bail créé avec une garantie `DETENU` jamais restituée
  et des paiements `EN_RETARD` réels (générés par `/api/batch/echeances`) — `POST .../cloture` →
  **200**, `statut: CLOS`, `dateClotureEffective` renseignée, `dateFin` (contractuelle) **inchangée**,
  **2 avertissements** présents dans la réponse (garantie non restituée + paiements en cours),
  **aucun blocage 409** conformément à K3/K4.
- **US-117 (purge de l'échéancier futur)** : après clôture, **0** paiement `A_VENIR` de période
  strictement postérieure au mois de clôture ; les **6** paiements `EN_RETARD` historiques restent
  intacts (faits immuables, jamais retouchés).
- **US-118 (non-régression alertes)** : `POST /api/batch/alertes` rejoué après la clôture réelle —
  **0** alerte `LOYER_EN_RETARD` générée pour ce bail malgré les paiements `EN_RETARD` non purgés
  (seuls les `A_VENIR` le sont) ; confirme en conditions réelles le filtre `bail.statut = 'ACTIF'`
  ajouté par V25 à la CTE `LOYER_EN_RETARD`.
- **Clôture d'un bail déjà clos** → **409** (garde métier `Bail.cloturer()`).
- **US-116 (réouverture)** : `POST .../reouverture` → **200**, `statut: ACTIF`,
  `dateClotureEffective` remise à `null`.
- **US-116 (collision `uq_bail_actif`)** : bail réouvert sur un bien où un second bail `ACTIF`
  existe déjà (bien libéré manuellement en base pour permettre le scénario, hors périmètre
  applicatif de ce Sprint — cf. ADR-17 §Points assumés) → `POST .../reouverture` → **409**, comme
  attendu (K5).
- **Bilan : 22 PASS / 0 FAIL.** Compte de test Keycloak créé durant la vérification reste sur le
  realm (pattern déjà en place pour les comptes smoke, suffixé par un id de run) ; échafaudage
  `directAccessGrants` révoqué automatiquement.

## Incident de sécurité mineur survenu pendant l'instruction de ce Gate

Une commande de diagnostic exécutée avec `set -x` a tracé le `source .env` sur `ai-test-server` et
imprimé en clair, dans la session de travail (hors dépôt, hors artefact persistant), les 7 secrets
Staging (`POSTGRES_PASSWORD`, `DB_APP_PASSWORD`, `DB_BATCH_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`,
`KEYCLOAK_API_CLIENT_SECRET`, `KEYCLOAK_TEST_BAILLEUR_PASSWORD`, `QUITTANCE_HMAC_SECRET`). Signalé
immédiatement au PO. **Recommandation non bloquante pour ce Gate** (environnement Staging, aucun
secret Production concerné) : faire tourner ces 7 secrets sur `ai-test-server` dans un délai
raisonnable.

## Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | GO : kickoff clos, Plan d'Exécution approuvé, ADR-17 Acceptée, historique tracé |
| Enterprise Architect | GO : `Bail.cloturer()`/`rouvrir()` suit le patron `Locataire.archiver()`/`restaurer()` ; aucun recouplage Garantie/Paiements ; migration additive uniquement |
| DevSecOps Lead | GO : CI/images, sauvegarde, Flyway, smoke, STG-ISOL-01 et vérification fonctionnelle live tous PASS ; incident secrets mineur signalé et non bloquant |
| Release Manager | GO Staging uniquement ; Production soumise à un Gate distinct |
| Chief Delivery Officer | **GO — `STAGING_DEPLOYED`** |

## Statuts et suite

- `STAGING_READY` : atteint avant déploiement (CI verte, images publiées).
- `STAGING_DEPLOYED` : atteint sur `sha-cba13f52`.
- `PRODUCTION_READY` / `PRODUCTION_DEPLOYED` : non atteints.
- Prochaine étape autorisée : instruire le Gate Production du Sprint EP-13 (distinct). Ce document
  n'autorise aucun déploiement Production.
