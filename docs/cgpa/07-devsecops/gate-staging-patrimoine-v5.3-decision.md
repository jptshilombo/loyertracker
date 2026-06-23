# Gate Staging v5.3 — Lot Patrimoine `[Non publié]`

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Périmètre | Lot Patrimoine `[Non publié]` — Sprint 1 + Sprint 2 + correctifs smoke/honoraires |
| Référence | PR #72, #73, #74, #75, #76, #77 |
| Commit de référence projet | `1d6db31` côté `origin/main` après merge PR #77 ; documentation locale ensuite à `05424aa` |
| Environnement | Staging `https://loyertracker.staging.loyerpro.org` |
| Décision | **GO** |
| Statut CGPA v5.3 | `STAGING_DEPLOYED` |

## 1. Objet du Gate

Ce Gate Staging v5.3 statue le passage Staging du lot Patrimoine post-go-live, sans déclencher de Production.

Périmètre inclus :

- Sprint 1 Patrimoine : `Patrimoine`, `TypeBien`, rattachement obligatoire des biens, migration V12.
- Sprint 2 Patrimoine : affectations patrimoine, héritage ReBAC, liste effective gestionnaire, garde RS-06, migration V13.
- Correctifs de validation Staging : smoke réaligné V13+patrimoine, honoraires patrimoine corrigés par V14, compteur Flyway smoke aligné V14.

Hors périmètre :

- Sprint 3 Patrimoine.
- Logique complète `EXCLUSION`.
- UX avancée de périmètre effectif.
- Promotion Production.

## 2. Conditions d'entrée

| Critère | Statut | Preuve |
|---------|--------|--------|
| Sprint identifié | OK | Sprint 1 + Sprint 2 Patrimoine |
| Plan d'Exécution approuvé | OK | `sprint-2-patrimoine-plan-execution.md`, arbitrage PO Option A |
| Rapport d'exécution disponible | OK | `sprint-2-patrimoine-rapport-validation.md` |
| Commit / artefact candidat identifié | OK | `1d6db31` après PR #77 ; tag Staging précédent PR #76 `sha-75473413`, puis staging resynchronisé sur HEAD |
| Environnement Staging identifié | OK | `docs/staging-state.md`, hôte `ai-test-server`, URL publique staging |

## 3. Contrôles Sprint

| Critère | Statut | Preuve |
|---------|--------|--------|
| Stories terminées listées | OK | US-80/81/82, US-84, début US-85 |
| Stories exclues ou reportées listées | OK | Sprint 3 non démarré ; `EXCLUSION` complète et UX avancée hors périmètre |
| Écarts au plan acceptés | OK | R-S04-1 régularisée et fermée par PR #76 |
| Validation Product Owner | OK | Plans et décisions Patrimoine antérieurs validés ; clôtures Sprint 1/2 actées |
| Validation Release Manager | OK | Staging uniquement ; Production explicitement exclue |

## 4. Contrôles DevSecOps

| Critère | Statut | Preuve |
|---------|--------|--------|
| Build stable | OK | CI PR #74/#76/#77 verte |
| Tests unitaires et intégration | OK | Backend 84 tests puis CI verte ; `SchemaMigrationTest` aligné V14 |
| Frontend lint/build/tests | OK | 41 tests frontend, lint/build OK |
| Secrets / SCA / images | OK | Gitleaks, Trivy, CodeQL et packaging Docker verts |
| SonarQube | OK | CI verte 13/13 après re-run d'un flake serveur sans lien fonctionnel |
| Migrations DB | OK | V12, V13, V14 validées ; smoke Flyway aligné V14 via PR #77 |
| Secrets non exposés | OK | Aucun secret versionné détecté ; secrets runtime hors dépôt |

## 5. Validation Staging

| Critère | Statut | Preuve |
|---------|--------|--------|
| Déploiement Staging | OK | Staging redéployé sur `sha-75473413`, puis resynchronisé après PR #77 |
| Services healthy | OK | 4/4 healthy |
| Smoke Staging | OK | 47 PASS / 0 FAIL après correction PR #77 |
| Honoraires patrimoine | OK | Confirmés fonctionnels en conditions réelles Staging |
| Rollback Staging | OK | Redéploiement par `LOYERTRACKER_TAG=sha-<8>` documenté |
| `staging-state.md` | OK sous réserve mineure | État Staging historique maintenu ; décision v5.3 présente dans ce dossier |

## 6. Avis des sous-agents

| Sous-agent | Avis |
|------------|------|
| Governance Officer | GO : écarts R-S04-1 et compteur Flyway fermés ; Gate v5.3 tracé |
| Enterprise Architect | GO : modèle Patrimoine cohérent, ReBAC centralisé, RLS et migrations validées |
| DevSecOps Lead | GO : CI/CD, scans, migrations et smoke Staging validés |
| Release Manager | GO Staging uniquement : aucune autorisation Production ; prochaine release à cadrer par Gate Production |

## 7. Décision

**GO — Gate Staging v5.3 validé pour le lot Patrimoine `[Non publié]`.**

Statuts :

- `STAGING_READY` : atteint.
- `STAGING_DEPLOYED` : atteint, sur la base du redéploiement et du smoke Staging 47 PASS / 0 FAIL.
- `PRODUCTION_READY` : non atteint pour ce lot.
- `PRODUCTION_DEPLOYED` : non atteint pour ce lot.

## 8. Réserves et suite

Réserves non bloquantes :

- Production non autorisée par ce Gate.
- Prochaine Production à piloter par Epic/Release/Hotfix avec Gate Production explicite.
- Sprint 3 Patrimoine à cadrer séparément avant tout codage.
- UX/UI Gate 02A à formaliser si le prochain lot touche significativement l'interface.

Action suivante recommandée :

Choisir entre cadrage Sprint 3 Patrimoine ou préparation d'une Release candidate incluant Quittances + Patrimoine, avec périmètre explicite et Gate Production ultérieur.
