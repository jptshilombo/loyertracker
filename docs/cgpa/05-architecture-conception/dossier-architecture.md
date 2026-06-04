# Dossier d'architecture (DAT) — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 05 — Architecture & conception |
| Gate visé | Gate 4 (dernier verrou avant code) |
| Statut | En revue |
| Prérequis | CDC validé (Gate 3 Go) |

> Ce DAT matérialise les **7 décisions d'architecture** arbitrées en clôture de Phase 04 (réserves Gate 3). Chaque décision structurante est détaillée dans un **ADR** dédié (`adr/`). ⛔ **Aucun code applicatif** n'est produit ici : ce document est une spécification de conception (verrou de codage maintenu jusqu'à Gate 4 Go).

---

## 1. Vue d'ensemble

LoyerTracker est une application web **bailleur-centrée avec délégation fine par bien**, déployée en **self-hosting conteneurisé**. L'architecture est un **monolithe modulaire** (Spring Boot) + **SPA** (Angular) + **IdP** (Keycloak) + **PostgreSQL**, le tout orchestré par Docker Compose.

Le choix du monolithe modulaire (vs microservices) est délibéré : périmètre MVP borné, dev solo, coûts cash quasi nuls, surface d'intégration externe nulle (CDC §5.4). La modularité interne (packages par domaine métier) préserve l'évolutivité sans le coût opérationnel d'une architecture distribuée.

```
                          ┌──────────────────────────┐
                          │        Navigateur        │
                          │  Angular SPA (OIDC+PKCE)  │
                          └────────────┬─────────────┘
                                       │ HTTPS (443) — origine unique
                             ┌─────────▼─────────────┐
                             │   Nginx reverse proxy │  ← terminaison TLS
                             │   point d'entrée unique│
                             │  /            → SPA    │
                             │  /auth        → Keycloak (console + OIDC)
                             │  /api         → Backend │
                             └───┬───────────┬────────┬┘
              (1) login OIDC     │           │        │ (2) API REST (Bearer JWT)
                  + console      ▼           ▼ static │
              ┌────────────────────┐   ┌──────────┐  ▼
              │     Keycloak       │   │  Angular │ ┌───────────────────────────┐
              │  (realm dédié)     │   │  statique│ │     Spring Boot API        │
              │  AuthN + RBAC      │◄──┤ (assets) │ │ SecurityFilter (JWT)       │
              │  rôles BAILLEUR /  │   └──────────┘ │ AuthorizationService(ReBAC)│
              │  GESTIONNAIRE      │                │ Services métier (modules)  │
              │  Admin API ▲       │◄───────────────┤  comptes·biens·baux·...    │
              │             │      │ (3) provision  │ Batch @Scheduled 07:00     │
              └─────────────┼──────┘   gestionnaire └─────────────┬──────────────┘
                            │            (EF-04)                  │ JDBC
                            │                          ┌──────────▼──────────┐
                            └─────────(JDBC)──────────►│     PostgreSQL      │
                                                       │  RLS sur bailleurId │
                                                       └─────────────────────┘
```

> **Point d'entrée unique :** seul **Nginx** est exposé (443). SPA, Keycloak (console + endpoints OIDC) et API sont servis derrière le même hôte/origine, ce qui réduit la surface d'attaque et simplifie le TLS et le CORS (origine unique). Keycloak et PostgreSQL ne sont **pas** publiés directement.

---

## 2. Composants & couches

| Composant | Responsabilité | Technologie |
|-----------|----------------|-------------|
| Reverse proxy | **Point d'entrée unique** : terminaison TLS, routage `/`→SPA, `/auth`→Keycloak (console + OIDC), `/api`→backend ; sert les assets statiques de la SPA | **Nginx** |
| Frontend | SPA, tableaux de bord cloisonnés (EF-70/71), affichage alertes in-app, responsive (ENF-11) | Angular (build statique servi par Nginx) |
| Backend | API métier REST, règles de gestion, cloisonnement, batch | Spring Boot (Java) |
| Auth | AuthN OIDC + RBAC grossier (rôles), provisioning gestionnaire via Admin API | Keycloak (OIDC) |
| Données | Persistance + intégrité (contraintes, index uniques partiels) + RLS | PostgreSQL |
| Migrations | Schéma versionné, reproductible | Flyway |
| Déploiement | Orchestration locale, config externalisée (ENF-09) | Docker Compose |

**Découpage backend (packages par domaine, couplage maîtrisé) :**
`comptes` · `biens` · `baux` · `affectations` · `paiements` · `garanties` · `honoraires` · `alertes` · `audit` · `securite` (transverse : `AuthorizationService`) · `batch`.

Chaque module suit la séparation **Controller → Service → Repository**. **Toute** la logique de cloisonnement et d'autorisation fine est portée par le **service layer** (jamais le controller seul), conformément à ENF-02.

---

## 3. Modèle de données

### 3.1 Entités & relations (MCD logique)

```
Bailleur (1) ───< (N) Bien (1) ───< (N) Bail
    │                  │                  │
    │                  │ (1)              │ (1)
    │                  ├───< (N) Affectation >─── (N) Gestionnaire
    │                  │                  │
    │                  └───< (N) Paiement │
    │                                     └───< (1) Garantie
    │
    ├───< (N) Honoraire (rattaché à Affectation + période)
    ├───< (N) Alerte
    ├───< (N) AuditLog
    └───< (N) Invitation
```

> **Règle d'ownership :** **toute** table métier porte une colonne `bailleur_id` (dénormalisée si besoin) → discriminant de cloisonnement (ADR-01) et clé d'index de performance (ENF-06).

### 3.2 Attributs structurants & nouveautés actées

| Entité | Attribut décisif | Valeurs | Origine |
|--------|------------------|---------|---------|
| `Bail` | **`statut`** *(nouveau — gap corrigé CDC)* | `ACTIF` \| `CLOS` | EF-12/13 |
| `Affectation` | `statut` | `ACTIVE` \| `REVOQUEE` \| `EXPIREE` | EF-20/22 |
| `Affectation` | `type_honoraires`, `montant_honoraires` | `POURCENTAGE` \| `FORFAIT` | EF-50 |
| `Paiement` | `statut` | `RECU` \| `PARTIEL` \| `EN_RETARD` \| `IMPAYE` | EF-30 |
| `Paiement` | `periode` | `YYYY-MM` — **mois consommé** (≥ mois de début du bail) | EF-33 |
| `Paiement` | **`date_exigibilite`** *(nouveau)* | 1er du mois **suivant** la période (terme échu) | EF-33/60 |
| `Garantie` | `statut` | `DETENU` \| `RESTITUE_PARTIEL` \| `RESTITUE_TOTAL` | EF-41 |
| `Honoraire` | `statut` | `DU` \| `EN_ATTENTE` \| `PAYE` | EF-52 |
| `Alerte` | `statut`, `type`, `periode` | cf. EB Annexe A.1 | EF-60→65 |

### 3.3 Index & contraintes d'intégrité (décision Réserve 2)

Les règles d'unicité métier sont garanties **au niveau base** par des **index uniques partiels** (atomiques, sûrs en concurrence — vs check applicatif faillible) :

| Règle (EF) | Contrainte (conceptuel, PostgreSQL) |
|------------|--------------------------------------|
| **EF-12** un seul bail actif/bien | `CREATE UNIQUE INDEX uq_bail_actif ON bail (bien_id) WHERE statut = 'ACTIF';` |
| **EF-21** un seul gestionnaire actif/bien | `CREATE UNIQUE INDEX uq_affectation_active ON affectation (bien_id) WHERE statut = 'ACTIVE';` |
| **EF-30/33** un loyer attendu/mois | `CREATE UNIQUE INDEX uq_paiement_periode ON paiement (bien_id, periode);` |
| **EF-51** un honoraire/affectation/mois | `CREATE UNIQUE INDEX uq_honoraire_periode ON honoraire (affectation_id, periode);` |
| **EF-65** anti-doublon alerte | `CREATE UNIQUE INDEX uq_alerte_nonlue ON alerte (type, bien_id, periode) WHERE statut = 'NON_LUE';` |

**Index de performance (ENF-06, < 2 s / 50 biens) :** `bien(bailleur_id)`, `bail(bailleur_id)`, `paiement(bailleur_id)`, `affectation(gestionnaire_id, statut)` (dashboard gestionnaire EF-71), `alerte(destinataire_id, statut)` (badge alertes non lues).

> Toute violation d'unicité remonte en **HTTP 409** (CDC §5.1).

---

## 4. Contrats d'API

Ressources REST métier, **toutes scopées `bailleurId`** au service layer, JWT Bearer obligatoire (sauf acceptation d'invitation). Pagination des listes ; erreurs normalisées 401/403/404/409.

| Endpoint (exemple) | Méthode | Description | Sécurité |
|--------------------|---------|-------------|----------|
| `/api/biens` | GET/POST | Liste / création de biens | BAILLEUR ; périmètre = ses biens |
| `/api/biens/{id}/baux` | GET/POST | Baux d'un bien (409 si 2ᵉ actif) | BAILLEUR \| GESTIONNAIRE affecté actif |
| `/api/affectations` | POST | Créer une affectation (409 si 2ᵉ active) | BAILLEUR seul |
| `/api/affectations/{id}/revocation` | POST | Révoquer (→ `REVOQUEE`) | BAILLEUR seul |
| `/api/paiements` | POST | Pointer un loyer (EF-30) | BAILLEUR \| GESTIONNAIRE affecté actif |
| `/api/garanties/{id}/restitution` | POST | Restituer (transition de statut) | BAILLEUR \| GESTIONNAIRE affecté actif |
| `/api/honoraires/{id}/validation` | POST | Valider `→ PAYE` (EF-52) | **BAILLEUR seul** |
| `/api/alertes` | GET | Alertes du périmètre (EF-64) | selon rôle/affectations |
| `/api/invitations` | POST | Inviter un gestionnaire (token 72h) | BAILLEUR seul |
| `/api/invitations/{token}/acceptation` | POST | Accepter (crée compte EF-04) | **non authentifié** (token) |
| `/api/audit-log` | GET | Journal d'audit | **BAILLEUR seul**, son `bailleurId` |
| `/api/rgpd/export` · `/api/rgpd/locataires/{id}` | GET · DELETE | Export / effacement RGPD (ENF-04) | BAILLEUR seul |

> Les contrats détaillés (schémas req/resp, codes) seront figés en OpenAPI au début de la Phase 07, **après** Gate 4 Go.

---

## 5. Architecture de sécurité

### 5.1 Authentification & autorisation (ADR-02)
- **Keycloak = AuthN + RBAC grossier** : rôles realm `BAILLEUR` / `GESTIONNAIRE` portés dans le JWT. Client SPA public (PKCE), client backend resource-server.
- **Application = autorisation fine (ReBAC)** : un `AuthorizationService` central résout le **périmètre effectif** d'une requête — `bailleur → tous ses biens` / `gestionnaire → biens où une Affectation est ACTIVE`. Mise en œuvre via Spring Security `@PreAuthorize` adossé à ce service. **Aucune permission par-bien dans Keycloak** (évite l'explosion combinatoire et la désynchronisation à chaque rotation).
- **EF-72 (403 côté serveur)** = test d'intégration de l'`AuthorizationService`, indépendant de l'UI.

### 5.2 Cloisonnement (ADR-01 — défense en profondeur)
1. **Couche 1 — service layer (primaire) :** filtre `bailleurId` + résolution d'affectations actives systématiques.
2. **Couche 2 — PostgreSQL Row-Level Security (filet) :** policy RLS sur `bailleur_id` ; un oubli de `WHERE` ne peut jamais provoquer de fuite cross-bailleur. Le contexte tenant est injecté par requête (`SET app.current_bailleur`).
3. **Couche 3 — tests d'autorisation (ENF-02) :** suite couvrant chaque endpoint, objectif **0 accès cross-bailleur/cross-affectation**.

### 5.3 Gestion des secrets
Secrets **hors dépôt** (variables d'environnement / fichier `.env` non versionné, `.env.example` fourni). Aucun secret en clair (ENF-03, interdiction CLAUDE.md §6). Scan secrets en CI (Phase 07).

### 5.4 Surface d'attaque & reverse proxy (ADR-08)
Volontairement minimale : **aucune intégration externe** au MVP (pas de SMTP, banque, paiement). **Nginx est le seul composant exposé** (port 443) ; Keycloak, le backend et PostgreSQL ne sont **pas** publiés (réseau Docker interne).
- **Terminaison TLS** centralisée sur Nginx (certificat unique) ; trafic interne en clair sur le réseau privé Docker.
- **Origine unique** (`/`→SPA, `/auth`→Keycloak, `/api`→backend) : simplifie le CORS (same-origin) et la politique de cookies.
- **Durcissement Nginx :** en-têtes de sécurité (HSTS, CSP, `X-Content-Type-Options`, `X-Frame-Options`), masquage de version, limites de débit/taille de requête, timeouts.
- **Console d'admin Keycloak** atteignable **uniquement** via `/auth/admin` derrière Nginx (restriction d'accès possible par IP/règle au niveau proxy).
- Anti-CSRF géré par flux OIDC + JWT stateless ; validation d'entrées au backend.

### 5.5 Conformité RGPD (ADR-03)
- **Pseudonymisation** du locataire à l'effacement (vs suppression physique) → préserve l'intégrité financière (paiements/honoraires) et la chaîne d'audit (conservation bail+3 ans, ENF-05), tout en honorant le droit à l'effacement (ENF-04). Résout le conflit effacement ↔ conservation.
- **Cartographie PII** : seules `Locataire`, `Bailleur`, `Gestionnaire` portent des données personnelles.
- **Registre des traitements** minimal (Annexe A.2 EB) + **export** = sérialisation scopée `bailleurId`.
- **Audit** (`AuditLog`) : écritures sensibles uniquement, accès BAILLEUR seul.

---

## 6. Infrastructure & déploiement

- **Environnements :** `dev` (Compose local) ; `staging`/`prod` = même image, config externalisée (cible self-hosting unique au MVP).
- **Conteneurisation :** images `nginx` (reverse proxy/TLS, **seul port publié : 443**), `api`, `keycloak`, `postgres` ; `docker compose up` démarre la stack via variables d'environnement (ENF-09). La SPA Angular est buildée en assets statiques servis par Nginx (pas de conteneur web séparé).
- **Réseau :** un réseau Docker interne ; `api`, `keycloak`, `postgres` non exposés à l'hôte — joignables seulement via Nginx (ou en interne).
- **Routage Nginx :** `/`→assets SPA · `/auth`→Keycloak · `/api`→backend. Config Nginx versionnée (template + variables, pas de secret en dur).
- **Stratégie de déploiement :** recreate/rolling simple (dev solo, pas de HA). Sauvegardes PostgreSQL planifiées + test de restauration (ENF-07).
- **Migrations :** Flyway au démarrage de l'`api` (schéma reproductible, ordonnancé).

---

## 7. Décisions d'architecture (ADR)

| ADR | Décision | Alternatives écartées | Justification |
|-----|----------|----------------------|---------------|
| [ADR-01](adr/ADR-01-cloisonnement-multitenant.md) | Discriminant `bailleurId` au service layer **+ RLS PostgreSQL** en défense en profondeur ; périmètre gestionnaire dynamique via `Affectation` ACTIVE | Schema-per-tenant ; discriminant seul | Sur-ingénierie écartée ; RLS = filet anti-fuite ; le gestionnaire n'est pas un simple scope statique |
| [ADR-02](adr/ADR-02-keycloak-vs-autorisation-fine.md) | Keycloak = AuthN + RBAC grossier ; **autorisation fine (ReBAC) dans l'app** | Permissions par-bien dans Keycloak | Évite explosion combinatoire + désync à chaque rotation |
| [ADR-03](adr/ADR-03-rgpd-by-design.md) | **Pseudonymisation** du locataire à l'effacement | Suppression physique | Concilie droit à l'effacement et obligation de conservation (audit, bail+3 ans) |
| ADR-04 | EF-33 = **génération lazy idempotente** par le batch (upsert `(bien_id, periode)` du mois courant) | Eager total ; calcul virtuel | Robuste aux changements de bail ; fournit la ligne cible nécessaire au pointage et à l'alerte retard |
| ADR-05 | EF-51 = **upsert idempotent `(affectation_id, periode)`**, recalcul `POURCENTAGE` à chaque pointage tant que `≠ PAYE`, gel du montant à `PAYE` | Double déclencheur non réconcilié | Supprime le risque de double calcul ; assiette `%` correcte sur paiements partiels |
| ADR-06 | **Monolithe modulaire** Spring Boot (packages par domaine) | Microservices | Périmètre borné, dev solo, coût opérationnel nul |
| ADR-07 | Intégrité métier par **index uniques partiels** PostgreSQL → 409 | Verrous applicatifs | Atomique, sûr en concurrence |
| ADR-08 | **Nginx en reverse proxy** : point d'entrée unique (TLS), sert la SPA et proxy `/auth`→Keycloak (console + OIDC) et `/api`→backend | Exposer chaque service directement ; Traefik/Caddy | Surface d'attaque réduite (seul 443 publié), origine unique (CORS simplifié), TLS centralisé ; Nginx maîtrisé, léger, statique |

> ADR-01 à 03 sont détaillés en fiches dédiées (réserves Gate 2). ADR-04 à 08 sont consignés ci-dessus (décisions de moindre portée).

### Mécaniques détaillées (Réserve 3)

**EF-33 — génération des loyers attendus (ADR-04), modèle à terme échu :**
- **Période de consommation** : du **mois de début** du bail jusqu'au **mois du terme** (inclus), un loyer attendu = `loyer_CC` par mois, **sans prorata**.
- **Exigibilité (terme échu)** : chaque échéance de période `m` est **exigible le 1er du mois `m+1`** (`date_exigibilite`). Le locataire paie le mois consommé.
- **Génération par le batch** : à chaque run, le job parcourt les baux `ACTIF` et fait un **upsert idempotent** (`uq_paiement_periode` sur `(bien_id, periode)`) des échéances dont la période est **révolue ou en cours** jusqu'au dernier mois consommé, en calculant `date_exigibilite = 1er(periode + 1 mois)`.

> 💡 **Exemple (décideur, 2026-06-04) :** bail démarrant le **1er mai 2026** → échéance `periode = 2026-05`, **`date_exigibilite = 2026-06-01`**. Au début de juin, le locataire paie l'échéance **Mai_2026**. Le mois d'entrée n'est **pas** perdu : il est facturé à terme échu.
>
> **Impact EF-60 (alerte retard) :** le retard se calcule par rapport à **`date_exigibilite` + tolérance**, et non par rapport à la période — une échéance `2026-05` ne peut être « en retard » qu'à partir de juin.

**EF-51 — déclenchement des honoraires (ADR-05) :** clé idempotente `(affectation_id, periode)`. `POURCENTAGE` : recalcul = `% × loyer_encaissé_du_mois` à chaque pointage **tant que** `statut ≠ PAYE` (un paiement partiel fait évoluer l'assiette). `FORFAIT` : montant fixe posé par le batch de fin de mois. Les deux chemins (pointage / batch) **convergent vers le même upsert** ; le montant se **fige dès `→ PAYE`** (validation BAILLEUR, EF-52).

---

## 8. Risques techniques

| Risque | Impact | Mitigation |
|--------|--------|------------|
| Oubli d'un filtre `bailleurId` au service layer | Fuite cross-bailleur (RGPD) | RLS PostgreSQL (couche 2) + suite de tests d'autorisation (ENF-02) |
| Désync Keycloak ↔ affectations | Accès incohérent | Aucune autorisation fine dans Keycloak (ADR-02) ; source de vérité = `Affectation` |
| Double calcul d'honoraires | Incohérence financière | Upsert idempotent `(affectation_id, periode)` + gel à `PAYE` (ADR-05) |
| Conflit effacement RGPD ↔ conservation audit | Non-conformité | Pseudonymisation (ADR-03) |
| Course sur création concurrente (bail/affectation) | Doublon métier | Index uniques partiels → 409 atomique (ADR-07) |
| Batch raté un jour | Alerte/échéance retardée > J+1 | Idempotence : le run suivant rattrape sans doublon (ENF-08) |
| Provisioning gestionnaire via Admin API | Couplage Keycloak | Encapsulé dans un adaptateur dédié (module `comptes`) |

---

## 9. Score de maturité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité | 4 |
| Sécurité | 4 |
| Traçabilité | 4 |
| Automatisation | 2 |
| **Total** | **18/20** |

> Lecture : **18/20 → « Solide+ »**. Architecture tracée au CDC (composants ↔ EF/ENF), ENF couvertes, sécurité by design (cloisonnement à 3 couches, ReBAC, secrets, RGPD), modèle de données + contrats d'API définis, déploiement Docker spécifié, 7 ADR documentés. *Automatisation* à 2 : la CI/CD et la suite de tests seront outillées en Phases 06–07 (pipeline non encore implémenté).

---

## 10. Décision Gate 4

- **Décision recommandée :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Autorisation de coder :** ☑ **Oui (si Go)** — Gate 4 est le **dernier verrou** ; son passage en Go lève le verrou de codage (Gates 1→4 tous Go).
- **Réserves / points de vigilance :**
  1. ✅ *Résolu* — loyer à terme échu confirmé par le décideur (cf. §7, EF-33).
  2. Outiller la CI/CD + suite de tests d'autorisation dès le 1er lot de dev (Phases 06–07).
- **Date & responsable :** 2026-06-04 — jptshilombo@gmail.com (décideur Gate 4).

---
*Livrable CGPA v1.0 — Phase 05 (Architecture & conception). ⛔ Verrou de codage maintenu jusqu'à consignation du **Gate 4 Go**. Prochaine phase : 06 — Planification Agile (backlog).*
