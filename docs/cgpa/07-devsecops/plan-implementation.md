# Plan d'implémentation — Phase 07 DevSecOps · LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 07 — DevSecOps |
| Gate visé | Gate 6 |
| Statut | En revue |
| Prérequis | Backlog validé (Gate 5 Go) — autorisation de coder active (Gate 4 Go) |

> **Principe directeur :** avant d'écrire la première ligne de code métier, chaque couche de l'usine logicielle est planifiée ici dans le détail — fichiers à créer, commandes clés, critère de validation. Ce document est la référence de la Phase 07. Chaque étape doit être complète et validée avant de passer à la suivante (même logique que les gates CGPA, mais à granularité technique).

---

## Vue d'ensemble des étapes

```
Étape 01 — Structure du dépôt & conventions
    │
    ▼
Étape 02 — Docker Compose & Nginx (stack complète)
    │
    ▼
Étape 03 — Keycloak realm (config versionnée)
    │
    ▼
Étape 04 — Spring Boot skeleton (projet, sécurité, Actuator)
    │
    ▼
Étape 05 — Angular skeleton (projet, OIDC, routing de base)
    │
    ▼
Étape 06 — Flyway — migration V1 (schéma complet, index, RLS)
    │
    ▼
Étape 07 — Pipeline CI/CD (GitHub Actions)
    │
    ▼
Étape 08 — Gestion des secrets & .env
    │
    ▼
Étape 09 — Observabilité de base (logs structurés, Actuator, Nginx access log)
    │
    ▼
Étape 10 — Validation Gate 6 (checklists devops + sécurité)
```

---

## Étape 01 — Structure du dépôt & conventions

### Objectif
Définir la layout du dépôt, les conventions de commits et la stratégie de branches avant tout code.

### Structure cible du dépôt

```
loyertracker/
├── backend/                        # Spring Boot (Maven)
│   ├── src/
│   │   ├── main/java/com/loyertracker/
│   │   │   ├── comptes/            # module comptes/auth/invitation
│   │   │   ├── biens/              # module biens
│   │   │   ├── baux/               # module baux
│   │   │   ├── affectations/       # module affectations
│   │   │   ├── paiements/          # module paiements
│   │   │   ├── garanties/          # module garanties
│   │   │   ├── honoraires/         # module honoraires
│   │   │   ├── alertes/            # module alertes
│   │   │   ├── audit/              # module audit log
│   │   │   ├── batch/              # job batch quotidien
│   │   │   └── securite/           # AuthorizationService (transverse)
│   │   └── main/resources/
│   │       ├── db/migration/       # scripts Flyway (V1__init.sql, ...)
│   │       └── application.yml
│   └── pom.xml
├── frontend/                       # Angular (standalone)
│   ├── src/app/
│   │   ├── core/                   # guards, interceptors, auth service
│   │   ├── shared/                 # composants partagés
│   │   ├── bailleur/               # feature bailleur
│   │   └── gestionnaire/           # feature gestionnaire
│   └── package.json
├── infra/
│   ├── nginx/
│   │   ├── nginx.conf              # config principale
│   │   └── certs/                  # certificats (gitignorés sauf .example)
│   └── keycloak/
│       └── realm-loyertracker.json # import realm versionné
├── docker-compose.yml              # stack complète dev
├── docker-compose.prod.yml         # overrides prod (ports, volumes nommés)
├── .env.example                    # variables requises (pas de secrets réels)
├── .gitignore
└── docs/                           # livrables CGPA (déjà présents)
```

### Conventions de commits
Format **Conventional Commits** (déjà en place dans ce dépôt) :
```
feat(module): description courte
fix(module): ...
docs(cgpa): ...
test(module): ...
infra(docker|nginx|keycloak): ...
ci: ...
```

### Stratégie de branches
| Branche | Usage |
|---------|-------|
| `main` | Branche stable — code livrable, protégée |
| `develop` | Intégration continue des features |
| `feat/<us-id>-<slug>` | Une branche par user story (ex. `feat/us-01-docker-compose`) |
| `fix/<slug>` | Corrections |

Règle : **merge via PR** sur `develop` ; `develop → main` sur validation de sprint.

### Critère de validation ✅ (étape 01 terminée — commit `4dffc65`)
- [x] Répertoires `backend/`, `frontend/`, `infra/` créés avec fichiers `.gitkeep`
- [x] `.gitignore` couvrant : `.env`, `*.class`, `target/`, `node_modules/`, `dist/`, `certs/` (sauf `.gitkeep`) — vérifié via `git check-ignore`
- [x] `CONTRIBUTING.md` minimal (conventions de commits, stratégie de branches, DoD)
- [x] `README.md` projet mis à jour (état des gates, stack, démarrage)

---

## Étape 02 — Docker Compose & Nginx

### Objectif
Stack complète démarrant en une commande (`docker compose up`) — Nginx point d'entrée unique, Keycloak, API, PostgreSQL. Correspond à **US-01**.

### Fichiers à créer

**`docker-compose.yml`** — services et dépendances :
```yaml
# Structure (pas le fichier final — voir implémentation Phase 08)
services:
  nginx:      # reverse proxy, seul port publié : 443 (et 80→redirect)
  api:        # Spring Boot, port interne 8080
  keycloak:   # Keycloak 24+, port interne 8080
  postgres:   # PostgreSQL 16, port interne 5432
  
networks:
  loyertracker-net:   # réseau interne — api, keycloak, postgres non exposés

volumes:
  postgres-data:
  keycloak-data:
```

**Règles Docker Compose :**
- `nginx` dépend de `api` et `keycloak` (healthcheck)
- `api` dépend de `postgres` (healthcheck `pg_isready`)
- `keycloak` dépend de `postgres`
- Toutes les variables sensibles via `env_file: .env` (jamais en dur)
- Images : `nginx:1.27-alpine`, `eclipse-temurin:21-jre-alpine`, `quay.io/keycloak/keycloak:24`, `postgres:16-alpine`
- Conteneurs non-root (`user: "1000:1000"` ou `USER` dans Dockerfile)

**`infra/nginx/nginx.conf`** — routage :
```nginx
# Blocs upstream (api:8080, keycloak:8080)
# server 443 :
#   / → serve /usr/share/nginx/html (SPA Angular build)
#   /auth → proxy_pass http://keycloak:8080
#   /api  → proxy_pass http://api:8080
# server 80 → redirect 301 https
# En-têtes sécurité : HSTS, CSP, X-Frame-Options, X-Content-Type-Options
# Gzip pour assets SPA
```

**`docker-compose.prod.yml`** — overrides production :
- Volumes nommés persistants
- `restart: unless-stopped` sur tous les services
- Pas de mount de code source

### Critère de validation (US-01) — étape 02 (commit à suivre)
- [x] `docker compose config` valide (syntaxe + interpolation des variables) ; 4 services déclarés
- [x] `nginx -t` : configuration valide (testée en conteneur)
- [x] **postgres** démarre et atteint l'état `healthy` (healthcheck `pg_isready` réel)
- [x] **Nginx sert le placeholder SPA en HTTPS** (`curl -k https://…/` → page LoyerTracker) avec en-têtes de sécurité (HSTS, nosniff, X-Frame-Options DENY, CSP, Referrer-Policy)
- [x] **Redirection HTTP → HTTPS** (301) vérifiée
- [x] *Bring-up complet 4 services `healthy`* — validé à l'**étape 04** : `postgres`/`keycloak`/`api` healthy, `keycloak-init` exit 0, `nginx` up
- [x] `https://localhost/api/actuator/health` → `{"status":"UP"}` — validé à l'**étape 04**
- [x] `https://localhost/auth` → Keycloak via Nginx (HTTP 200 sur le realm) — validé à l'**étape 04**
- [x] Conception : Keycloak/PostgreSQL **non publiés** sur l'hôte (réseau interne `loyertracker-net`, seuls 80/443 exposés par Nginx)

> 🔧 **Corrections appliquées à l'étape 03** (Keycloak réellement démarré pour la 1ʳᵉ fois) :
> - Healthcheck Keycloak : sonde `:9000` → `:8080/auth/health/ready` (KC 24 sert la santé sur le port HTTP principal ; le port de management 9000 n'est par défaut qu'à partir de KC 25).
> - `KC_HOSTNAME` : `https://localhost/auth` (URL, invalide en hostname v1 → issuer corrompu) → `localhost` + `KC_HOSTNAME_STRICT_HTTPS=true`, donnant l'issuer correct `https://localhost/auth/realms/loyertracker`.

---

## Étape 03 — Keycloak realm (configuration versionnée)

### Objectif
Realm LoyerTracker exportable/importable au démarrage, versionné dans le dépôt. Correspond à **US-02**.

### Fichier : `infra/keycloak/realm-loyertracker.json`

Éléments à configurer dans le realm :

| Élément | Valeur |
|---------|--------|
| Realm name | `loyertracker` |
| Rôles realm | `BAILLEUR`, `GESTIONNAIRE` |
| Client SPA | `loyertracker-spa` — public, PKCE (`S256`), redirectURIs `https://localhost/*`, webOrigins `https://localhost`, mapper d'audience → `loyertracker-api` |
| Client backend | `loyertracker-api` — `bearer-only` (resource-server). Valide les JWT via JWKS : **aucun secret requis**. Un service-account confidentiel (avec secret `.env`) sera ajouté quand l'inscription bailleur appellera l'Admin API. |
| Token settings | Access token TTL : 5 min (300 s) ; refresh/SSO idle : 30 min (1800 s) ; rotation activée (`revokeRefreshToken`, `refreshTokenMaxReuse=0`) |
| Brute force | Activé (`failureFactor=5`) |
| Self-registration | **Désactivée** (`registrationAllowed=false`) — inscription via endpoint `/api/bailleurs/inscription` + Admin API uniquement |

**Import automatique au démarrage Keycloak (seul le fichier realm est monté) :**
```yaml
# docker-compose.yml — service keycloak
command: ["start-dev", "--import-realm", "--http-relative-path=/auth"]
volumes:
  - ./infra/keycloak/realm-loyertracker.json:/opt/keycloak/data/import/realm-loyertracker.json:ro
```

> ⚠️ Keycloak 24 ne substitue **pas** les `${env.*}` dans le realm importé : aucun secret/mot
> de passe n'est donc placé dans `realm-loyertracker.json` (versionné « propre »).

**Compte de test (dev uniquement) :** `bailleur-test@test.local`, créé **sans** mot de passe dans le
realm. Le service one-shot `keycloak-init` (script `infra/keycloak/bootstrap-test-account.sh`,
idempotent) positionne le mot de passe depuis `KEYCLOAK_TEST_BAILLEUR_PASSWORD` (`.env`) après import,
via `kcadm.sh` — démarrage entièrement automatique, zéro secret versionné.

### Critère de validation (US-02) — étape 03 (commit à suivre)
- [x] Realm `loyertracker` présent après `docker compose up` (import auto, sans action manuelle) — log `Realm 'loyertracker' imported`
- [x] Client SPA en PKCE `S256` (vérifié via Admin API + discovery) ; client API en `bearer-only`
- [x] Rôles `BAILLEUR` / `GESTIONNAIRE` créés (Admin API)
- [x] Compte de test bailleur obtient un JWT via **flow PKCE S256 complet** (login + échange code) — JWT vérifié : `iss=https://localhost/auth/realms/loyertracker`, `aud=loyertracker-api`, `realm_access.roles=[BAILLEUR]`, `expires_in=300s` ; ROPC rejeté (directAccessGrants off)
- [ ] Backend Spring rejette une requête sans JWT valide → 401 — *dépend du backend, validé à l'**étape 04***

---

## Étape 04 — Spring Boot skeleton

### Objectif
Projet Maven compilable, Spring Security configuré (JWT validation, extraction rôle), Actuator exposé, structure de packages en place. Base pour toutes les user stories métier.

### Dépendances `pom.xml`

| Dépendance | Usage |
|-----------|-------|
| `spring-boot-starter-web` | API REST |
| `spring-boot-starter-security` | Sécurité |
| `spring-boot-starter-oauth2-resource-server` | Validation JWT Keycloak |
| `spring-boot-starter-data-jpa` | Persistance |
| `spring-boot-starter-actuator` | Health/metrics |
| `spring-boot-starter-validation` | Validation des entrées |
| `spring-boot-starter-batch` | Job batch alertes/échéances |
| `flyway-core` + `flyway-database-postgresql` | Migrations |
| `postgresql` (driver) | JDBC |
| `lombok` | Réduction boilerplate |
| `mapstruct` | DTO mapping |
| `springdoc-openapi-starter-webmvc-ui` | OpenAPI/Swagger |
| `spring-boot-starter-test` + `testcontainers-postgresql` | Tests |

### Configuration `application.yml` (variables externalisées)

```yaml
# Structure — valeurs réelles via .env / variables d'env
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa.hibernate.ddl-auto: validate   # Flyway gère le schéma
  security.oauth2.resourceserver.jwt:
    issuer-uri: ${KEYCLOAK_ISSUER_URI}  # https://localhost/auth/realms/loyertracker

server:
  port: 8080

management.endpoints.web.exposure.include: health,info
```

### Structure `SecurityConfig`

```java
// Pseudo-code — plan de la configuration Spring Security
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // active @PreAuthorize
class SecurityConfig {
    // - Stateless (JWT, pas de session)
    // - Extraction rôle depuis claim "realm_access.roles"
    // - Whitelist : POST /api/invitations/{token}/acceptation (non authentifié)
    // - Tout le reste : authentifié
    // - CORS : origine https://localhost uniquement
    // - CSRF désactivé (stateless JWT)
}
```

### `AuthorizationService` (ADR-02) — plan

```java
// Responsabilités :
// - estBailleurProprietaire(UUID bienId, UUID bailleurId) → boolean
// - estGestionnaireAffecteActif(UUID bienId, UUID gestionnaireId) → boolean
// - peutAccederBien(UUID bienId, Authentication auth) → boolean
// Utilisé via @PreAuthorize("@authz.peutAccederBien(#bienId, authentication)")
```

### Critère de validation (US-02 côté backend) — étape 04 (commit à suivre)
- [x] `mvn clean package` sans erreur — 8 tests verts (5 sécurité MockMvc + 3 unitaires converter)
- [x] `GET /api/actuator/health` → 200 sans auth (`{"status":"UP"}`, vérifié via Nginx en HTTPS)
- [x] `GET /api/biens` sans JWT → 401 ; **JWT à signature invalide → 401** (validation réelle, pas seulement présence)
- [x] `GET /api/biens` avec JWT `BAILLEUR` valide (obtenu par flow PKCE complet via Nginx) → 200 `[]`
- [x] `GET /api/biens` avec JWT `GESTIONNAIRE` → 200 `[]` (test d'intégration MockMvc) ; JWT sans rôle métier → 403

> **Décisions / écarts de plan documentés (traçabilité CGPA) :**
> - **Dépendances introduites au plus juste.** L'étape 04 reste un squelette *bootable sans base* :
>   `data-jpa` / `flyway` / `postgresql` sont **reportés à l'étape 06** (là où le schéma est créé),
>   `batch` au pas batch, `mapstruct` / `springdoc` à leur premier usage. Évite un couplage BDD
>   inutile au démarrage et garde les tests hors-ligne.
> - **Validation JWT compatible reverse proxy.** `issuer-uri` (public `https://localhost/auth/...`)
>   pour valider le claim `iss`, mais `jwk-set-uri` interne (`http://keycloak:8080/auth/...`) pour
>   récupérer les clés — sinon le conteneur `api` tenterait de joindre `https://localhost` (lui-même).
> - **Préfixe `/api`** porté par les `@RequestMapping` + `management.base-path=/api/actuator`
>   (plutôt que `context-path`), pour un routage Nginx direct et des tests MockMvc sans contorsion.
> - **Dockerfile backend** créé dès maintenant (nécessaire au build Compose) ; son durcissement et
>   le scan Trivy restent du ressort de l'étape 07.

---

## Étape 05 — Angular skeleton

### Objectif
Projet Angular compilable, OIDC (PKCE) configuré, guard de route auth en place, appel API protégé fonctionnel. Correspond à la partie frontend de **US-10**.

### Dépendances `package.json`

| Package | Usage |
|---------|-------|
| `keycloak-angular` + `keycloak-js` | Intégration OIDC Keycloak |
| `@angular/material` | UI components |
| `@ngrx/store` (optionnel) | State management (simple service d'abord) |

### Structure `src/app/`

```
core/
  auth/
    auth.guard.ts          # CanActivate — redirige vers Keycloak si non authentifié
    auth.interceptor.ts    # Ajoute Bearer token à chaque requête API
    auth.service.ts        # Wraps KeycloakService (rôle, profil, logout)
  api/
    api.config.ts          # Base URL /api
shared/
  components/              # navbar, footer, spinner
bailleur/
  dashboard/               # page tableau de bord bailleur (placeholder)
gestionnaire/
  dashboard/               # page tableau de bord gestionnaire (placeholder)
app.routes.ts              # routes protégées par AuthGuard
```

### Configuration Keycloak Angular

```typescript
// app.config.ts — plan
// KeycloakAngularModule initialisé avec :
// {  url: '/auth',  realm: 'loyertracker',  clientId: 'loyertracker-spa'  }
// bearerExcludedUrls: ['/auth']   // ne pas envoyer le token aux endpoints Keycloak
```

### Critère de validation (US-10 côté frontend) — étape 05 (commit à suivre)
*Validé end-to-end avec Chrome headless (Puppeteer) contre la stack complète servant le build réel.*
- [x] `ng build` sans erreur ; `dist/loyertracker/browser` servi par Nginx (index + bundles 200, fallback SPA sur `/bailleur` 200)
- [x] Accès à `https://localhost/` redirige vers le login Keycloak (`onLoad: 'login-required'`, PKCE S256)
- [x] Après login `bailleur-test` → dashboard bailleur ; bandeau « bailleur-test@test.local · rôles : BAILLEUR »
- [x] `authInterceptor` pose `Authorization: Bearer <token>` sur `/api/biens` → **200 OK** (header capturé côté navigateur)
- [x] Logout → retour à la page de login Keycloak

> **Décisions / écarts de plan documentés (traçabilité CGPA) :**
> - **`@angular/material` et NgRx reportés** : non nécessaires au squelette US-10 (auth + routing).
>   Material sera introduit avec les premiers écrans métier ; un simple service d'état d'abord.
> - **CSP × build prod** : l'inlining CSS critique (critters) injectait un handler inline
>   `onload="..."` bloqué par notre CSP `default-src 'self'`. Corrigé en désactivant
>   `optimization.styles.inlineCritical` — la CSP stricte reste inchangée (pas d'assouplissement).
> - **`getUsername()` via le token** : lu depuis `tokenParsed.preferred_username` plutôt que
>   `KeycloakService.getUsername()` (qui exige un `loadUserProfile()` préalable).
> - **Service de la SPA en dev** : `docker compose up` sert toujours le placeholder ; le build
>   Angular est servi en montant `dist/loyertracker/browser` dans Nginx. L'intégration du build
>   dans l'image (Dockerfile frontend) est finalisée à l'**étape 07**.

---

## Étape 06 — Flyway — Migration V1 (schéma complet)

### Objectif
Script `V1__init_schema.sql` créant **toutes** les tables, colonnes, clés étrangères, index (dont uniques partiels), politiques RLS et séquences. Correspond à **US-03**.

### Tables à créer

| Table | Colonnes clés | Contraintes |
|-------|--------------|-------------|
| `bailleur` | `id`, `keycloak_id`, `email`, `nom`, `prenom`, `date_creation` | PK uuid |
| `gestionnaire` | `id`, `keycloak_id`, `email`, `nom`, `prenom` | PK uuid |
| `invitation` | `id`, `bailleur_id`, `email`, `token`, `statut` (PENDING/ACCEPTED/EXPIRED), `date_expiration` | UK `token` |
| `bien` | `id`, `bailleur_id`, `adresse`, `type`, `statut` (LIBRE/LOUE/EN_TRAVAUX) | FK bailleur |
| `bail` | `id`, `bailleur_id`, `bien_id`, `locataire_nom`, `locataire_email`, `loyer_cc`, `depot_garantie`, `date_debut`, `date_fin`, `statut` (ACTIF/CLOS) | FK bien |
| `affectation` | `id`, `bailleur_id`, `bien_id`, `gestionnaire_id`, `type_honoraires` (POURCENTAGE/FORFAIT), `montant_honoraires`, `date_debut`, `date_fin`, `statut` (ACTIVE/REVOQUEE/EXPIREE), `date_revocation` | FK bien, gestionnaire |
| `paiement` | `id`, `bailleur_id`, `bail_id`, `bien_id`, `periode` (YYYY-MM), `montant_attendu`, `montant_recu`, `date_exigibilite`, `statut` (RECU/PARTIEL/EN_RETARD/IMPAYE) | FK bail |
| `garantie` | `id`, `bailleur_id`, `bail_id`, `montant`, `type_garantie`, `date_depot`, `statut` (DETENU/RESTITUE_PARTIEL/RESTITUE_TOTAL), `montant_retenu`, `motif_retenue` | FK bail |
| `honoraire` | `id`, `bailleur_id`, `affectation_id`, `periode` (YYYY-MM), `montant`, `statut` (DU/EN_ATTENTE/PAYE) | FK affectation |
| `alerte` | `id`, `bailleur_id`, `destinataire_id`, `type` (LOYER_EN_RETARD/FIN_BAIL/PREAVIS/GARANTIE_NON_RESTITUEE), `bien_id`, `bail_id`, `periode`, `message`, `statut` (NON_LUE/LUE), `date_creation`, `date_lecture` | FK bien |
| `audit_log` | `id`, `bailleur_id`, `acteur_id`, `acteur_role`, `action`, `entity_type`, `entity_id`, `timestamp`, `details` (JSONB) | — |

### Index uniques partiels (ADR-07 — garantie BDD)

```sql
-- EF-12 : un seul bail actif par bien
CREATE UNIQUE INDEX uq_bail_actif ON bail (bien_id) WHERE statut = 'ACTIF';

-- EF-21 : un seul gestionnaire actif par bien
CREATE UNIQUE INDEX uq_affectation_active ON affectation (bien_id) WHERE statut = 'ACTIVE';

-- EF-30/33 : un loyer attendu par bien/période
CREATE UNIQUE INDEX uq_paiement_periode ON paiement (bien_id, periode);

-- EF-51 : un honoraire par affectation/période
CREATE UNIQUE INDEX uq_honoraire_periode ON honoraire (affectation_id, periode);

-- EF-65 : anti-doublon alertes NON_LUE
CREATE UNIQUE INDEX uq_alerte_nonlue ON alerte (type, bien_id, periode) WHERE statut = 'NON_LUE';
```

### Index de performance (ENF-06)

```sql
CREATE INDEX idx_bien_bailleur ON bien (bailleur_id);
CREATE INDEX idx_bail_bailleur ON bail (bailleur_id);
CREATE INDEX idx_bail_bien ON bail (bien_id);
CREATE INDEX idx_paiement_bailleur ON paiement (bailleur_id);
CREATE INDEX idx_paiement_bail ON paiement (bail_id);
CREATE INDEX idx_affectation_gestionnaire_statut ON affectation (gestionnaire_id, statut);
CREATE INDEX idx_alerte_destinataire_statut ON alerte (destinataire_id, statut);
CREATE INDEX idx_audit_bailleur ON audit_log (bailleur_id);
```

### Row-Level Security (ADR-01 — défense en profondeur)

```sql
-- Exemple sur la table bien (pattern répété sur toutes les tables métier)
ALTER TABLE bien ENABLE ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON bien
  USING (bailleur_id = current_setting('app.current_bailleur_id')::uuid);

-- Rôle technique batch (contourne la RLS pour le job multi-bailleur)
CREATE ROLE loyertracker_batch BYPASSRLS;
```

> L'intercepteur Spring injecte `SET app.current_bailleur_id = '<uuid>'` à chaque connexion issue d'une requête utilisateur. Le batch utilise un `DataSource` dédié avec le rôle `loyertracker_batch`.

### Critère de validation (US-03)
- [x] `docker compose up` → Flyway log : `Successfully applied 1 migration to schema "public", now at version v1`
- [x] Toutes les tables existent : 11 tables métier + `flyway_schema_history` présentes (`information_schema.tables`)
- [x] Les 5 index uniques métier sont créés (`uq_bail_actif`, `uq_affectation_active`, `uq_paiement_periode`, `uq_honoraire_periode`, `uq_alerte_nonlue`) + 8 index de performance
- [x] RLS active **et forcée** sur les tables métier : `SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'bien'` → `t | t` (10 tables au total)
- [x] 2ᵉ démarrage : Flyway log : `Schema "public" is up to date. No migration necessary.`
- [x] Test automatisé `SchemaMigrationTest` (Testcontainers PostgreSQL 16) : migration, présence tables/index, RLS ENABLE+FORCE, rôle batch BYPASSRLS, idempotence, **et preuve fonctionnelle du cloisonnement** (rôle restreint → ne voit que les biens du bailleur courant ; GUC absent → 0 ligne). Suite verte : 14 tests.

#### Écarts documentés (traçabilité CGPA)
- **`FORCE ROW LEVEL SECURITY` ajouté** (au-delà du `ENABLE` du plan) : l'API se connecte en tant que **propriétaire** des tables, or un propriétaire contourne la RLS par défaut. Sans `FORCE`, les politiques ne s'appliqueraient pas à l'API — la RLS serait inopérante. Correction de sécurité indispensable (ADR-01).
- **Fail-closed durci** : la politique utilise `NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid`. Le `true` (missing_ok) évite l'erreur si le GUC est absent ; le `NULLIF(..., '')` couvre le cas d'une connexion **poolée réinitialisée** (un GUC placeholder rendu renvoie `''`, pas `NULL`) → comparaison à `NULL` ⇒ 0 ligne, sans lever d'erreur SQL.
- **Rôle `loyertracker_batch` créé `NOLOGIN` sans mot de passe** (créé idempotemment via bloc `DO`) : aucun secret versionné dans la migration. L'activation `LOGIN` + mot de passe sera faite au **pas batch** via une migration ultérieure (`DB_BATCH_USER/PASSWORD` déjà réservés dans `.env.example`). `BYPASSRLS` est positionné dès maintenant ; des `GRANT SELECT/INSERT/UPDATE/DELETE` lui sont accordés.
- **2 des 5 index « uniques »** (`uq_paiement_periode`, `uq_honoraire_periode`) sont des **uniques totaux** (sans clause `WHERE`) conformément à leur SQL dans le plan — 3 seulement sont réellement partiels. Tous validés par nom.
- **Colonne `audit_log.timestamp` renommée `horodatage`** (`timestamp` est un mot réservé SQL).
- **Environnement de test** : sur cet hôte, le daemon Docker exige une API ≥ 1.40 alors que le client docker-java embarqué par Testcontainers 1.19.8 négocie 1.32 par défaut ; contourné **hors-repo** par `-Dapi.version=1.43` + `DOCKER_HOST` à l'exécution. Aucun artefact spécifique à l'hôte n'est versionné (le CI standard de l'étape 07 négociera la version nominalement).

---

## Étape 07 — Pipeline CI/CD (GitHub Actions)

### Objectif
Pipeline automatisé sur chaque push/PR : build, tests, scans de sécurité, packaging Docker. Correspond à **US-04**.

### Fichier : `.github/workflows/ci.yml`

```yaml
# Plan des jobs (pas le fichier final)
name: CI

on: [push, pull_request]

jobs:
  backend:
    # 1. Checkout
    # 2. Setup Java 21
    # 3. Cache Maven
    # 4. mvn verify (compile + tests + Flyway check via Testcontainers PostgreSQL)
    # 5. Upload rapport de couverture (JaCoCo)
    # Quality gate : couverture règles critiques (cloisonnement, transitions statut) ≥ 80%

  frontend:
    # 1. Checkout
    # 2. Setup Node 20
    # 3. npm ci
    # 4. ng build --configuration production
    # 5. ng test --watch=false --browsers=ChromeHeadless

  security:
    # 1. Gitleaks (scan secrets dans le dépôt et l'historique git)
    # 2. OWASP Dependency-Check (SCA — dépendances Java + npm)
    # 3. Trivy (scan image Docker api après build)
    # 4. Résultats publiés en artefacts CI

  docker-build:
    needs: [backend, frontend, security]
    # 1. Build image api (multi-stage : build Maven → JRE Alpine)
    # 2. Build image nginx (avec assets Angular dans dist/)
    # 3. Tag : sha-${GITHUB_SHA::8}
    # (push vers registry : hors scope MVP — images buildées localement)
```

### Dockerfile backend (multi-stage)

```dockerfile
# Étape 1 : build Maven (image avec JDK + Maven)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline    # cache des dépendances
COPY src ./src
RUN mvn package -DskipTests

# Étape 2 : image de run (JRE Alpine minimal)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app   # non-root
USER app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dockerfile frontend (multi-stage via Nginx)

```dockerfile
# Étape 1 : build Angular
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Étape 2 : Nginx sert les assets + config
FROM nginx:1.27-alpine
COPY --from=build /app/dist/loyertracker/browser /usr/share/nginx/html
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf
```

### Critère de validation (US-04)
- [x] Workflow `.github/workflows/ci.yml` complet : 4 jobs `backend` / `frontend` / `security` / `docker-build` (YAML valide, sans injection — n'utilise que `GITHUB_SHA`).
- [x] **Backend** validé localement : `mvn verify` vert (14 tests, dont migration Flyway via Testcontainers) + **gate JaCoCo** (paquet `securite` ≥ 80 % → **96 %** mesuré) + rapport JaCoCo publié en artefact.
- [x] **Frontend** validé localement : `npm ci` + `ng build --configuration production` OK + `ng test` headless **3 tests verts** (launcher `ChromeHeadlessNoSandbox`).
- [x] **Gitleaks** exécuté localement (binaire v8.21.2) sur le dépôt **et les 24 commits d'historique** : **0 secret détecté**.
- [x] **Trivy** exécuté localement (v0.71.0) sur l'image API : après remédiation (cf. écarts), **0 vulnérabilité CRITICAL/HIGH corrigeable** (`--ignore-unfixed`, gate `exit-code 1`).
- [x] **Images Docker** : `loyertracker-api` (392 MB) et `loyertracker-web` (74 MB, SPA + Nginx ADR-08, contexte racine) buildent.
- [ ] **Pipeline CI vert sur push** : **en attente** — l'authentification de push GitHub n'est pas configurée (le remote `origin` existe mais les commits restent locaux). Le workflow et toutes ses étapes sont validés hors-ligne ci-dessus ; la confirmation « vert sur push » sera cochée à la mise en place de l'auth (cf. étape 08).

#### Écarts & décisions documentés (traçabilité CGPA)
- **Remédiation de sécurité — Spring Boot 3.3.5 → 3.5.14 (décision validée par le PO).** Le scan SCA/Trivy du pipeline a révélé **5 CRITICAL + 20 HIGH** dans les dépendances gérées par Boot 3.3.5 (Tomcat embarqué 10.1.31, spring-security-web 6.3.4 → CVE-2026-22732 *contournement de politique*, spring-core, pgjdbc). Le correctif de la CVE `spring-security-web` n'existant qu'en 6.5.9+, la montée à **Spring Boot 3.5.14** était requise. Deux versions restant en deçà du seuil après bump, surcharge explicite des propriétés du BOM : **`tomcat.version=10.1.55`** (CVE → 10.1.55) et **`postgresql.version=42.7.11`** (CVE-2026-42198 → 42.7.11). Résultat : **0 CRITICAL/HIGH corrigeable**. La baseline de stack n'était figée que dans le `pom.xml` (aucun ADR/doc ne pinnait le patch `3.3.5` ; ADR-06 « Monolithe modulaire Spring Boot » reste valide) → pas de nouvel ADR, consignation ici. Les 14 tests passent inchangés sous Boot 3.5.14 (Spring Security 6.5 sans impact sur `SecurityConfig`).
- **Quality gate JaCoCo ciblé.** La règle `check` s'applique au paquet `com.loyertracker.securite` (cloisonnement, ADR-01) à **≥ 80 %** de lignes — élargie aux règles métier (transitions de statut) à leur arrivée dans les phases applicatives. Rapport HTML/XML/CSV généré en phase `verify`, publié en artefact CI.
- **Dockerfile frontend — contexte racine.** Construit via `docker build -f frontend/Dockerfile .` (et non depuis `frontend/`) afin d'embarquer à la fois la SPA **et** `infra/nginx/nginx.conf` (image web = point d'entrée unique ADR-08). Un `.dockerignore` racine limite le contexte transféré. Les certificats TLS ne sont **pas** embarqués (montés au runtime).
- **Tests frontend ajoutés** (aucune infra de test n'existait) : karma + jasmine, cible `test` dans `angular.json`, `tsconfig.spec.json`, `karma.conf.js` (launcher NoSandbox pour CI) et un spec réel `auth.service.spec.ts` couvrant le parsing du token (bug corrigé à l'étape 05).
- **Déclencheurs CI** élargis à toute branche (`push: ["**"]` + `pull_request`) plutôt que `develop` seul, pour couvrir le flux de dev actuel sur `main`. OWASP Dependency-Check : clé `NVD_API_KEY` (optionnelle, accélère le téléchargement de la base) prévue en secret à l'**étape 08**.
- **Outillage non exécutable en local** (gitleaks/trivy installés ad hoc et exécutés ; OWASP DC non lancé localement car NVD volumineux) : la configuration des actions correspondantes est validée syntaxiquement et suit l'usage documenté de chaque action.

---

## Étape 08 — Gestion des secrets

### Objectif
Aucun secret en clair dans le dépôt. Variables documentées, process de gestion défini. (ENF-03, ADR-01)

### Fichier : `.env.example`

```dotenv
# Base de données
DB_URL=jdbc:postgresql://postgres:5432/loyertracker
DB_USER=loyertracker
DB_PASSWORD=CHANGE_ME

# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=CHANGE_ME
KEYCLOAK_ISSUER_URI=https://localhost/auth/realms/loyertracker
KEYCLOAK_API_CLIENT_SECRET=CHANGE_ME

# PostgreSQL
POSTGRES_DB=loyertracker
POSTGRES_USER=loyertracker
POSTGRES_PASSWORD=CHANGE_ME

# Batch (rôle technique RLS BYPASS)
DB_BATCH_USER=loyertracker_batch
DB_BATCH_PASSWORD=CHANGE_ME

# TLS (dev : auto-signé via mkcert)
NGINX_CERT_PATH=./infra/nginx/certs/localhost.pem
NGINX_KEY_PATH=./infra/nginx/certs/localhost-key.pem
```

### `.gitignore` (entrées critiques)
```
.env
infra/nginx/certs/*.pem
infra/nginx/certs/*.key
*.jks
*.p12
```

### Règles de gestion des secrets
- **Dev :** `.env` local (copie de `.env.example`, non versionné)
- **CI :** GitHub Actions Secrets (`DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, etc.)
- **Prod :** variables d'environnement injectées par l'hôte ou un vault (Vault/Infisical — hors scope MVP)
- **Rotation :** documentée dans le README (pas automatisée au MVP)

### Critère de validation
- [ ] `git log --all -- '**/.env'` → 0 résultat
- [ ] Gitleaks CI : 0 secret détecté sur tout l'historique
- [ ] `.env.example` présent et documenté
- [ ] Tout secret réel absent du dépôt (`grep -r "password" --include="*.yml" --include="*.properties"` → 0 valeur en dur)

---

## Étape 09 — Observabilité de base

### Objectif
Logs structurés, health check exposé, métriques Actuator, accès log Nginx. Base pour détecter les problèmes en dev/staging.

### Backend — logs structurés

```yaml
# application.yml
logging:
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  level:
    com.loyertracker: DEBUG          # dev
    org.springframework.security: INFO
```

**Règles de journalisation (ENF-03 — pas de PII dans les logs) :**
- ✅ Logger : id entité, action, bailleurId, timestamp
- ❌ Ne jamais logger : mots de passe, tokens, noms/emails de locataires

**Actuator exposé (health + info) :**
```yaml
management:
  endpoints.web.exposure.include: health,info
  endpoint.health.show-details: when-authorized   # détails réservés aux admins
```

### Nginx — access log

```nginx
# nginx.conf — format JSON structuré pour parsing futur
log_format json_combined escape=json
  '{"time":"$time_iso8601","remote_addr":"$remote_addr",'
  '"method":"$request_method","uri":"$uri",'
  '"status":$status,"body_bytes":$body_bytes_sent,'
  '"upstream":"$upstream_addr"}';
access_log /var/log/nginx/access.log json_combined;
```

### Critère de validation
- [ ] `GET /api/actuator/health` → `{"status":"UP"}` en < 200 ms
- [ ] Logs Spring visibles dans `docker compose logs api` (format structuré)
- [ ] Logs Nginx visibles dans `docker compose logs nginx` (JSON)
- [ ] Aucune PII (email, nom) dans les logs applicatifs

---

## Étape 10 — Validation Gate 6 (checklists)

### Objectif
Renseigner les deux checklists CGPA et recommander la décision Gate 6 (DevSecOps Go).

**Livrables :**
- `docs/cgpa/07-devsecops/checklist-devops.md` (renseignée)
- `docs/cgpa/07-devsecops/checklist-securite.md` (renseignée)

Les checklists seront complétées à l'issue des étapes 01→09, chaque item coché lorsque son critère est vérifié.

**Seuil Gate 6 :** score ≥ 14/20 recommandé.

---

## Récapitulatif des étapes

| Étape | Livrable principal | Stories | Critère clé |
|-------|-------------------|---------|-------------|
| 01 | Structure dépôt + conventions | — | Layout + `.gitignore` + branches |
| 02 | `docker-compose.yml` + `nginx.conf` | US-01 | `docker compose up` → stack healthy |
| 03 | `realm-loyertracker.json` | US-02 | Realm importé automatiquement |
| 04 | Spring Boot skeleton + `SecurityConfig` | US-02/10 | Build + 401/403 fonctionnels |
| 05 | Angular skeleton + OIDC | US-10 | Login OIDC end-to-end |
| 06 | `V1__init_schema.sql` (Flyway) | US-03 | Schéma complet + RLS + index |
| 07 | `.github/workflows/ci.yml` + Dockerfiles | US-04 | CI verte, 0 secret détecté |
| 08 | `.env.example` + `.gitignore` | — | 0 secret dans le dépôt |
| 09 | Logs structurés + Actuator + Nginx log | — | Health OK, logs lisibles |
| 10 | Checklists devops + sécurité | — | Gate 6 Go |

---

## Score de maturité cible (/20)

| Axe | Note cible | Justification |
|-----|-----------|---------------|
| Complétude | 4 | Toutes les étapes 01→10 couvertes |
| Qualité | 4 | Pipeline CI complet, tests intégration, quality gate |
| Sécurité | 4 | SAST/SCA/scan secrets/images, RLS, secrets hors dépôt, TLS |
| Traçabilité | 3 | Stories → étapes → critères ; OpenAPI en Phase 08 |
| Automatisation | 4 | CI/CD intégral, `docker compose up` reproductible |
| **Total** | **19/20** | |

---
*Livrable CGPA v1.0 — Phase 07 (DevSecOps). Plan avant implémentation. ⚠️ Chaque étape doit être validée (critères cochés) avant de passer à la suivante. Prochaine action : implémenter étape par étape, puis valider Gate 6.*
