# Gate Staging v5.4.1 — Sprint 11 (EP-14a Quittances certifiées) + Sprint Angular 20→22

| Champ | Valeur |
|-------|--------|
| Date | 2026-07-06 |
| Périmètre | Sprint 11 EP-14a (quittance certifiée persistante, token HMAC + QR, PDF pro) + Sprint montée de version Angular 20 → 22 (frontend) |
| Référence | PR #183 (merge `eddc037`, Sprint 11) ; PR #197 (merge `05a4f86`, Angular 22) ; PR #194/#195/#196/#198 (correctifs CI et documentation, docs-only) |
| Commit HEAD candidat | `9713fda` (`origin/main`) |
| Tag GHCR candidat | `sha-9713fdaa` (publié par CI post-push, confirmé présent pour `loyertracker-api` et `loyertracker-web`) |
| Tag Staging précédent / rollback | `sha-2c5f43c7` (release `1.8.0`) |
| Environnement | Staging `https://loyertracker.staging.loyerpro.org` (`ai-test-server`) |
| Décision | **GO** — smoke 59/0, Flyway 22/22, STG-ISOL-01 PASS avant/après, rendu PDF certifié vérifié visuellement |
| Statut CGPA v5.4.1 | `STAGING_DEPLOYED` ✅ (2026-07-06) |

## 1. Objet du Gate

Ce Gate Staging statue le passage Staging du lot combiné Sprint 11 (EP-14a) + montée de version
Angular 20→22, sans déclencher de Production. Les deux composantes sont inséparables car elles
coexistent sur `main` HEAD au moment de ce Gate — même pattern de gouvernance que le Gate Staging
combiné CORS + Sprint 3 du 2026-06-25 (`gate-staging-cors-sprint3-v5.3-decision.md`).

**Périmètre inclus :**

- **Sprint 11 (EP-14a) — quittance certifiée** (PR #183, merge `eddc037`) : migration **V22**
  (`quittance` versionnée, `quittance_numerotation`, `quittance_verification_log`, RLS FORCE),
  `QuittanceCertifieeService` (émission idempotente, numéro `QT-YYYY-NNNNNN`), `TokenQuittanceService`
  (HMAC-SHA256 + `token_kid`), QR ZXing, gabarit PDF A4 professionnel, extension export RGPD.
- **Sprint montée de version Angular 20 → 22** (PR #197, merge `05a4f86`) : `@angular/*` (8 paquets)
  + cli/build-angular/compiler-cli → 22.0.5, `typescript` → 6.0.3, `angular-eslint` → 22.0.0,
  `keycloak-angular` → 22.0.0. Cadrage préalable : `plan-execution-upgrade-angular-22.md` (GO PO).
- **Correctifs CI/documentation** (PR #194 CodeQL init/analyze désynchronisé, PR #195/#196/#198
  docs-only) : sans impact applicatif.

**Hors périmètre :**

- API/page publiques de vérification `/verify/receipt/:id` (Sprint 12, EP-14b) — le QR imprimé sur
  la quittance y pointe déjà mais la page cible n'est pas encore livrée (attendu, ADR-15 K5).
- Promotion Production — **interdite avant la release unique `1.9.0`** après le Sprint 12
  (ADR-15 K5, rappelé au Sprint 11).

## 2. Conditions d'entrée

| Critère | Statut | Preuve |
|---------|--------|--------|
| Sprint(s) identifié(s) | OK | Sprint 11 EP-14a ; Sprint Angular 20→22 |
| Plan(s) d'Exécution approuvé(s) | OK | `plan-execution-ep14-quittances-certifiees.md` ; `plan-execution-upgrade-angular-22.md` (GO PO explicite) |
| Rapport d'exécution disponible | OK | `mvn verify` 156/156, `ng test` 85/85 (Sprint 11) ; critères GO §4 validés localement + CI (sprint Angular) |
| Commit / artefact candidat identifié | OK | `9713fda` HEAD `origin/main` ; tag GHCR `sha-9713fdaa` confirmé (api + web) |
| Environnement Staging identifié | OK | `ai-test-server`, `docs/staging-state.md` |

## 3. Contrôles Sprint

| Critère | Statut | Preuve |
|---------|--------|--------|
| Stories terminées listées | OK | Quittance certifiée persistante (V22), token HMAC+QR, PDF pro, export RGPD étendu ; bump coordonné Angular 22 |
| Stories exclues ou reportées listées | OK | `/verify/receipt/:id` (Sprint 12) ; `eslint` 9→10 volontairement hors périmètre Angular (angular-eslint 22 accepte encore `^9`) |
| Validation Product Owner | OK | GO PO explicite sur le cadrage Angular 22 (2026-07-06) ; clôture Sprint 11 déjà actée (PR #183) |
| Validation Release Manager | OK | Staging uniquement ; Production explicitement exclue de ce Gate (verrou ADR-15 K5 rappelé) |

## 4. Contrôles DevSecOps

| Critère | Statut | Preuve |
|---------|--------|--------|
| Build stable | OK | CI `9713fda` : CodeQL ✅, Backend ✅, Frontend ✅, Sécurité ✅, Packaging Docker ✅ |
| Tests backend | OK | `mvn verify` 156/156 (Sprint 11, payload canonique, token, RLS, concurrence numérotation) |
| Tests frontend | OK | `ng test` 85/85 (Karma headless, post-bump Angular 22) |
| Vérification navigateur (risque `OnPush` Angular 22) | OK | Dashboard bailleur rendu correctement en local (stack Docker complète, flux OIDC/PKCE réel), rechargement async confirmé fonctionnel |
| Secrets / SCA / images | OK | Gitleaks, Trivy SCA + images api/web — CI verte sur PR #197 et #183 |
| Migrations DB | OK | V22 (`quittance`, `quittance_numerotation`, `quittance_verification_log`, RLS FORCE) ; smoke script aligné à 22 dans la même PR que la migration (PR #183) |
| Secrets non exposés | OK | `QUITTANCE_HMAC_SECRET` généré et injecté hors dépôt (préflight, `.env` hôte uniquement) |

## 5. Gate STG-ISOL-01 (CGPA v5.4.1)

> Checklist complète : `docs/cgpa/checklists/stg-isol-01-checklist.md`

| Critère | Statut | Preuve |
|---------|--------|--------|
| Namespace Compose explicite et unique | PASS | `loyertracker-staging` (`docker inspect` labels confirmés) |
| Réseaux / volumes namespacés | PASS | `loyertracker-staging_loyertracker-net`, `loyertracker-staging_postgres-data`, `loyertracker-staging_prometheus-data` |
| Absence de conflit de ports | PASS | `18080`/`18443` ; `pushgateway` bindé `127.0.0.1` uniquement ; services internes non publiés |
| Reverse proxy mutualisé par nom DNS | PASS | nginx-proxy-manager Proxy Host #18 → `loyertracker.staging.loyerpro.org`, conteneur intact |
| Absence de commande Docker globale | PASS | Déploiement ciblé `docker compose -f docker-compose.staging.yml pull/up` (api + nginx uniquement) |
| Conteneurs des autres projets inchangés | PASS | Avant : 8 `loyertracker-staging-*` + `nginx-proxy-manager` ; après : identique — aucun autre projet affecté |
| **STG-ISOL-01** | **PASS** | Vérifié avant ET après déploiement (2026-07-06), lecture seule puis après recréation `api`/`nginx` |

## 6. Préflight exécuté

- `QUITTANCE_HMAC_SECRET` absent du `.env` hôte avant ce Gate (défaut par défaut vide dans
  `docker-compose.staging.yml` — signature HMAC insecure/non fonctionnelle en l'état) : généré
  (`openssl rand -hex 32`) et injecté, avec `QUITTANCE_TOKEN_KID=1` et `QUITTANCE_VERIFY_BASE_URL`
  confirmée (`https://loyertracker.staging.loyerpro.org`). Sauvegarde `.env` pré-changement
  conservée (`.env.bak-pre-sprint11-gate-staging`).
- Compteur Flyway du smoke déjà aligné à 22 (fait dans la même PR que la migration, PR #183) —
  aucune correction nécessaire.
- `git pull --ff-only origin main` sur l'hôte : `20d6701` → `9713fda` (fast-forward, 58 fichiers).

## 7. Déploiement Staging

```bash
# 1. Synchroniser le dépôt
cd ~/loyertracker
git pull --ff-only origin main        # → HEAD 9713fda

# 2. STG-ISOL-01 — avant
docker ps --format "{{.Names}}"

# 3. Déployer avec le tag candidat
sed -i 's/^LOYERTRACKER_TAG=.*/LOYERTRACKER_TAG=sha-9713fdaa/' .env
docker compose -f docker-compose.staging.yml pull
docker compose -f docker-compose.staging.yml up -d

# 4. Vérifier la santé
docker compose -f docker-compose.staging.yml ps

# 5. STG-ISOL-01 — après
docker ps --format "{{.Names}}"

# 6. Smoke test (V22 attendu)
BASE=https://localhost:18443 COMPOSE_FILE=docker-compose.staging.yml ./infra/smoke/smoke-stack.sh
```

## 8. Vérification visuelle du rendu PDF certifié (objectif spécifique du Sprint 11)

Réalisée via tunnel SSH local (port 443 → 18443 distant) + Chromium headless piloté par script,
flux OIDC/PKCE réel contre le Keycloak Staging (bypass volontaire du reverse proxy public pour
éviter de manipuler `directAccessGrantsEnabled`, mesure de sécurité déjà révoquée intentionnellement) :

- Connexion `bailleur-test@test.local` réussie.
- Adresse postale bailleur renseignée via « Mon profil » (mention légale obligatoire de la
  quittance, absente du compte de test — 409 `Adresse bailleur manquante` observé puis résolu).
- Téléchargement réel de la quittance certifiée pour la période 2026-01 (`RECU`, 900,00 €) via le
  bouton applicatif « Télécharger la quittance ».
- PDF rendu (`pdftoppm`) et inspecté visuellement : logo LoyerTracker, badge **DOCUMENT CERTIFIÉ**,
  numéro **QT-2026-000001** / version 1 / date d'émission, bailleur identifié, **locataire anonymisé
  `[anonymisé]`** (cohérent RGPD), détail Patrimoine/Bien/Période, tableau Loyer HC (800,00 €) +
  Provision charges (100,00 €) = Total reçu 900,00 €, **QR code de vérification** pointant vers
  `https://loyertracker.staging.loyerpro.org/verify/receipt/{id}?token=...` avec empreinte SHA-256
  affichée, mentions légales de bas de page.
- Le QR pointe vers une page (`/verify/receipt/:id`) non encore livrée (Sprint 12) — **conforme à
  l'attendu**, ne bloque pas ce Gate Staging (bloque uniquement la Production, ADR-15 K5).

## 9. Avis des sous-agents

| Sous-agent | Avis |
|------------|------|
| Governance Officer | GO : plans d'exécution approuvés (Sprint 11 + Angular 22), aucun Gate/décision/risque historique rejoué ou supprimé, combinaison de lots tracée et motivée (précédent CORS+Sprint 3) |
| Enterprise Architect | GO : V22 additive (rollback applicatif seul), bump Angular 22 sans API supprimée utilisée dans le code, RLS/ReBAC inchangés |
| DevSecOps Lead | GO : CI `9713fda` verte (7/7), smoke 59/0, STG-ISOL-01 PASS avant/après, secret HMAC généré hors dépôt |
| Release Manager | GO Staging uniquement : aucune autorisation Production par ce Gate ; rappel explicite du verrou ADR-15 K5 (release unique `1.9.0` après Sprint 12) |

## 10. Décision

**Verdict : GO** — 2026-07-06

Preuves :
- CI `9713fda` : CodeQL ✅, Backend ✅, Frontend ✅, Sécurité ✅, Packaging Docker ✅.
- Smoke **59 PASS / 0 FAIL** : Flyway 22/22, parcours métier complet (S01→S04, invitation/acceptation,
  affectation, échéances, pointage, honoraires, alertes, audit, isolation cross-tenant, RGPD export/effacement).
- STG-ISOL-01 : conteneurs `loyertracker-staging-*` identiques avant/après — aucun autre projet affecté.
- Rendu PDF certifié vérifié visuellement en conditions réelles (§8).

Statuts :

- `STAGING_READY` : ✅ atteint.
- `STAGING_DEPLOYED` : ✅ atteint (2026-07-06, `sha-9713fdaa`).
- `PRODUCTION_READY` / `PRODUCTION_DEPLOYED` : non atteints — Gate Production distinct requis,
  **et explicitement interdit avant la release unique `1.9.0`** (ADR-15 K5 : le QR imprimé pointe
  vers une page publique livrée au Sprint 12).

## 11. Réserves et suite

- Production non autorisée par ce Gate, et bloquée par construction avant la release `1.9.0`.
- Sprint 12 (EP-14b — API/page publiques `/verify/receipt/:id`, observabilité, rate-limit nginx) a
  son plan déjà approuvé (PR #182) et peut démarrer indépendamment de ce Gate.
- PR dependabot `#190` (eslint 9→10) reste ouverte et hors périmètre (volontairement exclue du
  sprint Angular 22, cf. `plan-execution-upgrade-angular-22.md` §2.5) — laissée en l'état.
