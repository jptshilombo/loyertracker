# AGENTS.md — Règles pour Codex (et agents compatibles)

> Ce dépôt est gouverné par le **CGPA — Cadre de Gouvernance des Projets Applicatifs** (voir `docs/cgpa/`). Tout agent automatisé intervenant ici **doit** respecter ce cadre.

---

## 1. Principe fondamental

**Tout projet applicatif de ce dépôt suit le CGPA.** Le référentiel maître est `docs/cgpa/00-cadre-general/CGPA-v1.0.md`. Le mode d'emploi est `docs/cgpa/README.md`.

Aucune action de l'agent ne doit contourner la gouvernance par phases et par gates.

---

## 2. Verrou de codage (RÈGLE BLOQUANTE)

> ⛔ **Il est interdit de commencer le développement applicatif tant que les livrables suivants ne sont pas validés (gate en statut Go ou Go sous réserve documenté) :**
> - **Expression du besoin (EB)** — Gate 1
> - **Faisabilité** — Gate 2
> - **Cahier des charges (CDC)** — Gate 3
> - **Architecture & conception** — Gate 4

Si l'un de ces livrables manque ou n'est pas validé, l'agent **refuse de coder** et produit (ou complète) d'abord le livrable manquant.

---

## 3. Workflow obligatoire de l'agent

À **chaque** sollicitation, Codex doit :

1. **Identifier la phase courante** du projet (parmi les 12 phases du CGPA) avant toute action.
2. **Vérifier les gates franchis** : quels livrables existent, lesquels sont validés.
3. **Appliquer la logique Go / No Go** : ne pas avancer si le gate de la phase courante n'est pas Go.
4. **Produire ou mettre à jour les livrables CGPA** de la phase courante **avant** tout développement.
5. **Annoncer explicitement** la phase, l'état du gate, et la décision (Go / Go sous réserve / No Go) avant d'agir.

Si l'agent ne peut pas déterminer la phase, il le signale et demande à remplir la **fiche idée** (`docs/cgpa/templates/fiche-idee.md`).

---

## 4. Logique de gate Go / No Go

- ✅ **Go** : critères remplis → la phase suivante peut démarrer.
- 🟠 **Go sous réserve** : démarrage autorisé avec actions correctives **datées et tracées**.
- ⛔ **No Go** : critères non remplis → rester sur la phase, corriger, ou arrêter.

Les gates sont décrits dans `docs/cgpa/gates/`. Chaque décision doit être **consignée** (date, score de maturité, décision, réserves).

---

## 5. Livrables avant développement

Avant toute écriture de code applicatif, l'agent s'assure que ces livrables existent et sont à jour :

| Livrable | Emplacement gabarit |
|----------|---------------------|
| Fiche idée | `docs/cgpa/templates/fiche-idee.md` |
| Expression du besoin | `docs/cgpa/templates/expression-besoin.md` |
| Étude de faisabilité | `docs/cgpa/templates/etude-faisabilite.md` |
| Cahier des charges | `docs/cgpa/templates/cahier-des-charges.md` |
| Dossier d'architecture | `docs/cgpa/templates/dossier-architecture.md` |
| Product backlog | `docs/cgpa/templates/product-backlog.md` |

---

## 6. Standards DevSecOps imposés

Tout développement réalisé ou proposé par l'agent doit respecter :

- **Tests** : tests unitaires et d'intégration ; couverture cohérente avec la criticité ; respect de la Définition de « Terminé ».
- **Sécurité** : analyse des risques (Shift-Left), gestion des secrets, moindre privilège, auth via standard (ex. **Keycloak / OIDC**), scans SAST/SCA/secrets/images. Voir `docs/cgpa/checklists/checklist-securite.md`.
- **Documentation** : code documenté, README à jour, ADR pour décisions d'architecture. Voir `docs/cgpa/checklists/checklist-documentation.md`.
- **CI/CD** : build, test, scan et déploiement automatisés (Docker, environnements `dev`/`staging`/`prod`). Voir `docs/cgpa/checklists/checklist-devops.md`.

---

## 7. Interdictions

- ⛔ Coder avant la validation des Gates 1 à 4.
- ⛔ Sauter un gate ou avancer de phase sans décision consignée.
- ⛔ Introduire des secrets en clair dans le dépôt.
- ⛔ Livrer du code sans tests ni documentation.
- ⛔ Modifier le cadre CGPA sans incrémenter sa version (`CGPA-v1.0.md`).
- ⛔ Supprimer des livrables existants ; on les **versionne** et on les **met à jour**.

---

## 8. Stack de référence (projets personnels)

SaaS · application web · mobile · **Spring Boot** · **Angular** · **Keycloak** · **Docker** · **CI/CD**. L'agent adapte ses propositions à cette stack par défaut, sauf indication contraire du cahier des charges.

---

*Voir aussi `CLAUDE.md` (règles pour Claude Code) et `docs/cgpa/README.md`.*
