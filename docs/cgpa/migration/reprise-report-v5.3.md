# État du projet

LoyerTracker est un projet existant en production, gouverné par CGPA v5.3 après migration additive du 2026-06-23. La release `1.0.0` est LIVE depuis le 2026-06-20 sur `https://loyertracker.loyerpro.org`. Les lots post-go-live Quittances et Patrimoine sont intégrés côté `main` mais restent `[Non publié]` tant qu'une décision de promotion/release n'est pas prise.

# Version CGPA détectée

| Champ | Valeur |
|-------|--------|
| `framework_version` | `5.3` |
| `migrated_from` | `5.2` |
| `migration_date` | 2026-06-23 |
| Mode | Additif, sans rejeu de Gate |

# Écarts avec CGPA v5.3

| Écart | Statut | Action |
|-------|--------|--------|
| Gate 02A UX/UI non historique | Accepté sous réserve | Appliquer aux futurs lots UI proportionnés |
| Gate Staging v5.3 du lot Patrimoine | Fermé | Décision créée : `docs/cgpa/07-devsecops/gate-staging-patrimoine-v5.3-decision.md` |
| Drill de rollback production significatif non encore réalisé sur release ultérieure | Ouvert | Planifier à la prochaine release |
| Plan Quittances non formalisé en fichier dédié | Mineur, ouvert | Formaliser a posteriori si le lot s'étend |

# Résultat des contrôles de continuité

| Contrôle | Résultat | Justification |
|----------|----------|---------------|
| Migration Governance Check | PASS | Migration v5.3 additive, phase et Gates conservés |
| Architecture Continuity Check | PASS | ADR et choix structurants conservés ; architecture Spring Boot / Angular / Keycloak / PostgreSQL cohérente |
| Delivery Continuity Check | PASS | Sprints historiques et Patrimoine tracés ; Patrimoine statué `STAGING_DEPLOYED` |
| Release History Check | PASS | `1.0.0` en production, Gate 10 GO, release notes et changelog présents |
| UX/UI Continuity Check | PASS sous réserve | UI Angular existante ; Gate 02A non rétroactif, futurs lots UI à cadrer |
| DevSecOps Continuity Check | PASS | CI/CD, SonarQube, CodeQL, Gitleaks, Trivy, tests et artefacts présents |
| Staging Continuity Check | PASS sous réserve | Staging opérationnel ; lot Patrimoine Gate Staging v5.3 GO (`STAGING_DEPLOYED`) |
| Production Readiness Check | PASS | Production LIVE, Gate 10 GO, monitoring, alerting, backup et rollback documentés |

# Phase actuelle

Phase 7 — Développement, pour les lots post-go-live.

# Gate actuel

Gate 07 — Développement, non statué globalement. Gates transverses déjà validés : Gate Staging Readiness GO, Gate 06A GO, Gate 07A GO sous réserve, Gate 09 GO sous réserve, Gate 10 GO.

# Sprint actuel

Aucun Sprint actif. Sprint 1 Patrimoine et Sprint 2 Patrimoine sont clôturés côté `main`. Sprint 3 Patrimoine n'est pas démarré.

# Release actuelle

`1.0.0`, SemVer, `PRODUCTION_DEPLOYED` depuis le 2026-06-20.

# État DevSecOps

Conforme CGPA v5.3 : GitHub Actions, Maven verify, Angular lint/build/test, SonarQube bloquant, CodeQL, Gitleaks, Trivy, GHCR par tag immuable, smoke script, monitoring et backup/restore.

# État Staging / Production

| Environnement | État |
|---------------|------|
| Staging | Opérationnel, exposé publiquement, Gate Staging historique GO, smoke validé |
| Production | LIVE, Gate 10 GO, release `1.0.0`, monitoring/alerting/backup opérationnels |

# Sous-agents mobilisés

| Sous-agent | Avis |
|------------|------|
| Governance Officer | Resume Approved with Reservations : historique conservé, réserves v5.3 non bloquantes |
| UX/UI Design Lead | GO sous réserve : UI existante, Gate 02A à appliquer aux futurs lots UI |
| Enterprise Architect | GO : architecture et environnements cohérents |
| DevSecOps Lead | GO : CI/CD et sécurité automatisée conformes |
| Release Manager | GO sous réserve : production `1.0.0` conforme, prochaine release à piloter par Gate Production |

# Analyse consolidée

La reprise peut se faire sans retour Phase 00, sans rejeu de Gate et sans reconstruction du backlog. L'état réel est un projet en production avec un flux post-go-live actif. La conformité v5.3 est acquise pour la migration et la traçabilité ; les réserves portent sur les prochains mouvements de delivery/release, pas sur la reprise elle-même.

# Risques

| Risque | Statut |
|--------|--------|
| RSV-RM-01 — accumulation excessive d'éléments en Staging | Ouvert |
| RSV-RM-02 — dérive Staging/Production | Maîtrisé |
| RSV-RM-03 — rollback non testé sur release ultérieure | Ouvert |
| RSV-RM-04 — release contenant plusieurs Epics non validés | Ouvert |
| R-V52-8 — plan Quittances non formalisé en fichier dédié | Ouvert mineur |

# Réserves

- Prochaine Production à piloter par Epic/Release/Hotfix, avec Gate Production explicite.
- Drill de rollback production significatif à planifier à la prochaine release.
- UX/UI Gate 02A à formaliser pour les futurs lots UI.

# Actions correctives nécessaires

1. Arbitrer le prochain axe : cadrage Sprint 3 Patrimoine ou préparation d'une Release candidate incluant Quittances + Patrimoine.
2. Formaliser le périmètre de la prochaine release par Epic ou Release fonctionnelle.
3. Planifier le drill de rollback production au prochain changement de version.
4. Documenter les livrables UX/UI minimaux avant tout lot UI substantiel.

# Décision de reprise

**Resume Approved with Reservations**.

# Actions suivantes

- Ne pas démarrer de nouveau codage sans Plan d'Exécution approuvé.
- Préparer explicitement le prochain Gate Production si une Release candidate est retenue.
- Maintenir `docs/project-state.md`, `docs/staging-state.md` et `docs/prod-state.md` après chaque étape significative.
