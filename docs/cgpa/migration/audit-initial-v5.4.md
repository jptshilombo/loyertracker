# Audit initial de migration CGPA v5.4 — LoyerTracker

> Fichier versionné distinct de `docs/cgpa/migration/audit-initial.md` (audit de la migration
> CGPA v5.3, conservé tel quel). Aucun audit historique n'est réécrit.

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-24 |
| Projet | LoyerTracker |
| Référentiel cible | CGPA v5.4 (`/home/ubuntu/setup-cgpa/docs/cgpa/CGPA-v5.4.md`) |
| Référentiels associés | `MIGRATION-REPORT-v5.4.md`, `checklists/migration-checklist-v5.4.md` |
| Version détectée avant migration | CGPA v5.3 (`docs/project-state.md` §15, `framework.current_version` historique `5.3`) |
| Décision d'audit | **GO sous réserve** |

## 1. Structure du dépôt

Arborescence `docs/cgpa/` conforme à la migration v5.3 (phases 01 à 10, `agents/`, `checklists/`,
`workflows/`, `migration/`). Aucune restructuration requise par v5.4 : la nouvelle gouvernance
s'ajoute sous forme de documents et de sections supplémentaires, sans déplacer l'existant.

## 2. Version CGPA actuelle

| Champ | Valeur |
|-------|--------|
| `framework.current_version` (avant) | `5.3` |
| `migrated_from` (lignée) | `3.0.1 -> 5.0.1 -> 5.2 -> 5.3` |
| Dernière reprise tracée | 2026-06-24, `Resume Approved with Reservations` (`docs/cgpa/migration/reprise-report-v5.3.md`) |

## 3. Phases et Gates déjà validés (non rejoués)

| Élément | État détecté |
|---------|--------------|
| Phases 00 à 06 | Validées historiquement |
| Phase courante | Phase 7 — Développement |
| Gate 06 | GO le 2026-06-06 |
| Gate Staging Readiness (v4.0) | GO le 2026-06-14 |
| Gate 06A — DevSecOps Readiness | GO le 2026-06-16 |
| Gate Staging enrichi (v5.2) | GO le 2026-06-19 |
| Gate 07A — Release Readiness | GO sous réserve le 2026-06-19 |
| Gate 09 — Production Readiness | GO sous réserve le 2026-06-19 |
| Gate 10 — Mise en production | GO le 2026-06-20 (`1.0.0` LIVE) |
| Gate Staging Patrimoine v5.3 | GO le 2026-06-23 (`docs/cgpa/07-devsecops/gate-staging-patrimoine-v5.3-decision.md`) |
| Gate Production v5.3 `1.1.0` | GO sous réserve le 2026-06-23 (`1.1.0` `PRODUCTION_DEPLOYED`) |

Aucun de ces Gates n'est rejoué ni supprimé. La migration v5.4 est strictement additive.

## 4. Décisions et risques déjà enregistrés (conservés)

- Décisions D-RM-01 à D-RM-04 (`docs/project-state.md` §15) — conservées, non rejouées.
- Risques RSV-RM-01 à RSV-RM-04 — conservés ; statuts inchangés par cet audit.
- Risques historiques v5.2 (R-V52-x) et v5.0.1 (R-1 à R-4) — conservés en l'état.

## 5. Epics et Sprints

Référence : `docs/cgpa/migration/epics-migration-report.md` et `sprints-migration-report.md`
(v5.3, conservés). Aucun Sprint actif au moment de cet audit ; Sprint 3 Patrimoine non démarré.
L'audit v5.4 ne change pas l'état fonctionnel des lots, mais conditionne leur **prochaine**
promotion Staging au contrôle `STG-ISOL-01` (cf. §8).

## 6. Pipelines CI/CD

`.github/workflows/ci.yml` et `codeql.yml` inchangés depuis l'audit v5.3. Le pipeline ne déploie
jamais directement sur l'hôte Staging/Production (CD manuel gouverné par Gate) : packaging et
push GHCR uniquement, avec tag immuable `sha-<8>`. Aucune commande Docker globale détectée dans
le pipeline (`docker build`/`docker push` ciblés par nom d'image, aucun `docker compose down`,
aucun `docker stop $(docker ps -q)`, aucun `docker system prune`).

## 7. Environnements

| Environnement | État |
|---------------|------|
| Development | `docker-compose.yml` (`name: loyertracker`), réseau/volume locaux, sans enjeu de mutualisation |
| Test | CI GitHub Actions, éphémère, isolé par runner |
| Staging | **Hôte mutualisé** `ai-test-server` (IP privée `172.31.11.102`), héberge plusieurs projets (LoyerTracker, « loyerpro », « outils labo » / `innovtech-ai-lab-sg`) derrière un reverse proxy partagé (nginx-proxy-manager) |
| Production | Hôte dédié `loyertracker-prod-server` (EC2 dédié), **hors périmètre de mutualisation** — aucun autre projet hébergé, le risque `STG-ISOL-01` ne s'y applique pas |

L'environnement concerné par CGPA v5.4 est exclusivement le **Staging mutualisé**.

## 8. Architecture Docker, stacks, réseaux, volumes, reverse proxy (analyse STG-ISOL-01)

| Élément | Constat | Conformité v5.4 |
|---------|---------|------------------|
| Nom de projet Compose | `name: loyertracker-staging` déclaré explicitement en tête de `docker-compose.staging.yml` (ligne 1) | ✅ D-STG-02 |
| Réseau Docker | `loyertracker-net` (bridge), namespacé par Compose en `loyertracker-staging_loyertracker-net` — distinct de tout autre projet hébergé sur le même hôte | ✅ |
| Volumes | `postgres-data`, namespacé en `loyertracker-staging_postgres-data` — distinct par construction | ✅ |
| Variables d'environnement / secrets | `.env` dédié sur l'hôte, hors dépôt, jamais partagé avec un autre projet (`KEYCLOAK_*`, `DB_*`, `LOYERTRACKER_TAG`) | ✅ |
| Ports hôte | Conteneurs internes (`api`, `keycloak`, `postgres`) **non publiés** ; seul `nginx` publie `WEB_HTTP_PORT`/`WEB_HTTPS_PORT` (paramétrables, défaut `18080`/`18443` documentés sur l'hôte réel) — aucun conflit avec le reverse proxy mutualisé (80/443) ni avec d'autres projets | ✅ |
| Reverse proxy | nginx-proxy-manager (mutualisé, hors dépôt) route `https://loyertracker.staging.loyerpro.org` vers l'IP privée de l'hôte sur le port alternatif du Nginx du projet — publication par nom DNS, pas d'exposition directe d'un port applicatif | ✅ |
| Commandes de déploiement | Runbook (`docs/cgpa/07-devsecops/runbook-exploitation.md` §2) : toutes les commandes ciblent explicitement `-f docker-compose.staging.yml` (résout le projet `loyertracker-staging` via le `name:` du fichier) ; aucune commande globale (`docker stop $(docker ps -q)`, `docker compose down` sans fichier, `docker system prune -a`) trouvée dans le runbook ni dans la CI | ✅ |
| Pipeline CI/CD | Aucune étape de déploiement automatique vers Staging ; packaging/push GHCR uniquement, sans commande Docker globale | ✅ |
| Preuve `STG-ISOL-01` automatisée | Absente : la preuve d'isolation est aujourd'hui **documentaire** (`docs/staging-state.md` §1, historique des redéploiements §8) et non produite par un script ou une étape CI dédiée | ⚠️ Écart mineur |
| Inventaire formel des ressources mutualisées (reverse proxy, hôte, autres projets) | Partiel : `docs/staging-state.md` §1 mentionne « loyerpro, outils labo » et l'accès `innovtech-ai-lab-sg` (§8, 2026-06-24), mais sans inventaire structuré dédié (propriétaire, ressources, conditions) | ⚠️ Écart mineur (D-STG-04) |
| Validation `STG-ISOL-01` en conditions réelles (hôte réel, ≥ 2 projets) | Non encore exécutée formellement sous la forme du contrôle v5.4 (la coexistence est démontrée de facto depuis 2026-06-14 sans incident, mais pas tracée comme un contrôle `STG-ISOL-01` PASS/FAIL explicite avant cet audit) | ⚠️ Réserve — traitée par le Gate dédié (§10) |

**Conclusion d'architecture** : l'isolation requise par CGPA v5.4 (namespace, réseau, volumes,
variables, ports, reverse proxy) est **déjà en place de façon native** depuis le lot Production
Readiness 4b (2026-06-14) — elle n'a simplement pas encore été formalisée sous le vocabulaire et
le contrôle `STG-ISOL-01`. La migration v5.4 est donc majoritairement une **formalisation
documentaire d'un état technique déjà conforme**, complétée par deux actions correctives
mineures (inventaire des ressources mutualisées, preuve automatisable).

## 9. Écarts avec CGPA v5.4

| Écart | Impact | Traitement |
|-------|--------|------------|
| `framework.current_version` encore à `5.3` | Traçabilité v5.4 absente | Mise à jour additive vers `5.4` |
| Contrôle `STG-ISOL-01` non formalisé (Gate, checklist, décision) | Gate Staging incomplet au regard de v5.4 | Création checklist + décision de Gate dédiées (§10) |
| Workflow `staging-isolation-workflow.md` absent | Contrôle d'isolation non outillé en tant que workflow | Création |
| Décisions D-STG-01 à D-STG-05 absentes | Politique Staging partagé non tracée | Ajout au registre v5.4 |
| Risque RSV-STG-01 absent | Risque résiduel (preuve live/CI automatisée) non tracé | Ajout au registre v5.4 |
| ADR-STG-001 absent | Décision d'architecture sur l'isolation non formalisée | Création (obligatoire, ÉTAPE 10) |
| Inventaire des ressources Staging mutualisées non structuré | D-STG-04 partiellement satisfaite | Inventaire ajouté à `staging-state.md` §11 |
| Responsabilités agents (Release Manager, DevSecOps Lead, Governance Officer, Enterprise Architect) non mises à jour pour l'isolation | Gouvernance v5.4 incomplète | Mise à jour des fiches agents |

## 10. Risques de migration

| Risque | Niveau | Mitigation |
|--------|--------|------------|
| Confusion entre conformité *structurelle* (déjà acquise) et validation *live* `STG-ISOL-01` (à exécuter formellement) | Moyen | Gate `STG-ISOL-01` statué **PASS sur preuves documentaires et historiques** avec réserve explicite de confirmation live au prochain déploiement (RSV-STG-01) |
| Sur-interprétation de l'obligation v5.4 comme nécessitant un changement d'infrastructure | Faible | Audit démontre que l'architecture existante satisfait déjà les exigences ; aucune migration technique requise |
| Perte d'historique par réécriture des rapports de migration v5.3 | Élevé si non maîtrisé | Tous les livrables v5.4 sont produits sous des noms de fichiers distincts (`*-v5.4.md`) ; aucun fichier v5.3 n'est modifié en profondeur, seules des sections additives sont ajoutées aux documents vivants (`project-state.md`, `staging-state.md`) |
| Application rétroactive de `STG-ISOL-01` aux déploiements Staging passés (2026-06-14 → 2026-06-24) | Faible | Écart accepté : ces déploiements sont antérieurs à v5.4 et ont démontré l'isolation de fait sans incident ; non rejoués |

## 11. Recommandation

**GO sous réserve** pour la migration CGPA v5.4.

Réserves non bloquantes :

- Exécuter une confirmation **live** de `STG-ISOL-01` (inspection `docker ps` / `docker network ls`
  / `docker volume ls` sur `ai-test-server`, en regard des autres projets hébergés) au prochain
  déploiement Staging réel (RSV-STG-01).
- Compléter l'inventaire structuré des ressources Staging mutualisées (propriétaire, ressources,
  conditions d'usage) dans `docs/staging-state.md` §11.
- Évaluer l'opportunité d'une preuve `STG-ISOL-01` automatisée (script ou étape CI) lors d'une
  prochaine évolution du pipeline, sans bloquer la présente migration documentaire.
