# CGPA v1.0 — Référentiel maître

> Cadre de Gouvernance des Projets Applicatifs — document de référence décrivant la vision, les principes, le cycle de vie, la gouvernance, les règles de transition, la matrice des responsabilités, les livrables et le modèle de score de maturité.

**Version :** 1.0 · **Statut :** Actif · **Dernière revue :** 2026-06-03

---

## 1. Vision générale

Le CGPA est un **système d'exploitation de projet** pour développeurs indépendants et petites équipes. Il transpose les pratiques d'ingénierie d'entreprise (gouvernance par phases, gates de décision, DevSecOps, Agile) à l'échelle d'un **projet personnel**, sans la lourdeur bureaucratique.

Son ambition : qu'un projet personnel atteigne un niveau de **rigueur professionnelle** — du premier brouillon d'idée jusqu'à l'exploitation en production — tout en restant **rapide à dérouler** et **lisible par un agent IA**.

Le CGPA répond à trois douleurs récurrentes des projets personnels :

1. **On code trop tôt** — avant d'avoir qualifié le besoin et la faisabilité.
2. **On néglige la sécurité et la qualité** — repoussées « pour plus tard ».
3. **On ne capitalise pas** — pas de traçabilité, pas de bilan, pas d'amélioration continue.

---

## 2. Principes directeurs

1. **Pas de code sans gouvernance.** EB, faisabilité, CDC et architecture validés avant la première ligne de code applicatif.
2. **Tout livrable est traçable.** Un livrable = un fichier versionné, daté, statué.
3. **Décision explicite à chaque frontière.** Chaque phase se ferme par un gate Go / Go sous réserve / No Go.
4. **Sécurité et qualité par conception (Shift-Left).** DevSecOps n'est pas une phase finale mais un fil conducteur.
5. **Itératif et incrémental.** Agile : on livre de la valeur par petits lots, on inspecte, on adapte.
6. **Automatiser ce qui est répétable.** Tests, builds, déploiements, scans de sécurité dans la CI/CD.
7. **Mesurer la maturité.** Le score objective le « ressenti » et guide la décision de gate.
8. **Documentation vivante.** La doc accompagne le code, pas l'inverse ; elle est à jour à chaque gate.
9. **Réversibilité.** Toute mise en production prévoit son plan de rollback.
10. **Compatibilité IA.** Le cadre est rédigé pour être lu et appliqué par Codex et Claude Code.

---

## 3. Cycle complet : idée → production → amélioration

```
 [01] Idée & opportunité
        │  Gate 0
        ▼
 [02] Expression du besoin (EB)
        │  Gate 1 ──────────────┐
        ▼                       │
 [03] Faisabilité               │  Verrou « pas de code »
        │  Gate 2               │  tant que Gates 1→4
        ▼                       │  ne sont pas Go
 [04] Cahier des charges (CDC)  │
        │  Gate 3               │
        ▼                       │
 [05] Architecture & conception │
        │  Gate 4 ──────────────┘
        ▼
 [06] Planification Agile
        │  Gate 5
        ▼
 [07] DevSecOps (pipeline, sécurité)
        │  Gate 6
        ▼
 [08] Développement (sprints itératifs) ◄─┐
        │                                  │ boucle sprint
        ▼                                  │
 [09] QA & recette  ───────────────────────┘
        │  Gate 7
        ▼
 [10] Production (déploiement)
        │  Gate 8
        ▼
 [11] Exploitation (run, supervision)
        │
        ▼
 [12] Amélioration continue ──► réamorce [01]
```

---

## 4. Gouvernance Agile / DevOps / DevSecOps

### 4.1 Agile
- Cadence par **sprints** (recommandé : 1 à 2 semaines).
- Backlog priorisé par la valeur ; user stories avec critères d'acceptation.
- Cérémonies adaptées au solo : planning, revue, rétrospective (même courtes).
- Définition de « Terminé » (DoD) incluant tests + doc + sécurité.

### 4.2 DevOps
- Tout est versionné (code, infra, docs, configs).
- CI/CD : build, test, package, déploiement automatisés (Docker, registry, environnements).
- Environnements distincts : `dev` → `staging` → `prod`.
- Infrastructure as Code ; configuration externalisée et secrets gérés.

### 4.3 DevSecOps
- **Shift-Left Security** : analyse de risques dès l'EB et l'architecture.
- Scans automatiques : SAST, dépendances (SCA), secrets, images Docker.
- Authentification/autorisation centralisées (ex. **Keycloak** : OIDC/OAuth2).
- Gestion des secrets, principe du moindre privilège, journalisation.
- Revue de sécurité obligatoire au Gate 6 et avant le Gate 8.

---

## 5. Règles de passage entre phases (gates)

1. Une phase n'est **close** que lorsque son gate est statué **Go** ou **Go sous réserve**.
2. Un **No Go** renvoie à la phase courante (corriger) ou décide l'**arrêt** du projet.
3. Un **Go sous réserve** exige une **liste d'actions correctives** datées et un responsable.
4. Le **verrou de codage** : aucune implémentation applicative avant Gates **1, 2, 3 et 4** en Go.
5. Toute décision de gate est **consignée** (date, score, décision, réserves) dans le fichier du gate.
6. Un gate peut être **rejoué** autant de fois que nécessaire ; l'historique est conservé.
7. La phase 08 (développement) et la phase 09 (QA) **bouclent** à chaque sprint avant d'atteindre le Gate 8.

---

## 6. Matrice des responsabilités (RACI)

> **R** = Réalise · **A** = Approuve (redevable) · **C** = Consulté · **I** = Informé
> En contexte solo, une personne cumule les rôles ; la matrice clarifie la **posture** à adopter.

| Activité / Phase | Sponsor/PO | Architecte | Développeur | DevSecOps | QA | Agent IA |
|------------------|:---------:|:----------:|:-----------:|:---------:|:--:|:--------:|
| 01 Idée | A/R | C | I | I | I | C |
| 02 Expression du besoin | A/R | C | C | C | C | R |
| 03 Faisabilité | A | R | C | C | I | R |
| 04 Cahier des charges | A | C | R | C | C | R |
| 05 Architecture | C | A/R | C | C | I | C |
| 06 Backlog / planning | A/R | C | C | I | C | R |
| 07 DevSecOps | C | C | C | A/R | C | C |
| 08 Développement | I | C | A/R | C | C | C |
| 09 QA / recette | A | C | C | C | R | C |
| 10 Production | A | C | C | R | C | I |
| 11 Exploitation | I | C | C | A/R | C | I |
| 12 Amélioration continue | A/R | C | C | C | C | R |

---

## 7. Tableau des livrables

| Phase | Livrable obligatoire | Gabarit | Gate |
|-------|----------------------|---------|------|
| 01 | Fiche idée | `templates/fiche-idee.md` | 0 |
| 02 | Expression du besoin | `templates/expression-besoin.md` | 1 |
| 03 | Étude de faisabilité | `templates/etude-faisabilite.md` | 2 |
| 04 | Cahier des charges | `templates/cahier-des-charges.md` | 3 |
| 05 | Dossier d'architecture | `templates/dossier-architecture.md` | 4 |
| 06 | Product backlog | `templates/product-backlog.md` | 5 |
| 06 | Sprint planning | `templates/sprint-planning.md` | 5 |
| 07 | Politique & pipeline DevSecOps | `checklists/checklist-devops.md` + `checklist-securite.md` | 6 |
| 08 | Code, tests, documentation | `checklists/checklist-documentation.md` | — |
| 09 | Rapport QA | `templates/rapport-qa.md` | 7 |
| 09 | PV de recette | `templates/pv-recette.md` | 7 |
| 10 | Checklist de production | `templates/checklist-production.md` | 8 |
| 10 | Rapport de mise en production | `templates/rapport-mise-production.md` | 8 |
| 12 | Bilan post-production | `templates/bilan-post-production.md` | — |

---

## 8. Modèle de score de maturité

### 8.1 Axes d'évaluation (par phase)

Chaque phase est notée sur **5 axes**, chacun de **0 à 4** (total **/20**) :

| Axe | 0 | 2 | 4 |
|-----|---|---|---|
| **Complétude** | Livrable absent | Partiel | Complet et revu |
| **Qualité / robustesse** | Non évaluée | Acceptable | Éprouvée |
| **Sécurité (DevSecOps)** | Ignorée | Identifiée | Traitée & contrôlée |
| **Traçabilité / doc** | Aucune | Sommaire | À jour & versionnée |
| **Automatisation** | Manuelle | Partielle | Automatisée en CI/CD |

### 8.2 Grille de décision

| Score /20 | Maturité | Décision de gate recommandée |
|-----------|----------|------------------------------|
| 0–7 | Insuffisant | ⛔ **No Go** |
| 8–13 | Partiel | 🟠 **Go sous réserve** (actions correctives) |
| 14–17 | Solide | ✅ **Go** |
| 18–20 | Excellent | ✅ **Go** |

### 8.3 Score global de projet

Le **score global** est la moyenne des scores des phases franchies. Il donne une vue d'ensemble de la maturité du projet et alimente le **bilan post-production** (phase 12).

```
Score global = Σ(score des phases atteintes) / (nombre de phases atteintes)
```

### 8.4 Exemple

> Phase 05 (Architecture) : Complétude 4 · Qualité 3 · Sécurité 3 · Traçabilité 4 · Automatisation 2 → **16/20** → *Solide* → ✅ **Go**.

---

## 9. Versionnement du cadre

- **v1.0** — version initiale du CGPA (2026-06-03).
- Toute évolution majeure du cadre incrémente la version et est consignée ici.

---

*CGPA v1.0 — Référentiel maître. Voir `README.md` pour l'usage opérationnel.*
