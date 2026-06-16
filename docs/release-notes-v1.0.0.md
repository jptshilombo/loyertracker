# Release Notes — LoyerTracker v1.0.0

> Document de release (CGPA v5.2, Release Governance). Première release destinée à la production.

## 1. Identification de la release (D-REL-001)

| Champ | Valeur |
|---|---|
| **Version** | `1.0.0` (Semantic Versioning) |
| **Date** | 2026-06-16 |
| **Commit / artefact** | merge de la release sur `main` → image GHCR `loyertracker-api` / `loyertracker-web` au tag immuable `sha-<8>` produit par la CI |
| **Périmètre fonctionnel** | MVP de gestion locative bailleur-centrée, complet S01→S04 (comptes & délégation, biens/baux/affectations, paiements & garanties, honoraires & pilotage), industrialisé (CD GHCR, staging durci, backup/restore, observabilité + alerting) |
| **Environnement cible** | **Production** (préparée) — **validée sur Staging** ; go-live production différé à un lot ultérieur (Gate 09/10) |
| **Responsable de validation** | PO `jptshilombo@gmail.com` |

## 2. Contenu

Cf. `CHANGELOG.md` (section [1.0.0]) pour le détail. En synthèse : socle fonctionnel MVP
complet, cloisonnement multi-tenant RLS `FORCE`, AuthN Keycloak OIDC/PKCE + ReBAC, chaîne de
livraison GHCR par tag immuable, sauvegarde/restauration prouvée, et observabilité avec alerting
des composants critiques (OBS-02/03).

## 3. Déploiement

- **Source d'images** : GHCR (`ghcr.io/jptshilombo/loyertracker-{api,web}`), **tag immuable
  `sha-<8>`** — jamais `latest`.
- **Staging** : `docker compose -f docker-compose.staging.yml up -d` avec `LOYERTRACKER_TAG=sha-<8>`.
- **Production** (lot ultérieur) : `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`
  avec `LOYERTRACKER_TAG=sha-<8>` (images GHCR, aucun build local — aligné staging).
- **Observabilité** : activer le profil `monitoring` (`COMPOSE_PROFILES=monitoring`), renseigner
  `ALERTMANAGER_WEBHOOK_URL`.

## 4. Rollback (D-REL-004)

Redéployer le `LOYERTRACKER_TAG` précédent (tags immuables). Procédure détaillée :
`docs/cgpa/07-devsecops/runbook-exploitation.md` §3 et `docs/staging-state.md` §7. Base de
données : sauvegarde/restauration `infra/backup/` (drill prouvé).

## 5. Traçabilité production (D-REL-004)

À renseigner lors du go-live production réel (lot ultérieur) :

| Champ | Valeur |
|---|---|
| Version déployée | `1.0.0` (`sha-<8>`) |
| Date et heure | _à compléter_ |
| Environnement source → cible | Staging → Production |
| Décision Gate 07A / Production | _en attente (cf. §6)_ |
| Résultat du déploiement | _à compléter_ |
| Rollback disponible | oui — tag immuable précédent |

## 6. Statut des gates

- **Gate Staging Readiness (v4.0)** : GO (2026-06-14). Re-validation enrichie (alerting) à rejouer.
- **Gate 07A — Release Readiness** : **non statué** — dossier préparé
  (`docs/cgpa/07-devsecops/gate-07A-decision.md`), en attente de la validation staging par
  simulation d'incident (OBS-02/03) et de la re-validation du Gate Staging enrichi.
- **Gate 09 / Gate 10** (production readiness / mise en production) : ultérieurs (go-live différé).

## 7. Limitations connues

- Go-live production non encore réalisé.
- Alerting livré ; validation staging par simulation d'incident en attente.
- OpenAPI non produit ; UX S02 minimale.

---
*Livrable CGPA v5.2 — Release Governance (D-REL-001/003/004). Réf. :
`setup-cgpa/docs/cgpa/release-governance.md`.*
