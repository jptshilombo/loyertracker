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
| **Production** | Utilisateurs finaux | `docker-compose.prod.yml` (+ `docker-compose.yml`) | GHCR, tag immuable `sha-<8>` (cf. réserve d'alignement ci-dessous) | **Non provisionné** |

**Staging et Production sont distincts** (exigence non négociable v5.2). Production n'est
pas encore provisionnée ; son ouverture est subordonnée à la Release Governance et au
**Gate 07A — Release Readiness** (R-V52-5).

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
| Test → Staging | CI verte + politique de tag immuable | ✅ Gate Staging Readiness GO (2026-06-14) |
| Staging → Production | **Gate 07A — Release Readiness** + Release Governance | ⬜ non statué (R-V52-5) |

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
