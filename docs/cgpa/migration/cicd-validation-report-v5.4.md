# Rapport de validation CI/CD — CGPA v5.4

> Fichier versionné distinct de `docs/cgpa/migration/cicd-validation-report.md` (v5.3, conservé
> tel quel). Ajoute la validation de l'isolation Docker, des réseaux/volumes dédiés, du reverse
> proxy et du contrôle `STG-ISOL-01`.

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-24 |
| Projet | LoyerTracker |
| Décision | **GO sous réserve** |

## GitHub Actions (inchangé depuis v5.3)

Workflows détectés : `.github/workflows/ci.yml`, `.github/workflows/codeql.yml`. Build, tests,
SonarQube, SAST/SCA, scans d'images et packaging GHCR conformes (cf.
`docs/cgpa/migration/cicd-validation-report.md`).

## Contrôles spécifiques v5.4

| Domaine | État | Preuve |
|---------|------|--------|
| Isolation Docker — nom de projet | Conforme | `name: loyertracker-staging` (`docker-compose.staging.yml` l.1), distinct de `loyertracker` (dev/prod) |
| Réseaux dédiés | Conforme | `loyertracker-net` déclaré dans le Compose du projet, namespacé `loyertracker-staging_loyertracker-net` par Docker Compose |
| Volumes dédiés | Conforme | `postgres-data` déclaré dans le Compose du projet, namespacé `loyertracker-staging_postgres-data` |
| Variables/secrets séparés | Conforme | `.env` dédié hors dépôt sur `ai-test-server`, jamais partagé avec un autre projet |
| Reverse Proxy | Conforme | nginx-proxy-manager (mutualisé) route par nom DNS (`loyertracker.staging.loyerpro.org`) vers le port alternatif du Nginx du projet ; aucune exposition directe et durable d'un port applicatif |
| Absence de conflit de ports | Conforme | Services internes non publiés ; `nginx` publie un port paramétrable (`WEB_HTTP_PORT`/`WEB_HTTPS_PORT`, alternatifs `18080`/`18443` sur l'hôte réel) |
| Absence de commande Docker globale | Conforme | Recherche effectuée dans `runbook-exploitation.md` et `.github/workflows/*.yml` : aucune occurrence de `docker stop $(docker ps -q)`, `docker compose down` sans cible, ni `docker system prune -a` |
| Pipeline limité aux ressources du projet | Conforme | `docker build`/`docker push` ciblent uniquement `loyertracker-api`/`loyertracker-web` ; aucune étape CI ne déploie sur l'hôte Staging mutualisé (déploiement manuel gouverné par runbook) |
| Preuve `STG-ISOL-01` | **PASS** (documentaire + historique opérationnel) | `docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md` |
| Rollback ciblé documenté | Conforme | `docs/cgpa/07-devsecops/runbook-exploitation.md` §3 — redéploiement du tag immuable précédent, toujours via `-f docker-compose.staging.yml` |

## Environnements

| Environnement | Validation v5.4 |
|---------------|------------------|
| Development | Hors périmètre mutualisation — `name: loyertracker`, hôte local développeur |
| Test (CI) | Éphémère par runner GitHub Actions — aucun risque d'inter-projets |
| Staging | **Mutualisé** (`ai-test-server`) — isolation namespace/réseau/volume/ports/reverse proxy conforme ; `STG-ISOL-01` PASS |
| Production | Hôte dédié (`loyertracker-prod-server`) — hors périmètre `STG-ISOL-01` (aucun autre projet hébergé) |

## Écarts

| Écart | Impact | Action |
|-------|--------|--------|
| Confirmation `STG-ISOL-01` non encore exécutée en conditions *live* (accès direct à l'hôte au moment du contrôle) | Résidu de preuve, non bloquant | RSV-STG-01 — à lever au prochain déploiement Staging réel |
| Aucune étape CI dédiée ne produit automatiquement la preuve `STG-ISOL-01` | Amélioration continue | À évaluer pour une prochaine évolution du pipeline (hors blocage de la présente migration) |
| Inventaire structuré des ressources Staging mutualisées incomplet avant cette migration | Mineur gouvernance | Complété par `docs/staging-state.md` §11 (cette migration) |
| Écarts v5.3 résiduels (drill rollback Production non rejoué sur release ultérieure, CD manuel jusqu'à Production) | Inchangés, hors périmètre v5.4 | Suivi par RSV-RM-03 |

## Décision

**GO sous réserve**. La chaîne CI/CD et l'architecture Docker Staging satisfont les exigences
CGPA v5.4 (isolation namespace/réseau/volume/ports/reverse proxy, absence de commande globale,
`STG-ISOL-01` PASS sur preuves documentaires). La réserve porte sur la confirmation live de
l'isolation au prochain déploiement réel, non sur la conformité de l'architecture actuelle.
