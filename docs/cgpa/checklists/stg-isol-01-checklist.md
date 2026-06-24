# Checklist — STG-ISOL-01 (Isolation du déploiement Staging, CGPA v5.4.1)

> Référentiel : `/home/ubuntu/setup-cgpa/docs/cgpa/CGPA-v5.4.1.md`. ADR canonique :
> `docs/cgpa/adr/ADR-STG-001-staging-isolation.md`. Contrôle bloquant intégré au
> Gate Staging (`docs/cgpa/checklists/gate-staging-checklist.md`). Décision associée :
> `docs/cgpa/07-devsecops/gate-stg-isol-01-decision.md`.


## Identification de la preuve

| Champ | Valeur |
|---|---|
| Projet / version | LoyerTracker / prochain artefact candidat |
| Environnement | `ai-test-server` — Staging mutualisé |
| Auditeur technique | DevSecOps Lead |
| Validateur final | Release Manager |
| Date d’exécution | À renseigner à chaque déploiement |
| Référence des preuves | À renseigner dans la décision de Gate courante |

## Objectif

Vérifier qu'un déploiement Staging de LoyerTracker sur l'hôte mutualisé `ai-test-server` ne
perturbe aucun autre projet hébergé sur le même serveur.

## Namespace Docker

- [ ] Le fichier Compose déployé déclare un nom de projet explicite et unique (`name:` en tête de
  fichier, ou `-p <nom>` / `COMPOSE_PROJECT_NAME` explicite à l'invocation).
- [ ] Le nom de projet ne collisionne avec aucun autre projet hébergé sur l'hôte.
- [ ] Les conteneurs produits sont préfixés par ce nom de projet (`docker ps` → préfixe
  `loyertracker-staging-*`).

## Réseaux dédiés

- [ ] Le réseau Docker utilisé est déclaré dans le fichier Compose du projet (non externe partagé,
  sauf ressource mutualisée explicitement inventoriée — cf. §Ressources partagées).
- [ ] Le réseau résultant est namespacé par le nom de projet (`docker network ls` →
  `loyertracker-staging_<reseau>`).
- [ ] Aucun service du projet ne rejoint le réseau d'un autre projet hébergé.

## Volumes dédiés

- [ ] Les volumes persistants (données PostgreSQL notamment) sont déclarés dans le fichier Compose
  du projet, sans nom externe partagé non inventorié.
- [ ] Les volumes résultants sont namespacés par le nom de projet (`docker volume ls` →
  `loyertracker-staging_<volume>`).
- [ ] Aucune commande de déploiement ne cible un volume appartenant à un autre projet.

## Reverse proxy

- [ ] L'application est publiée par **nom DNS** via le reverse proxy mutualisé (nginx-proxy-manager
  ou équivalent), jamais par exposition directe et durable d'un port applicatif dédié.
- [ ] La configuration du reverse proxy mutualisé pour ce projet est identifiée et ne modifie pas
  la configuration des autres projets.
- [ ] Le certificat TLS et l'Access List applicables au projet sont identifiés.

## Absence de conflits de ports

- [ ] Les ports publiés sur l'hôte par le projet sont listés et vérifiés sans collision avec un
  autre projet hébergé (ports alternatifs documentés dans `.env` hors dépôt si l'hôte est partagé).
- [ ] Les services internes (non destinés à un accès direct) ne publient aucun port sur l'hôte.

## Absence d'impact sur les autres projets

- [ ] Aucune commande de déploiement, de rollback ou de nettoyage n'agit sur un périmètre plus
  large que le projet (recherche explicite de commandes interdites : `docker stop $(docker ps -q)`,
  `docker compose down` sans fichier/projet ciblé, `docker system prune -a`, ou équivalent).
- [ ] Les conteneurs, réseaux et volumes des autres projets restent à l'état `running`/présents
  avant et après le déploiement (vérification `docker ps` / `docker network ls` / `docker volume
  ls` avant/après).
- [ ] Les ressources partagées intentionnellement (reverse proxy, hôte, registre d'images) sont
  inventoriées avec leur propriétaire et leurs conditions d'utilisation
  (`docs/staging-state.md` §11).
- [ ] Le pipeline CI/CD du projet ne contient aucune commande Docker globale et ne cible que les
  ressources du projet (image, tag, nom de projet Compose).

## Décision

- [ ] Verdict : **PASS** ou **FAIL**.
- [ ] Si `FAIL` : décision **NO GO** au Gate Staging, action corrective datée et assignée avant
  nouvelle tentative.
- [ ] Si exception acceptée malgré un écart : décision explicite et motivée du Release Manager,
  datée, inscrite au registre des décisions (`docs/project-state.md`).
- [ ] Référence de la décision de Gate associée renseignée.
