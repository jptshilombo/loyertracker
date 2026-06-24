# Plan détaillé — Étape 2 : Hypercare Production 24 heures `1.1.1`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-24 |
| Statut | **Exécution en cours — T0 PASS sous surveillance** |
| Release surveillée | `1.1.1` |
| Tag attendu | `sha-0adc4941` |
| Rollback disponible | `sha-05424aa3` |
| Dossier de clôture | `docs/cgpa/09-production/cloture-release-v1.1.1.md` |
| Durée | 24 heures à partir du T0 réel |

## 1. Objectif

Observer la Production pendant 24 heures sans mutation afin de confirmer la stabilité de la release
`1.1.1`, puis statuer sur sa clôture opérationnelle.

L'hypercare comprend trois snapshots datés : T0, T+12 h et T+24 h. Elle ne rejoue pas le smoke
métier et ne crée aucune donnée de test.

## 2. Autorisation et responsabilités

La validation du présent plan autorise uniquement :

- les connexions SSH en lecture à l'hôte Production dédié ;
- les requêtes de santé, métriques, logs et inventaire décrites ci-dessous ;
- la mise à jour documentaire des observations ;
- l'attente jusqu'aux checkpoints T+12 h et T+24 h.

Rôles :

| Rôle | Responsabilité |
|---|---|
| DevSecOps Lead | Collecte les preuves techniques et qualifie les alertes. |
| Release Manager | Valide la continuité de service et le respect de la fenêtre. |
| Governance Officer | Vérifie la traçabilité T0/T+12/T+24 et les risques. |
| Chief Delivery Officer | Statue `CLÔTURÉE`, `SOUS SURVEILLANCE` ou `INCIDENT`. |

## 3. Périmètre strict

### Autorisé

- Utiliser l'IP privée Production depuis le serveur du même VPC.
- Cibler explicitement `docker-compose.yml`, `docker-compose.prod.yml` et l'overlay monitoring.
- Lire l'état des conteneurs, images, métriques, alertes et logs récents.
- Lire uniquement la variable `LOYERTRACKER_TAG`, sans afficher `.env`.
- Interroger les tables Flyway en lecture seule.
- Lire la présence et l'âge du heartbeat de sauvegarde.

### Interdit

- `docker compose pull`, `up`, `down`, `stop`, `restart`, `rm` ou `exec` avec écriture.
- Toute commande Docker globale ou destructive.
- Modification de `.env`, des services, du réseau, du Security Group ou de Keycloak.
- Requête métier créant, modifiant ou supprimant une donnée.
- Smoke complet, restauration, backup manuel ou changement de tag.
- Correction CORS, déploiement Staging ou démarrage Sprint 3.
- Lecture ou affichage de secrets, tokens, mots de passe ou contenu des backups.

## 4. Horloge et cadence

1. Le premier snapshot validé fixe `T0` en UTC.
2. `T+12 h` est exécuté entre T0+11 h 30 et T0+12 h 30.
3. `T+24 h` est exécuté entre T0+23 h 30 et T0+24 h 30.
4. Les heures absolues calculées à T0 sont inscrites immédiatement dans le dossier de clôture.
5. Un checkpoint manqué de plus de 30 minutes suspend la clôture ; le CDO décide d'étendre la
   fenêtre ou de recommencer l'hypercare. Aucun résultat n'est rétroactivement inventé.

La session peut être reprise entre les checkpoints. Le dossier de clôture constitue la source de
vérité de l'horloge et des observations.

## 5. Préflight T0

Avant tout accès :

- vérifier que l'Étape 1 est PASS et que le dossier de clôture existe ;
- vérifier que le worktree documentaire ne contient que les fichiers attendus ;
- confirmer l'hôte, l'IP privée et la clé SSH depuis l'inventaire sécurisé hors dépôt ;
- confirmer le contexte Compose Production ;
- enregistrer la date UTC T0 et calculer les échéances T+12/T+24.

Critère d'arrêt : identité d'hôte ambiguë, accès nécessitant une modification réseau, ou baseline
Production différente de `1.1.1` sans décision documentée.

## 6. Contrôles à chaque checkpoint

Les mêmes contrôles sont exécutés à T0, T+12 h et T+24 h afin de rendre les snapshots comparables.

### 6.1 Identité et immutabilité

- hostname et date UTC ;
- commit du dépôt hôte et état du worktree ;
- valeur unique de `LOYERTRACKER_TAG` : `sha-0adc4941` ;
- images et digests actifs API/Web conformes au rapport final ;
- IDs des conteneurs et restart counts.

PASS : tag/digests conformes, aucun changement inexpliqué, restart count inchangé à 0.

### 6.2 Santé applicative

- `docker compose ... ps` ciblé : API, Web, PostgreSQL et Keycloak healthy ;
- API Actuator `UP` via le réseau interne ;
- `/healthz` public HTTP 200 ;
- racine et assets Web critiques HTTP 200 ;
- endpoint Prometheus public toujours bloqué en HTTP 404 ;
- PostgreSQL prêt et Keycloak ready.

Aucune authentification utilisateur ou écriture métier n'est exécutée.

### 6.3 Base de données et Flyway

- vérifier PostgreSQL en lecture seule ;
- confirmer 14 migrations Flyway réussies, rang maximal 14 ;
- confirmer l'absence de migration failed/pending inattendue ;
- relever les métriques Hikari : connexions actives, idle, max et pending.

Seuil bloquant : `hikaricp_connections_pending > 0` pendant 5 minutes ou migration non réussie.

### 6.4 Observabilité

Interroger Prometheus/Alertmanager sur le réseau interne :

- cinq cibles attendues `up` ;
- aucune alerte `critical` active ;
- aucune alerte `warning` inexpliquée persistante ;
- ratio API 5xx sur 5 minutes ;
- latence API p99 sur 5 minutes ;
- taux de 401 sur 5 minutes ;
- état des sondes PostgreSQL et Keycloak ;
- âge du dernier batch applicatif et du heartbeat backup.

Seuils existants :

| Signal | Seuil de suspension |
|---|---|
| API indisponible | `up == 0` pendant 2 min |
| Erreurs API | ratio 5xx > 5 % pendant 5 min |
| Latence | p99 > 2 s pendant 5 min |
| Authentification | plus de 1 réponse 401/s pendant 5 min, à qualifier |
| PostgreSQL / Keycloak | sonde KO pendant 2 min |
| Pool JDBC | pending > 0 pendant 5 min |
| Batch | dernier succès > 26 h |
| Backup | heartbeat absent ou dernier succès > 26 h |

Une alerte warning ponctuelle peut être acceptée uniquement si sa cause et sa résolution sont
tracées. Toute alerte critical active suspend la clôture.

### 6.5 Capacité hôte et conteneurs

- charge système et uptime ;
- mémoire disponible et swap ;
- espace disque et inodes ;
- consommation CPU/mémoire instantanée des conteneurs avec `docker stats --no-stream` ;
- absence de filesystem en lecture seule ou de pression OOM.

Seuils de suspension :

- disque libre < 20 % ;
- mémoire disponible < 15 % de manière persistante ou OOM détecté ;
- swap en croissance accompagnée d'une dégradation ;
- charge supérieure au nombre de vCPU pendant plus de 15 minutes ;
- conteneur proche de sa limite mémoire ou redémarré.

### 6.6 Logs depuis le checkpoint précédent

Lire uniquement les logs API/Web, PostgreSQL et Keycloak sur la fenêtre écoulée :

- T0 : depuis la validation finale du déploiement ;
- T+12 : depuis T0 ;
- T+24 : depuis T+12.

Rechercher sans afficher de PII ou secret :

- `ERROR`, `FATAL`, panic, OOM, migration failure ;
- erreurs 5xx répétées ;
- refus d'authentification anormaux ;
- erreurs de connexion PostgreSQL/Keycloak ;
- boucle de redémarrage.

Les occurrences sont comptées et résumées ; les lignes contenant des données sensibles ne sont
pas copiées dans les documents.

## 7. Verdict par checkpoint

Chaque snapshot reçoit un verdict :

- **PASS** : tous les contrôles conformes, aucune anomalie non expliquée ;
- **PASS sous surveillance** : warning compris, résolu ou non bloquant, avec suivi explicite ;
- **FAIL** : seuil critique, dérive d'artefact, indisponibilité ou besoin de mutation.

Un `FAIL` :

1. arrête l'hypercare normale ;
2. marque la clôture `SUSPENDUE — INCIDENT` ;
3. interdit toute correction immédiate non planifiée ;
4. déclenche un diagnostic et un plan correctif/rollback distinct ;
5. conserve `PRODUCTION_DEPLOYED` tant qu'aucun rollback n'est décidé et exécuté.

## 8. Clôture à T+24

La release peut être déclarée **CLÔTURÉE** uniquement si :

- T0, T+12 et T+24 sont exécutés dans les fenêtres prévues ;
- aucun checkpoint n'est FAIL ;
- aucun incident critique n'est ouvert ;
- artefact, santé, Flyway, monitoring, backup et capacité restent conformes ;
- toute alerte warning est expliquée et acceptée ;
- `docs/cgpa/09-production/cloture-release-v1.1.1.md` est entièrement renseigné.

Décisions possibles du CDO :

- **GO — RELEASE CLÔTURÉE** ;
- **GO SOUS RÉSERVE — SURVEILLANCE PROLONGÉE**, avec durée et motif explicites ;
- **NO GO CLÔTURE — INCIDENT**, avec plan séparé obligatoire.

## 9. Traçabilité documentaire

À chaque checkpoint :

- renseigner uniquement la colonne correspondante du dossier de clôture ;
- ajouter les heures UTC et le verdict ;
- conserver les valeurs précédentes ;
- ne jamais supprimer une anomalie, une décision ou un risque historique.

À T+24 :

- inscrire la décision finale dans le dossier de clôture ;
- mettre à jour `docs/project-state.md`, `docs/prod-state.md` et `CHANGELOG.md` ;
- maintenir `RSV-STG-01` et la dette CORS ouvertes ;
- exécuter `git diff --check` ;
- produire la liste des fichiers modifiés et des écarts observés.

## 10. Sortie et point de contrôle suivant

Après décision **GO — RELEASE CLÔTURÉE**, l'unique étape suivante autorisée est la production du
plan détaillé du lot correctif CORS Compose.

Le présent plan n'autorise ni la correction CORS, ni un déploiement Staging, ni une promotion
Production supplémentaire.
