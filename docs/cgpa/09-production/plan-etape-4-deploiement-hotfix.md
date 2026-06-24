# Plan détaillé — Étape 4 : Déploiement Production du Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Statut | **Exécutée le 2026-06-24 — déploiement technique PASS** |
| Version | `1.1.1` |
| Candidat | API/Web `sha-0adc4941` |
| Production actuelle | API/Web `sha-05424aa3` |
| Gate | GO sous réserve acceptée — `PRODUCTION_READY` |
| Préflight | PASS |
| Backup | `loyertracker-20260624-140441.dump`, vérifié |

## 1. Objectif

Déployer techniquement les images API et Web `sha-0adc4941` sur l'hôte Production dédié, puis
effectuer uniquement les contrôles de santé immédiats.

Cette étape ne réalise pas le smoke métier complet, ne crée pas de données de test et ne marque
pas `PRODUCTION_DEPLOYED`. Ces opérations relèvent de l'Étape 5.

## 2. Autorisation requise

L'exécution est interdite avant validation explicite du présent plan par le PO.

La validation autorise :

- connexion SSH à l'hôte Production ;
- pull ciblé des images API et Web candidates ;
- recréation ciblée des services `api` et `nginx` ;
- contrôles techniques immédiats ;
- rollback immédiat vers `sha-05424aa3` si un critère d'arrêt est rencontré ;
- mise à jour documentaire du résultat.

## 3. Invariants

- Utiliser l'IP privée `172.31.22.90`.
- Travailler dans `~/loyertracker`.
- Utiliser explicitement
  `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml`.
- Utiliser une surcharge de processus :
  `LOYERTRACKER_TAG=sha-0adc4941`.
- Ne pas modifier `.env`.
- Ne pas exécuter `git pull`.
- Ne jamais utiliser `latest`.
- Ne jamais construire une image localement.
- Ne pas recréer PostgreSQL, Keycloak ou les services de monitoring.
- Ne pas exécuter de commande Docker globale.

## 4. Contrôle juste avant mutation

Reconfirmer :

- hostname Production ;
- Gate `PRODUCTION_READY` et backup vérifié ;
- 4/4 services applicatifs healthy ;
- images API/Web actuelles `sha-05424aa3` ;
- Flyway V1→V14 ;
- aucune alerte critique active ;
- espace disque supérieur aux seuils du préflight ;
- production publique accessible.

Si l'état diffère matériellement du rapport de préflight, arrêter et revenir au point de
décision.

## 5. Capturer l'état de rollback

Avant le pull :

- relever les IDs, images, digests, dates de démarrage et restart counts des services ;
- confirmer les digests de `sha-05424aa3` ;
- confirmer l'existence du backup et son checksum sans relire son contenu ;
- conserver les commandes de rollback prêtes.

## 6. Tirer et vérifier le candidat

Commande ciblée prévue :

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
LOYERTRACKER_TAG=sha-0adc4941 \
docker compose pull api nginx
```

Après le pull :

- vérifier que l'image API correspond au digest
  `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016` ;
- vérifier que l'image Web correspond au digest
  `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` ;
- confirmer qu'aucune autre image n'a été tirée ou reconstruite.

Un digest différent impose l'arrêt avant recréation.

## 7. Déployer API et Web

Commande ciblée prévue :

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
LOYERTRACKER_TAG=sha-0adc4941 \
docker compose up -d --no-deps api nginx
```

Effets attendus :

- `api` recréée avec l'image candidate ;
- `nginx` recréé avec l'image candidate ;
- `postgres`, `keycloak`, monitoring et volumes inchangés ;
- aucune migration V15+.

La fenêtre de perturbation doit être limitée au redémarrage des deux services.

## 8. Contrôles immédiats

Attendre la stabilisation, puis vérifier :

- `api` et `nginx` healthy ;
- `postgres` et `keycloak` toujours healthy et non recréés ;
- restart counts stables ;
- images API/Web = `sha-0adc4941` avec les digests attendus ;
- PostgreSQL prêt ;
- Flyway = 14 migrations réussies, rang maximal 14 ;
- issuer Keycloak inchangé ;
- `/healthz` public accessible ;
- racine publique accessible ;
- Prometheus public toujours 404 ;
- cinq cibles Prometheus `up` après le délai de scrape ;
- aucune alerte critique nouvelle ;
- logs de démarrage sans erreur critique.

Ces contrôles sont techniques et ne remplacent pas le smoke de l'Étape 5.

## 9. Critères d'arrêt et rollback immédiat

Rollback obligatoire si :

- digest candidat incorrect ;
- `api` ou `nginx` unhealthy après le délai défini ;
- PostgreSQL ou Keycloak perturbé ;
- Flyway différent de V1→V14 ;
- issuer incorrect ;
- endpoint public indisponible ;
- alerte critique nouvelle liée au déploiement ;
- erreur critique d'authentification, base ou démarrage dans les logs.

## 10. Procédure de rollback autorisée

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
LOYERTRACKER_TAG=sha-05424aa3 \
docker compose pull api nginx

COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
LOYERTRACKER_TAG=sha-05424aa3 \
docker compose up -d --no-deps api nginx
```

Après rollback :

- vérifier API/Web healthy ;
- confirmer les digests précédents ;
- vérifier Flyway V1→V14 ;
- vérifier le point d'entrée public ;
- consigner l'échec et interdire l'Étape 5.

Aucune restauration de base n'est attendue, car le candidat n'introduit aucune migration ni
transformation de données. Le backup reste le filet de sécurité ultime.

## 11. Mutations autorisées

- téléchargement des deux images candidates ;
- recréation d'`api` et `nginx` ;
- éventuel rollback ciblé de ces deux services ;
- écritures normales de logs et métriques ;
- mise à jour documentaire du résultat.

Toute autre mutation est interdite.

## 12. Sorties attendues

### Déploiement technique PASS

- API/Web exécutent `sha-0adc4941` ;
- contrôles techniques immédiats verts ;
- PostgreSQL, Keycloak, monitoring et données inchangés ;
- rapport d'exécution créé ;
- `PRODUCTION_DEPLOYED` reste non atteint ;
- autorise uniquement la production du plan détaillé de l'Étape 5.

### Déploiement technique FAIL

- rollback vers `sha-05424aa3` exécuté et vérifié ;
- incident documenté ;
- Étape 5 interdite ;
- nouveau candidat ou nouveau plan requis.

## 13. Preuves à consigner

- heure UTC de début et de fin ;
- IDs et digests avant/après ;
- services recréés et services restés inchangés ;
- résultats des healthchecks ;
- état Flyway ;
- état Prometheus/Alertmanager ;
- alertes éventuelles ;
- résultat PASS ou FAIL ;
- confirmation qu'aucun smoke métier et aucune donnée de test n'ont été exécutés.

## 14. Résultat d’exécution

Rapport : `docs/cgpa/09-production/deploiement-technique-v1.1.1-report.md`.

- API et Web `sha-0adc4941` déployés avec digests conformes.
- PostgreSQL, Keycloak et monitoring non recréés.
- Flyway 14/14, cinq cibles Prometheus up, aucune alerte.
- Aucun smoke métier exécuté.
- `.env` inchangé ; persistance du tag à décider à l’Étape 5.
- `PRODUCTION_DEPLOYED` non atteint.

## 15. Prochaine action autorisée

Produire le plan détaillé de l’Étape 5 — validation finale ou rollback.
