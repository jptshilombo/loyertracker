# Release Notes — LoyerTracker v1.0.0

> Document de release (CGPA v5.2, Release Governance). Première release destinée à la production.

## 1. Identification de la release (D-REL-001)

| Champ | Valeur |
|---|---|
| **Version** | `1.0.0` (Semantic Versioning) |
| **Date** | 2026-06-16 |
| **Commit / artefact** | merge de la release sur `main` → image GHCR `loyertracker-api` / `loyertracker-web` au tag immuable `sha-<8>` produit par la CI |
| **Périmètre fonctionnel** | MVP de gestion locative bailleur-centrée, complet S01→S04 (comptes & délégation, biens/baux/affectations, paiements & garanties, honoraires & pilotage), industrialisé (CD GHCR, staging durci, backup/restore, observabilité + alerting) |
| **Environnement cible** | **Production** — **go-live réalisé le 2026-06-20** (`https://loyertracker.loyerpro.org`, hôte dédié) après validation sur Staging (cf. §5, Gate 10 GO) |
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
- **Production** (LIVE depuis le 2026-06-20) : `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`
  avec `LOYERTRACKER_TAG=sha-73359c5c` (images GHCR, aucun build local — aligné staging). Détail go-live :
  `docs/prod-state.md`.
- **Observabilité** : activer le profil `monitoring` (`COMPOSE_PROFILES=monitoring`), renseigner
  `ALERTMANAGER_WEBHOOK_URL`.

## 4. Rollback (D-REL-004)

Redéployer le `LOYERTRACKER_TAG` précédent (tags immuables). Procédure détaillée :
`docs/cgpa/07-devsecops/runbook-exploitation.md` §3 et `docs/staging-state.md` §7. Base de
données : sauvegarde/restauration `infra/backup/` (drill prouvé).

## 5. Traçabilité production (D-REL-004) — **go-live réalisé le 2026-06-20** ✅

| Champ | Valeur |
|---|---|
| Version déployée | `1.0.0` (**`sha-73359c5c`**, immuable GHCR — `api` + `web`) |
| Date | **2026-06-20** |
| Environnement source → cible | Staging → **Production** (hôte dédié, ENV-01 strict) |
| Hôte / URL | `loyertracker-prod-server` (EC2 `t3.medium`, EIP `18.158.70.88`) → **`https://loyertracker.loyerpro.org`** (TLS Let's Encrypt, exp. 2026-09-17) |
| Opérateur | Claude Code (CGPA Chief Delivery Officer), sous PO `jptshilombo@gmail.com` |
| Décision Gate | **Gate 09 GO sous réserve** (2026-06-19) → **Gate 10 — Mise en production GO** (2026-06-20) |
| Vérifications | 4/4 `healthy`, Flyway V1→V10, pool sous `loyertracker_api` ; **smoke 46/0** ; monitoring **5/5 cibles `up`** ; alerting Discord **FIRING+RESOLVED prouvé** ; backup `-Fc` **vérifié `pg_restore --list`** + cron prod ; base **vierge** au lancement, compte de test **désactivé** |
| Résultat du déploiement | ✅ **Succès — production LIVE** (`docs/prod-state.md`) |
| Rollback disponible | oui — tag immuable précédent (procédure runbook §3 / `staging-state.md` §7) + restauration backup (RPO 24 h) |

Détail complet : `docs/prod-state.md`. Décision : `docs/cgpa/10-mise-en-production/gate-10-decision.md`.
**RR-2 levée.**

## 6. Statut des gates

- **Gate Staging Readiness (v4.0)** : GO (2026-06-14). **Gate Staging enrichi (alerting)** : GO (2026-06-19).
- **Gate 07A — Release Readiness** : **GO sous réserve** (2026-06-19) — RR-1 levée (validation alerting
  staging) ; reserve RR-2 portée au go-live (`docs/cgpa/07-devsecops/gate-07A-decision.md`).
- **Gate 09 — Production Readiness** : **GO sous réserve** (2026-06-19, review 19/24 Solide ;
  `docs/cgpa/09-production/gate-09-decision.md`).
- **Gate 10 — Mise en production** : **GO** (2026-06-20) — production LIVE, réserves RR-2 / RG-09-1 /
  RG-09-2 levées (`docs/cgpa/10-mise-en-production/gate-10-decision.md`).

## 7. Limitations connues

- OpenAPI non produit ; UX S02 minimale.
- Realm de production embarquant le compte de test (désactivé) — état « excellent » = realm prod dédié
  (suivi exploitation, non bloquant).
- SG SSH de l'hôte prod **restreint le 2026-06-20** : port 22 limité à `52.29.80.119/32` (admin) +
  `172.31.30.45/32` (serveur de dev `loyerpro-ci-server`, IP privée même VPC) ; `0.0.0.0/0` retiré.

---
*Livrable CGPA v5.2 — Release Governance (D-REL-001/003/004). Réf. :
`setup-cgpa/docs/cgpa/release-governance.md`.*
