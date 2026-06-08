# LoyerTracker

Application web de **gestion locative bailleur-centrée avec délégation fine par bien** : centralisation des baux, suivi des paiements de loyer mois par mois (à terme échu), gestion des garanties locatives, honoraires de gestion délégués, et alertes sur les échéances clés (loyers en retard, fin de bail, préavis, garanties non restituées).

## État du projet

Projet gouverné par le **CGPA**. Le projet a été démarré avec le référentiel local v1.0 (`setup-cgpa/`) et resynchronisé vers **CGPA v3.0.1**. Le point d'entrée de reprise est `docs/project-state.md`. Phases de gouvernance franchies :

| Gate | Phase | Statut |
|------|-------|--------|
| Gate 0 | Idée / opportunité | ✅ Go |
| Gate 1 | Expression du besoin (EB v1.2) | ✅ Go |
| Gate 2 | Faisabilité | ✅ Go |
| Gate 3 | Cahier des charges | ✅ Go |
| Gate 4 | Architecture & conception | ✅ Go — **verrou de codage levé** |
| Gate 5 | Backlog & planification | ✅ Go |
| Gate 6 | DevSecOps | ✅ Go (16/20) — **ratifié le 2026-06-06** ; R1 clôturée, R2→R5 ouvertes avant prod, R6 partielle |

## Phase actuelle

CGPA v3.0.1 : **Phase 7 — Développement**. Dernier gate statué : **Gate 06 — Go**. Gate courant : **Gate 07**, non encore statué.

## Stack

Spring Boot (Java 21) · Angular 20 · Keycloak (OIDC/PKCE) · PostgreSQL · Nginx (reverse proxy) · Docker · CI/CD.

## Démarrage rapide (dev)

> Stack de développement largement opérationnelle pour le socle S01. Certaines validations runtime restent ouvertes, notamment R6 (OIDC/PKCE complet et Admin API Keycloak gestionnaire).

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
| Project State CGPA v3.0.1 | `docs/project-state.md` |
| Plan d'implémentation DevSecOps | `docs/cgpa/07-devsecops/plan-implementation.md` |
| Contribuer | `CONTRIBUTING.md` |
