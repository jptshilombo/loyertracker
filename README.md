# LoyerTracker

Application web de **gestion locative bailleur-centrée avec délégation fine par bien** : centralisation des baux, suivi des paiements de loyer mois par mois (à terme échu), gestion des garanties locatives, honoraires de gestion délégués, et alertes sur les échéances clés (loyers en retard, fin de bail, préavis, garanties non restituées).

## État du projet

Projet gouverné par le **CGPA**. Le projet a été démarré avec le référentiel local v1.0 (`setup-cgpa/`) et migré de manière additive jusqu'à **CGPA v5.4.1**. Le point d'entrée de reprise est `docs/project-state.md`. Phases de gouvernance franchies :

| Gate | Phase | Statut |
|------|-------|--------|
| Gate 0 | Idée / opportunité | ✅ Go |
| Gate 1 | Expression du besoin (EB v1.2) | ✅ Go |
| Gate 2 | Faisabilité | ✅ Go |
| Gate 3 | Cahier des charges | ✅ Go |
| Gate 4 | Architecture & conception | ✅ Go — **verrou de codage levé** |
| Gate 5 | Backlog & planification | ✅ Go |
| Gate 6 | DevSecOps | ✅ Go (16/20) — **ratifié le 2026-06-06** ; R1 clôturée, R2→R5 ouvertes avant prod, R6 partielle |
| Gate Staging | Staging Readiness | ✅ Go — staging réel et smoke validés |
| Gate 09 | Production Readiness | ✅ Go sous réserve — réserves traitées au Gate 10 |
| Gate 10 | Mise en production | ✅ Go — production `1.2.1` LIVE depuis le 2026-06-27 |
| Gate `STG-ISOL-01` | Isolation Staging mutualisé (CGPA v5.4.1) | ✅ PASS — `ai-test-server` mutualisé, isolation Docker conforme |

## Phase actuelle

CGPA v5.4.1 : **Phase 7 — Développement** pour les lots post-go-live. Production `1.2.1` LIVE depuis le 2026-06-27 (`sha-47172297`, release clôturée). Le Sprint 4 UI Patrimoine est intégré à `main` via la PR #82 mais non promu ; il suit le workflow Sprint -> Staging avec `STG-ISOL-01`, puis un Gate Production distinct.

## Stack

Spring Boot (Java 21) · Angular 20 · Keycloak (OIDC/PKCE) · PostgreSQL · Nginx (reverse proxy) · Docker · CI/CD.

## Démarrage rapide (dev)

> Stack de développement complète ; les parcours OIDC/PKCE, Admin API Keycloak et isolation RLS sont couverts par les tests d’intégration et le smoke runtime.

```bash
cp .env.example .env      # renseigner les secrets locaux
docker compose up         # nginx (443) · api · keycloak · postgres
```

## Gestion des secrets

Aucun secret en clair n'est versionné (ENF-03). Le dépôt ne contient que des **modèles** (`.env.example`) et des références à des variables ; les valeurs réelles vivent hors dépôt.

| Environnement | Stockage des secrets |
|---------------|----------------------|
| **Dev** | Fichier `.env` local (copie de `.env.example`, gitignoré). Certificats TLS dans `infra/nginx/certs/` (gitignoré, générés via `mkcert`). |
| **CI** | **GitHub Actions Secrets** (chiffrés). Injectés dans les workflows via `${{ secrets.NOM }}`. |
| **Prod** | Variables d'environnement injectées par l'hôte, ou un coffre (Vault / Infisical — hors scope MVP). |

**Secrets attendus** (noms ; valeurs jamais committées) : `POSTGRES_PASSWORD`, `DB_PASSWORD`, `DB_BATCH_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_API_CLIENT_SECRET`, `KEYCLOAK_TEST_BAILLEUR_PASSWORD` (dev uniquement). Côté CI, `NVD_API_KEY` (optionnel, accélère OWASP Dependency-Check) ; `GITHUB_TOKEN` est fourni automatiquement.

Configurer un secret CI (GitHub CLI) :

```bash
gh secret set NVD_API_KEY            # saisie interactive (valeur non journalisée)
gh secret list                       # vérifier les secrets enregistrés
```

**Rotation** (manuelle au MVP) : régénérer la valeur (`openssl rand -base64 24`), mettre à jour `.env` local / le secret GitHub / la variable d'hôte, puis redéployer. La détection de fuite est assurée en continu par **Gitleaks** dans la CI (scan du dépôt et de tout l'historique).

## Documentation

| Sujet | Emplacement |
|-------|-------------|
| Architecture & ADR | `docs/cgpa/05-architecture-conception/` |
| Backlog & sprints | `docs/cgpa/06-planification-agile/` |
| Project State CGPA v5.3 | `docs/project-state.md` |
| Plan d'implémentation DevSecOps | `docs/cgpa/07-devsecops/plan-implementation.md` |
| Migration CGPA v5.3 | `docs/cgpa/migration/migration-report-v5.3.md` |
| Migration CGPA v5.4.1 | `docs/cgpa/migration/migration-report-v5.4.1.md` |
| Workflows Staging/Production v5.3 + isolation v5.4 | `docs/cgpa/workflows/` |
| Checklists Gate Staging/Production v5.3 + `STG-ISOL-01` v5.4 | `docs/cgpa/checklists/` |
| Contribuer | `CONTRIBUTING.md` |
