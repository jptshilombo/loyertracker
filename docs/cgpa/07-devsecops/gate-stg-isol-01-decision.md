# Gate STG-ISOL-01 — Isolation du déploiement Staging · Décision (LoyerTracker)

> Gate ajouté par **CGPA v5.4** (`setup-cgpa/docs/cgpa/CGPA-v5.4.md` §4). Vérifie qu'un
> déploiement Staging sur l'hôte mutualisé `ai-test-server` n'arrête, ne supprime et ne perturbe
> aucun conteneur, réseau, volume ou service d'un autre projet hébergé sur le même serveur.
> Statué le **2026-06-24** dans le cadre de la migration CGPA v5.4
> (`docs/cgpa/migration/migration-report-v5.4.md`). Checklist associée :
> `docs/cgpa/checklists/stg-isol-01-checklist.md`.

## Contexte

`ai-test-server` (IP privée `172.31.11.102`) héberge LoyerTracker **et** au moins un autre
périmètre (« loyerpro », outils labo — Security Group `innovtech-ai-lab-sg`, cf.
`docs/staging-state.md` §1 et §8). C'est donc un Staging partagé au sens de CGPA v5.4, et le
contrôle `STG-ISOL-01` s'applique pleinement.

## Conditions d'entrée

- [x] Stack Staging identifiée — `docker-compose.staging.yml`, projet Compose `loyertracker-staging`.
- [x] Hôte mutualisé confirmé — `ai-test-server`, reverse proxy partagé `nginx-proxy-manager`.
- [x] Historique de déploiements Staging disponible (2026-06-14 → 2026-06-24) — `docs/staging-state.md` §8.

## Critères de validation (PASS)

| # | Critère STG-ISOL-01 | Statut | Preuve |
|---|---|---|---|
| 1 | N'arrête pas les conteneurs d'un autre projet | ✅ | Aucune commande globale (`docker stop $(docker ps -q)` ou équivalent) dans le runbook (`docs/cgpa/07-devsecops/runbook-exploitation.md` §2-3) ni dans la CI ; toutes les commandes ciblent `-f docker-compose.staging.yml` |
| 2 | N'écrase ni ne supprime les volumes d'un autre projet | ✅ | Volume `postgres-data` déclaré dans le fichier Compose du projet, namespacé `loyertracker-staging_postgres-data` par Compose ; aucune commande `docker volume rm`/`prune` globale détectée |
| 3 | N'utilise pas de ressources partagées non inventoriées et non maîtrisées | ✅ avec réserve | Seule ressource partagée intentionnelle : le reverse proxy mutualisé (nginx-proxy-manager) et l'hôte lui-même — désormais inventoriés en `docs/staging-state.md` §11 ; aucune autre ressource (réseau, volume, base) partagée détectée |
| 4 | Respecte les conventions de nommage Docker du projet | ✅ | Préfixe de projet `loyertracker-staging` appliqué à tous les conteneurs/réseaux/volumes via le champ `name:` du fichier Compose |
| 5 | Utilise un nom de projet Compose explicite | ✅ | `name: loyertracker-staging` (`docker-compose.staging.yml` ligne 1) — distinct de `loyertracker` (dev/prod, hôtes différents, jamais colocalisés) |

**Vérification ports/reverse proxy (alignement §3 CGPA v5.4)** : services internes (`api`,
`keycloak`, `postgres`) non publiés sur l'hôte ; seul `nginx` publie un port hôte, paramétrable
(`WEB_HTTP_PORT`/`WEB_HTTPS_PORT`, ports alternatifs `18080`/`18443` documentés hors dépôt) pour
éviter tout conflit avec le reverse proxy mutualisé (80/443) ou un autre projet. L'application est
publiée par nom DNS (`https://loyertracker.staging.loyerpro.org`) via nginx-proxy-manager — aucun
accès public direct à un port applicatif dédié.

**Preuve opérationnelle historique** : 6 redéploiements Staging tracés entre le 2026-06-14 et le
2026-06-24 (`docs/staging-state.md` §8) sur cet hôte mutualisé, sans aucun incident rapporté
affectant un autre projet hébergé, y compris lors de l'ouverture temporaire d'un accès SSH sur le
SG `innovtech-ai-lab-sg` (2026-06-24).

## Résultat du contrôle

**PASS.**

## Réserves

| # | Réserve | Périmètre | Échéance |
|---|---------|-----------|----------|
| RSV-STG-01 | Confirmation *live* du PASS (inspection directe `docker ps`/`docker network ls`/`docker volume ls` sur `ai-test-server`, en regard des autres projets hébergés au moment du contrôle) | Hors preuve documentaire actuelle, nécessite un accès opérationnel à l'hôte | Au prochain déploiement Staging réel |
| — | Preuve `STG-ISOL-01` non encore automatisée (script ou étape CI dédiée) | Amélioration continue, non bloquante | À évaluer lors d'une prochaine évolution du pipeline |

> Ces réserves sont **non bloquantes** : elles ne remettent pas en cause le verdict PASS, fondé
> sur les preuves de configuration (fichiers Compose, runbook) et sur l'historique opérationnel
> sans incident. Elles renforcent la preuve lors du prochain mouvement Staging.

## Sous-agents mobilisés

| Sous-agent | Avis |
|------------|------|
| DevSecOps Lead | **PASS** — namespace, réseaux, volumes, secrets et reverse proxy isolés ; aucune commande globale détectée |
| Release Manager | **GO sous réserve** — autorise la prochaine promotion Staging ; exige RSV-STG-01 au prochain déploiement réel |
| Enterprise Architect | **GO** — stratégie d'isolation et alignement Staging/Production cohérents avec ENV-01 |
| Governance Officer | **GO sous réserve** — conformité documentaire constatée ; suit la clôture de RSV-STG-01 |

## Décision

- Recommandation DevSecOps Lead : **✅ PASS**
- **Décision CGPA Chief Delivery Officer : ✅ PASS — Gate Staging non bloqué par `STG-ISOL-01`**
- Date & responsable : **2026-06-24** — PO **jptshilombo@gmail.com** (jordan)

> ✅ **Gate STG-ISOL-01 statué PASS.** Aucune action corrective bloquante. La prochaine promotion
> Sprint → Staging peut s'appuyer sur ce Gate, sous réserve non bloquante **RSV-STG-01**
> (confirmation live au prochain déploiement réel).

---
*Livrable CGPA v5.4 — Gate STG-ISOL-01 (Isolation du déploiement Staging). Réf. :
`setup-cgpa/docs/cgpa/CGPA-v5.4.md` §4. Voir aussi `docs/staging-state.md` §11 et
`docs/cgpa/05-architecture-conception/adr/ADR-STG-001-isolation-staging-partage.md`.*
