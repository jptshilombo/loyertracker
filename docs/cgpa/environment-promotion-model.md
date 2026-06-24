# Environment Promotion Model — LoyerTracker

> Formalisation de la décision **ENV-01** (CGPA v5.2,
> `setup-cgpa/docs/cgpa/environment-promotion-model.md`). Lève la réserve **R-V52-3**
> ouverte lors de la reprise CGPA v5.2. Document vivant : à maintenir à chaque évolution
> de la chaîne d'environnements.

## Décision ENV-01

LoyerTracker définit explicitement les environnements utilisés et leur chaîne de
promotion. Le projet est un MVP en **cap vers la production** (profil PME) : le modèle
de référence complet **Dev → Test → Staging → Production** est retenu, sans fusion
Dev/Test (le Test est exercé en CI, distinct du Dev local).

## Environnements définis

| Environnement | Rôle | Support technique | Source des images | État |
|---|---|---|---|---|
| **Dev** | Intégration locale développeur | `docker-compose.yml` + `.env` local | images **buildées localement** (code monté) | Actif |
| **Test** | Tests automatiques et fonctionnels | CI GitHub Actions (`ci.yml`, `codeql.yml`) | éphémère : Testcontainers (backend), Chrome headless (frontend), stack Compose jetable (smoke) | Actif |
| **Staging** | Validation d'un incrément en conditions représentatives | `docker-compose.staging.yml` sur `ai-test-server` | **GHCR** `ghcr.io/jptshilombo`, **tag immuable `sha-<8>`** | Actif, **exposé publiquement** (`https://loyertracker.staging.loyerpro.org`) |
| **Production** | Utilisateurs finaux | `docker-compose.prod.yml` (+ `docker-compose.yml`) | GHCR, tag immuable `sha-<8>` | **Provisionné, LIVE depuis le 2026-06-20** (Gate 10 GO) |

**Staging et Production sont distincts** (exigence non négociable v5.2). Production est
provisionnée sur un hôte dédié (`loyertracker-prod-server`) depuis le **Gate 10 — Mise en
production (GO, 2026-06-20)** ; elle est hors périmètre de mutualisation, à la différence de
Staging (cf. §STG-ISOL-01 ci-dessous, CGPA v5.4).

> Correction de cohérence (migration CGPA v5.4, 2026-06-24) : cette ligne indiquait encore
> « Non provisionné », état devenu obsolète depuis la mise en Production réelle de la release
> `1.0.0` (Gate 09 GO sous réserve, Gate 10 GO). Corrigée pour refléter l'état réel, sans
> suppression du contenu historique du présent document.

## Règles de promotion

* **Dev → Test** : tout push déclenche la CI (build + tests + scans). Aucun artefact
  promu sans CI verte.
* **Test → Staging** : seul un merge sur `main` publie les images GHCR (`sha-<8>` + alias
  `latest`, jamais déployé par `latest`) ; le déploiement staging consomme un **tag
  immuable explicite** (`LOYERTRACKER_TAG=sha-<8>`).
* **Staging → Production** : autorisé uniquement si le **Gate Staging Readiness** est GO
  **et** le **Gate 07A — Release Readiness** est GO (version identifiée, changelog,
  release notes, rollback documenté).

Une promotion n'est autorisée que si le gate applicable est **GO** ou **GO sous réserve
avec actions correctives datées**.

## Traçabilité minimale d'une promotion

Chaque promotion trace (cf. `docs/staging-state.md` §8 — historique des redéploiements) :

| Champ | Où | Exemple |
|---|---|---|
| Version / commit promu | `LOYERTRACKER_TAG` | `sha-73359c5c` |
| Environnement source → cible | `staging-state.md` §8 | `main` → Staging |
| Décision de gate | `staging-state.md` §6, registre des décisions | Gate Staging Readiness GO (2026-06-14) |
| Responsable | registre des décisions | `jptshilombo@gmail.com` |
| Date | `staging-state.md` §8 | 2026-06-16 |
| Rollback identifié | `staging-state.md` §7 | redéploiement du `LOYERTRACKER_TAG` précédent (tags immuables) |

## Proportionnalité (CGPA v5.2)

Profil retenu : **PME / startup** → `Dev, Test, Staging, Production`. Aucune fusion
Dev/Test (le Test CI est isolé du Dev local). Aucun environnement additionnel justifié à
ce stade.

## Gates applicables à la promotion

| Transition | Gate(s) requis | Statut |
|---|---|---|
| Dev → Test | CI verte (Test) | ✅ continu |
| Test → Staging | CI verte + politique de tag immuable + **`STG-ISOL-01`** (CGPA v5.4, cf. ci-dessous) | ✅ Gate Staging Readiness GO (2026-06-14) ; `STG-ISOL-01` PASS (2026-06-24) |
| Staging → Production | **Gate 07A — Release Readiness** + Release Governance | ✅ Gate 07A GO sous réserve (2026-06-19) ; Gate 09 GO sous réserve + Gate 10 GO (2026-06-20), release `1.0.0` puis `1.1.0` `PRODUCTION_DEPLOYED` |

> Correction de cohérence (migration CGPA v5.4, 2026-06-24) : cette ligne indiquait encore
> « ⬜ non statué (R-V52-5) », état devenu obsolète depuis la levée de R-V52-5 et les Gates
> 07A/09/10 statués. Corrigée pour refléter l'état réel, sans suppression du contenu historique.

## Isolation du Staging mutualisé — `STG-ISOL-01` (CGPA v5.4)

> Ajouté par la migration CGPA v5.4 (2026-06-24). `ai-test-server` héberge LoyerTracker **et**
> d'autres projets (« loyerpro », outils labo) derrière un reverse proxy partagé. Le Gate
> `STG-ISOL-01` (namespace Docker, réseau/volume dédiés, absence de commande Docker globale,
> reverse proxy par nom DNS) est désormais bloquant à la promotion Test → Staging. Statué
> **PASS** le 2026-06-24 : `docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md`. Détail :
> `docs/cgpa/checklists/stg-isol-01-checklist.md`, `docs/cgpa/workflows/staging-isolation-workflow.md`,
> `docs/cgpa/05-architecture-conception/adr/ADR-STG-001-isolation-staging-partage.md`,
> inventaire des ressources mutualisées : `docs/staging-state.md` §11. Production
> (`loyertracker-prod-server`) est un hôte dédié, hors périmètre de ce contrôle.

## Accès SSH inter-serveurs (réseau privé) — politique réseau

> Contexte (2026-06-24) : les hôtes dev/CI (`loyerpro-ci-server`, qui héberge également
> SonarQube), test/staging (`ai-test-server`) et production (`loyertracker-prod-server`)
> résident dans le **même VPC** (`vpc-01a99b76679b8e92e`). Un accès SSH inter-serveurs par
> **IP privée** est ouvert entre eux (déjà en place entre `loyerpro-ci-server` et
> `loyertracker-prod-server`, cf. `docs/prod-state.md` §SG — IP privée `172.31.30.45/32`
> autorisée sur le SG production).

**Règle** : pour tout accès SSH d'un serveur du périmètre LoyerTracker vers un autre serveur
du même périmètre (dev/CI, test/staging, SonarQube), **l'IP privée est prioritaire** sur l'IP
publique dès que la source et la cible sont dans le même VPC et que le Security Group
applicable (qu'il s'agisse d'un SG unique partagé ou de SG distincts avec règles croisées par
IP privée) l'autorise. L'IP publique reste réservée :

- aux accès depuis un poste externe autorisé (hors VPC) ;
- au dernier recours si l'accès par IP privée échoue et n'est pas réparable immédiatement.

Cette règle est cohérente avec ENV-01 (la chaîne d'environnements reste Dev → Test → Staging →
Production, inchangée) et avec le principe déjà appliqué en Production (restriction SG SSH du
2026-06-20, `docs/prod-state.md`). Coordonnées réelles (IP privées, clés) : voir
`SERVER_CONFIG.md` (hors dépôt, source de vérité de l'exploitant) — **à vérifier/mettre à jour**
si la topologie des Security Groups a évolué depuis le 2026-06-19 (cf. réserve ci-dessous).
Détail opérationnel : `docs/cgpa/07-devsecops/runbook-exploitation.md` §0.1.

> **Réserve ouverte** : `SERVER_CONFIG.md` (2026-06-19) documente 3 Security Groups distincts
> (`loyerpro-ci-server-sg`, `innovtech-ai-lab-sg`, `loyertracker-prod-sg`), chacun avec ses
> propres règles d'entrée, et non un Security Group unique partagé par les trois hôtes. Si une
> consolidation en un SG commun a été réalisée depuis, `SERVER_CONFIG.md` doit être mis à jour
> en conséquence pour rester la source de vérité ; à défaut, cette section documente le modèle
> *fonctionnel* (IP privée prioritaire intra-VPC) indépendamment du détail d'implémentation SG.

## Réserve d'alignement (avant provisioning production)

* **Cohérence images prod** : `docker-compose.prod.yml` référence encore
  `loyertracker/api:${IMAGE_TAG:-latest}` et `loyertracker/web:${IMAGE_TAG:-latest}`
  (noms locaux + défaut `latest`), alors que la politique de promotion impose les images
  **GHCR à tag immuable `sha-<8>`** (jamais `latest` en déploiement). À aligner sur la
  source d'images GHCR + tag immuable lors du cadrage du cap production (Plan d'Exécution
  R-V52-5 / Gate 07A).

---
*Livrable CGPA v5.2 — Environment Promotion Model (ENV-01). Réf. :
`setup-cgpa/docs/cgpa/environment-promotion-model.md`. Voir aussi `docs/staging-state.md`
(promotions staging) et `docs/cgpa/07-devsecops/gate-06A-decision.md`.*
