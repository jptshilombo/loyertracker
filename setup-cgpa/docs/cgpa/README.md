# CGPA — Cadre de Gouvernance des Projets Applicatifs

> Cadre standard et réutilisable pour piloter **tout projet logiciel personnel** depuis l'idée jusqu'à la mise en production et l'exploitation, selon une approche **Agile**, **DevOps** et **DevSecOps**.

**Version :** 1.0
**Statut :** Actif
**Périmètre :** Projets personnels — SaaS, application web, mobile, Spring Boot, Angular, Keycloak, Docker, CI/CD.

---

## 1. Finalité du CGPA

Le CGPA fournit une **discipline de gouvernance légère mais rigoureuse** pour des projets menés en solo ou en petite équipe. Il garantit que :

- chaque projet est porté par une **idée qualifiée** et un **besoin exprimé** avant tout code ;
- chaque phase produit des **livrables traçables** ;
- le passage d'une phase à l'autre est soumis à un **gate Go / No Go** ;
- la **sécurité**, la **qualité** et l'**automatisation** sont intégrées dès le départ (DevSecOps) ;
- la **maturité** du projet est mesurable par un **score** objectif.

Le cadre est conçu pour être exploité aussi bien manuellement que par des **agents IA** (Codex via `AGENTS.md`, Claude Code via `CLAUDE.md`).

---

## 2. Les 12 phases

| # | Phase | Dossier | Gate de sortie |
|---|-------|---------|----------------|
| 00 | Cadre général | `00-cadre-general/` | — |
| 01 | Idée & opportunité | `01-idee-opportunite/` | Gate 0 |
| 02 | Expression du besoin (EB) | `02-expression-besoin/` | Gate 1 |
| 03 | Faisabilité | `03-faisabilite/` | Gate 2 |
| 04 | Cahier des charges (CDC) | `04-cahier-des-charges/` | Gate 3 |
| 05 | Architecture & conception | `05-architecture-conception/` | Gate 4 |
| 06 | Planification Agile | `06-planification-agile/` | Gate 5 |
| 07 | DevSecOps | `07-devsecops/` | Gate 6 |
| 08 | Développement | `08-developpement/` | — (itératif) |
| 09 | QA & recette | `09-qa-recette/` | Gate 7 |
| 10 | Production | `10-production/` | Gate 8 |
| 11 | Exploitation | `11-exploitation/` | — |
| 12 | Amélioration continue | `12-amelioration-continue/` | Boucle → 01 |

---

## 3. Livrables obligatoires

| Phase | Livrable | Template |
|-------|----------|----------|
| 01 | Fiche idée | `templates/fiche-idee.md` |
| 02 | Expression du besoin | `templates/expression-besoin.md` |
| 03 | Étude de faisabilité | `templates/etude-faisabilite.md` |
| 04 | Cahier des charges | `templates/cahier-des-charges.md` |
| 05 | Dossier d'architecture | `templates/dossier-architecture.md` |
| 06 | Product backlog + Sprint planning | `templates/product-backlog.md`, `templates/sprint-planning.md` |
| 07 | Pipeline & politique DevSecOps | `checklists/checklist-devops.md`, `checklists/checklist-securite.md` |
| 08 | Code + tests + documentation | `checklists/checklist-documentation.md` |
| 09 | Rapport QA + PV de recette | `templates/rapport-qa.md`, `templates/pv-recette.md` |
| 10 | Checklist + rapport de mise en production | `templates/checklist-production.md`, `templates/rapport-mise-production.md` |
| 12 | Bilan post-production | `templates/bilan-post-production.md` |

---

## 4. Rôles

Sur un projet personnel, une même personne porte plusieurs casquettes. Le CGPA les nomme pour clarifier la **posture** attendue à chaque instant.

| Rôle | Responsabilité principale |
|------|---------------------------|
| **Sponsor / Product Owner** | Porte la vision, priorise la valeur, décide des Go/No Go. |
| **Architecte** | Garantit la cohérence technique et la tenue des exigences non fonctionnelles. |
| **Développeur** | Implémente, teste, documente. |
| **Responsable DevSecOps** | Sécurité, CI/CD, conformité, supervision. |
| **Responsable QA** | Qualité, recette, validation fonctionnelle. |
| **Agent IA copilote** | Challenge, structure, vérifie, propose — sans jamais coder hors gouvernance. |

La matrice RACI complète figure dans `00-cadre-general/CGPA-v1.0.md`.

---

## 5. Validations & principe Go / No Go

Chaque transition de phase franchit un **gate** documenté dans `gates/`. Un gate statue sur l'une des trois décisions :

- ✅ **Go** — tous les critères sont remplis, la phase suivante démarre.
- 🟠 **Go sous réserve** — démarrage autorisé avec des actions correctives tracées et datées.
- ⛔ **No Go** — critères non remplis ; on reste sur la phase courante ou on arrête le projet.

> **Règle d'or :** aucun développement applicatif ne commence tant que **EB (Gate 1)**, **Faisabilité (Gate 2)**, **CDC (Gate 3)** et **Architecture (Gate 4)** ne sont pas en statut **Go** (ou Go sous réserve documenté).

---

## 6. Score de maturité

Chaque phase est évaluée sur **5 axes** (0 à 4 points chacun, soit 20 max) :

1. **Complétude des livrables**
2. **Qualité / robustesse**
3. **Sécurité (DevSecOps)**
4. **Traçabilité / documentation**
5. **Automatisation (CI/CD, tests)**

| Score phase | Niveau de maturité | Décision recommandée |
|-------------|--------------------|----------------------|
| 0–7 | Insuffisant | No Go |
| 8–13 | Partiel | Go sous réserve |
| 14–17 | Solide | Go |
| 18–20 | Excellent | Go |

Le modèle détaillé est décrit dans `00-cadre-general/CGPA-v1.0.md`.

---

## 7. Utiliser le CGPA sur un nouveau projet

1. **Copier** le dossier `docs/cgpa/` dans le nouveau dépôt (ou le référencer comme sous-module).
2. **Placer** `AGENTS.md` et `CLAUDE.md` à la racine du dépôt.
3. Démarrer par la **phase 01** : remplir `templates/fiche-idee.md`.
4. À chaque fin de phase, **dérouler le gate** correspondant dans `gates/` et consigner la décision.
5. Utiliser les **prompts** de `prompts/` pour faire produire/auditer les livrables par un agent IA.
6. Ne **jamais** sauter un gate : la valeur du cadre tient à la discipline des transitions.
7. En fin de cycle, remplir le **bilan post-production** et alimenter la **phase 12** qui réamorce une nouvelle itération.

---

## 8. Organisation du dossier

```
docs/cgpa/
├── README.md                     ← ce fichier
├── RESUME-MISE-EN-PLACE.md       ← synthèse de la mise en place
├── 00-cadre-general/             ← CGPA-v1.0.md (référentiel maître)
├── 01..12-*                      ← une note par phase
├── templates/                    ← 12 gabarits de livrables
├── prompts/                      ← 10 prompts pour agents IA
├── checklists/                   ← 6 listes de contrôle
└── gates/                        ← 9 gates Go/No Go
```

---

*CGPA v1.0 — Cadre réutilisable, compatible Codex & Claude Code.*
