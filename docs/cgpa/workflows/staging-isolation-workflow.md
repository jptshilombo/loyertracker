# Workflow — Contrôle d’isolation Staging (STG-ISOL-01, CGPA v5.4.1)

> Workflow ajouté par CGPA v5.4 et normalisé par **CGPA v5.4.1**. ADR canonique :
> `docs/cgpa/adr/ADR-STG-001-staging-isolation.md`. S'applique à tout
> déploiement Staging de LoyerTracker, l'hôte `ai-test-server` étant **mutualisé** (plusieurs
> projets hébergés derrière un reverse proxy partagé). Intégré comme étape obligatoire du workflow
> Sprint → Staging (`docs/cgpa/workflows/staging-deployment-workflow.md`, étape 7).

## Objectif

Garantir qu'un déploiement Staging de LoyerTracker ne stoppe, ne supprime et ne perturbe aucun
conteneur, réseau, volume ou service appartenant à un autre projet hébergé sur le même serveur.

## Déclencheur

Tout déploiement, redéploiement ou rollback sur l'environnement Staging réel
(`docker-compose.staging.yml` sur `ai-test-server`), avant l'exécution du Gate Staging.

## Principe de gouvernance

Le déploiement d'un projet ne doit jamais arrêter, supprimer ou perturber les conteneurs,
réseaux, volumes ou services d'un autre projet présent sur le même serveur Staging. Toute
exception exige une décision explicite du Release Manager, une analyse d'impact, un rollback
documenté et une inscription au registre des décisions (`docs/project-state.md`).

## Séquence

1. **Identifier le nom de projet Compose** utilisé (`name: loyertracker-staging`,
   `docker-compose.staging.yml` ligne 1) et confirmer son unicité sur l'hôte.
2. **Identifier les ressources namespacées** par ce projet : réseau (`loyertracker-staging_loyertracker-net`),
   volume (`loyertracker-staging_postgres-data`), conteneurs (préfixe `loyertracker-staging-`).
3. **Identifier les ressources mutualisées intentionnelles** (reverse proxy nginx-proxy-manager,
   hôte lui-même, registre GHCR) et vérifier qu'elles sont inventoriées
   (`docs/staging-state.md` §11) avec propriétaire et conditions d'usage.
4. **Vérifier l'absence de commande Docker globale** dans la procédure de déploiement effective
   (runbook, scripts, CI) : aucune commande agissant au-delà du projet ciblé.

   Commandes conformes (ciblées) :

   ```bash
   docker compose -f docker-compose.staging.yml pull
   docker compose -f docker-compose.staging.yml up -d
   docker compose -f docker-compose.staging.yml ps
   ```

   Commandes interdites (portée globale, jamais utilisées sur cet hôte) :

   ```bash
   docker stop $(docker ps -q)
   docker compose down            # sans fichier ni nom de projet explicite
   docker system prune -a
   ```

5. **Vérifier l'absence de conflit de ports** : les services internes (`api`, `keycloak`,
   `postgres`) ne publient aucun port hôte ; seul `nginx` publie un port hôte paramétrable
   (`WEB_HTTP_PORT`/`WEB_HTTPS_PORT`), choisi pour ne pas entrer en conflit avec le reverse proxy
   mutualisé (80/443) ni avec un autre projet.
6. **Vérifier le routage reverse proxy** : l'application reste joignable uniquement par son nom
   DNS dédié (`https://loyertracker.staging.loyerpro.org`) via nginx-proxy-manager, sans
   modification de la configuration des autres projets.
7. **(Si accès opérationnel disponible) Confirmer en conditions réelles** : avant et après le
   déploiement, `docker ps` / `docker network ls` / `docker volume ls` sur l'hôte ne montrent
   aucune perturbation des ressources des autres projets.
8. **Consigner le résultat** : `PASS` ou `FAIL` dans `docs/cgpa/checklists/stg-isol-01-checklist.md`
   et dans la décision de Gate (`docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md` ou
   équivalent daté pour le déploiement courant).
9. **Statuer** : un `FAIL` impose `NO GO` au Gate Staging, sauf exception explicite, motivée,
   datée et inscrite au registre des décisions par le Release Manager (D-STG-05).

## Rôles mobilisés

| Rôle | Action |
|------|--------|
| DevSecOps Lead | Exécute le contrôle technique (réseaux, volumes, secrets, reverse proxy, conventions de nommage, pipeline). |
| Release Manager | Valide que le déploiement ne perturbe aucun autre projet ; refuse la promotion si `FAIL` non excepté. |
| Enterprise Architect | Vérifie la stratégie d'isolation, les ressources partagées et l'alignement Staging/Production. |
| Governance Officer | Audite la conformité documentaire du contrôle et la traçabilité des exceptions. |

## Sortie attendue

- Résultat `STG-ISOL-01` (`PASS`/`FAIL`) tracé et daté.
- `docs/staging-state.md` §11 (inventaire des ressources mutualisées) à jour.
- Décision de Gate Staging tenant compte du résultat `STG-ISOL-01`.
- Toute exception inscrite au registre des décisions (`docs/project-state.md`).
