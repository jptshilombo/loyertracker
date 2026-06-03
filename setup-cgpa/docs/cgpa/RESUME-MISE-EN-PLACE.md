# Résumé de mise en place — CGPA v1.0

> Synthèse de la mise en place du **Cadre de Gouvernance des Projets Applicatifs** dans ce dépôt.

**Date :** 2026-06-03 · **Version du cadre :** 1.0

---

## 1. Fichiers créés

### Racine du dépôt
- `AGENTS.md` — règles de gouvernance pour **Codex**.
- `CLAUDE.md` — règles de gouvernance pour **Claude Code** (copilote stratégique).

### Documentation principale (`docs/cgpa/`)
- `README.md` — mode d'emploi du cadre (finalité, 12 phases, livrables, rôles, Go/No Go, usage).
- `RESUME-MISE-EN-PLACE.md` — ce document.
- `00-cadre-general/CGPA-v1.0.md` — référentiel maître (vision, principes, cycle, gouvernance, RACI, livrables, score de maturité).

### Notes de phase (12)
- `01-idee-opportunite/` … `12-amelioration-continue/` — une note `README.md` par phase (objectif, livrables, gate, prompt, score).

### Prompts pour agents IA (10) — `prompts/`
`01-analyser-idee.md`, `02-generer-expression-besoin.md`, `03-evaluer-faisabilite.md`, `04-rediger-cahier-des-charges.md`, `05-concevoir-architecture.md`, `06-generer-backlog-agile.md`, `07-mettre-en-place-devsecops.md`, `08-auditer-code.md`, `09-preparer-recette.md`, `10-preparer-mise-en-production.md`.

### Templates de livrables (12) — `templates/`
`fiche-idee.md`, `expression-besoin.md`, `etude-faisabilite.md`, `cahier-des-charges.md`, `dossier-architecture.md`, `product-backlog.md`, `sprint-planning.md`, `rapport-qa.md`, `pv-recette.md`, `checklist-production.md`, `rapport-mise-production.md`, `bilan-post-production.md`.

### Checklists (6) — `checklists/`
`checklist-securite.md`, `checklist-devops.md`, `checklist-architecture.md`, `checklist-qa.md`, `checklist-production.md`, `checklist-documentation.md`.

### Gates Go/No Go (9) — `gates/`
`gate-0-idee.md` … `gate-8-production.md`.

**Total : 51 fichiers de gouvernance créés. Aucun fichier existant supprimé ; aucun code applicatif modifié.**

---

## 2. Logique générale

Le CGPA structure tout projet en **12 phases** reliées par **9 gates** Go / Go sous réserve / No Go.

- Chaque phase produit un **livrable** (à partir d'un template) et se ferme par un **gate**.
- Un **verrou de codage** interdit tout développement tant que les **Gates 1 à 4** (EB, faisabilité, CDC, architecture) ne sont pas en Go.
- La **sécurité et la qualité** sont intégrées en continu (DevSecOps, Shift-Left), formalisées par les checklists.
- Chaque gate s'appuie sur un **score de maturité /20** (5 axes) qui objective la décision.
- Les **agents IA** (Codex/Claude Code) appliquent ces règles via `AGENTS.md` et `CLAUDE.md`, et produisent les livrables avec les **prompts**.
- En fin de cycle, le **bilan post-production** (phase 12) réamorce une nouvelle itération (boucle vers la phase 01).

---

## 3. Comment utiliser le CGPA

1. **Démarrer** un nouveau projet : copier `docs/cgpa/` et placer `AGENTS.md` + `CLAUDE.md` à la racine du dépôt cible.
2. **Phase 01** : remplir `templates/fiche-idee.md`, puis dérouler `gates/gate-0-idee.md`.
3. **Avancer phase par phase** : pour chaque phase, produire le livrable (template) à l'aide du prompt correspondant, puis statuer le gate.
4. **Respecter le verrou** : ne pas coder avant Gates 1→4 en Go.
5. **Industrialiser** (phase 07) avant le développement intensif : CI/CD + sécurité.
6. **Itérer** en sprints (phases 08–09) jusqu'à la recette (Gate 7).
7. **Mettre en production** (Gate 8), puis **exploiter** (phase 11).
8. **Capitaliser** : bilan post-production (phase 12) → nouvelles idées.

> À chaque gate : noter le **score**, la **décision** et les **réserves datées**.

---

## 4. Prochaine action recommandée

➡️ **Lancer la phase 01 sur votre premier projet** : copier `templates/fiche-idee.md` dans l'espace du projet, le compléter, puis dérouler `gates/gate-0-idee.md` pour statuer le **Gate 0**.

Si vous appliquez le CGPA à un projet existant, commencez par **reconstituer les livrables manquants** (EB, faisabilité, CDC, architecture) avant de reprendre tout développement, conformément au verrou de codage.

---

*CGPA v1.0 — mise en place terminée. Référentiel : `00-cadre-general/CGPA-v1.0.md`.*
