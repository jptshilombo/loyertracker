# Plan détaillé — Étape 5 : Validation finale ou rollback du Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Statut | **Exécutée le 2026-06-24 — PASS, `PRODUCTION_DEPLOYED`** |
| Version | `1.1.1` |
| Artefact actif | API/Web `sha-0adc4941` |
| Rollback | API/Web `sha-05424aa3` |
| Backup | `loyertracker-20260624-140441.dump`, vérifié |
| État CGPA | `PRODUCTION_READY`, `PRODUCTION_DEPLOYED` non atteint |

## 1. Objectif

Valider le Hotfix en conditions Production, nettoyer intégralement les échafaudages et données
de test, persister le tag candidat de manière contrôlée, puis décider :

- **succès** : `PRODUCTION_DEPLOYED` ;
- **échec** : rollback vers `sha-05424aa3` et décision NO GO.

## 2. Autorisation requise

L'exécution est interdite avant validation explicite du présent plan par le PO.

La validation autorise :

- les contrôles techniques finaux ;
- l'activation temporaire et strictement limitée des mécanismes de smoke ;
- l'exécution du smoke Production ;
- une vérification du flux critique patrimoine/bien ;
- le nettoyage des données et comptes de test créés pendant la validation ;
- la persistance atomique de `LOYERTRACKER_TAG=sha-0adc4941` après succès ;
- le rollback ciblé vers `sha-05424aa3` en cas d'échec ;
- la mise à jour documentaire et des statuts CGPA.

## 3. Principes de sécurité

- Utiliser uniquement l'IP privée de l'hôte Production.
- Ne jamais afficher ou versionner `.env` ou un secret.
- Ne jamais utiliser `latest`.
- Ne jamais exécuter de commande Docker globale.
- Ne jamais utiliser `docker compose down`, supprimer un volume ou restaurer la base sauf
  décision d'incident distincte.
- Capturer tous les identifiants de test afin de permettre un nettoyage ciblé.
- Ne supprimer aucune donnée qui ne porte pas la signature du run de validation.
- Conserver le backup pré-déploiement intact.

## 4. Contrôle d'entrée

Avant tout test :

- confirmer `api` et `nginx` sur `sha-0adc4941` avec les digests validés ;
- confirmer les quatre services applicatifs healthy ;
- confirmer Flyway V1→V14 ;
- confirmer cinq cibles Prometheus `up` et aucune alerte active ;
- confirmer Production publique accessible ;
- confirmer backup et rollback disponibles ;
- confirmer que `.env` référence encore `sha-05424aa3` tant que la validation n'est pas close.

Tout écart matériel impose un arrêt avant le smoke.

## 5. État de référence avant test

Capturer sans données personnelles :

- compteurs par table métier ;
- état `directAccessGrantsEnabled` du client `loyertracker-spa` ;
- état `enabled` du compte `bailleur-test@test.local` ;
- IDs et dates de démarrage des services ;
- liste des alertes actives ;
- tag et digests exécutés.

Ces données servent à vérifier le nettoyage et l'absence d'effet collatéral.

## 6. Préparer l'échafaudage de validation

Le smoke nécessite un bailleur de test et active déjà temporairement
`directAccessGrantsEnabled` avec révocation via `trap`.

Avant exécution :

- vérifier que le mot de passe de test est disponible hors logs ;
- activer temporairement le compte `bailleur-test@test.local` seulement si nécessaire ;
- relever son état initial pour le restaurer exactement après le test ;
- ne modifier aucun redirect URI, web origin, hostname ou règle réseau ;
- définir la cible locale sécurisée :

```bash
BASE=https://localhost:18443
CACERT=infra/nginx/certs/localhost.pem
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
```

## 7. Exécuter le smoke Production

Commande prévue :

```bash
BASE=https://localhost:18443 \
CACERT=infra/nginx/certs/localhost.pem \
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
./infra/smoke/smoke-stack.sh
```

Résultat attendu : **47 PASS / 0 FAIL**.

Le smoke couvre notamment :

- Flyway et rôle SQL applicatif ;
- JWT Keycloak réels ;
- inscription bailleur ;
- patrimoine, bien et bail ;
- invitation/acceptation gestionnaire ;
- affectation patrimoine ;
- paiements, honoraires, alertes et audit ;
- isolation cross-tenant ;
- garde-fous AuthN et ports internes.

Un seul FAIL impose l'arrêt du chemin de succès.

## 8. Validation spécifique du Hotfix

### Backend

Vérifier dans le résultat et, si nécessaire, par une requête authentifiée ciblée :

- une nouvelle inscription crée exactement un patrimoine principal actif ;
- la création d'un bien avec `patrimoineId` valide retourne 201 ;
- aucune erreur 400 liée au patrimoine sur le flux nominal.

### Frontend

L'image Web Production a le même digest que l'image validée par navigateur en Staging.

Vérifier :

- chargement de la SPA et des assets ;
- absence d'erreur JavaScript critique ;
- présence des sélecteurs patrimoine et type ;
- si un navigateur Production contrôlé est utilisé : authentification, préremplissage et
  création d'un bien via le formulaire.

Une vérification navigateur Production ne doit pas nécessiter d'ouverture de port, de
modification Keycloak, de CORS ou de Security Group. Si elle nécessite une telle mutation, elle
est abandonnée et l'équivalence de digest Staging + smoke Production est utilisée comme preuve.

## 9. Nettoyage obligatoire

Le smoke crée des données et des comptes suffixés par son `RUN_ID`. Avant toute décision de
succès :

1. relever le `RUN_ID` et les identifiants créés ;
2. supprimer uniquement les données du run dans l'ordre respectant les clés étrangères ;
3. supprimer les utilisateurs Keycloak créés par le run ;
4. restaurer l'état initial du compte `bailleur-test@test.local` ;
5. confirmer `directAccessGrantsEnabled=false` ;
6. vérifier que les compteurs métier reviennent à l'état attendu, hors écritures d'audit
   techniquement non supprimables si la politique d'audit exige leur conservation ;
7. documenter toute trace de test conservée et sa justification.

Le nettoyage ne doit jamais utiliser `TRUNCATE`, suppression globale, reset de volume ou
restauration complète de la base.

Si un nettoyage sûr et ciblé n'est pas possible, la validation ne peut pas être clôturée en
succès sans décision explicite du PO et du Governance Officer.

## 10. Contrôles après nettoyage

- quatre services applicatifs healthy ;
- API/Web toujours sur `sha-0adc4941` ;
- Flyway V1→V14 ;
- issuer Keycloak canonique ;
- Production publique accessible ;
- Prometheus 5/5 `up` ;
- aucune alerte critique ;
- `directAccessGrantsEnabled=false` ;
- compte de test restauré à son état initial ;
- aucune donnée de test active ou compte éphémère restant ;
- logs sans erreur critique.

## 11. Point de décision fonctionnel

### Validation PASS

Conditions cumulatives :

- smoke 47/0 ;
- critères Hotfix prouvés ;
- services et observabilité sains ;
- nettoyage terminé ;
- échafaudage Keycloak révoqué ;
- aucun défaut critique ou majeur ouvert.

### Validation FAIL

Un des cas suivants impose le rollback :

- smoke avec au moins un FAIL ;
- régression fonctionnelle du Hotfix ;
- fuite cross-tenant ou incident de sécurité ;
- service unhealthy ;
- erreur critique dans les logs ;
- nettoyage impossible ou incomplet ;
- altération inattendue de données ou de configuration.

## 12. Persister le tag après PASS

La persistance n'est effectuée qu'après la validation et le nettoyage.

Procédure :

1. créer une copie de sauvegarde horodatée de `.env`, permissions `600` ;
2. modifier uniquement la ligne `LOYERTRACKER_TAG` vers `sha-0adc4941` ;
3. vérifier sans afficher les autres variables que la clé apparaît exactement une fois ;
4. exécuter `docker compose config` avec les fichiers Production et vérifier que les images
   résolues sont les digests candidats ;
5. confirmer qu'aucun `up`, restart ou recreate n'est nécessaire : les conteneurs actifs
   exécutent déjà ces images ;
6. confirmer de nouveau la santé publique et les digests actifs.

La sauvegarde `.env` doit rester hors dépôt et être documentée dans l'inventaire d'exploitation.

## 13. Procédure de rollback en cas de FAIL

1. restaurer l'état Keycloak initial et nettoyer les données de test si possible ;
2. redéployer `api` et `nginx` avec `sha-05424aa3` :

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml \
LOYERTRACKER_TAG=sha-05424aa3 \
docker compose up -d --no-deps api nginx
```

3. vérifier les anciens digests, services healthy, Flyway 14/14 et point d'entrée public ;
4. garantir que `.env` reste ou revient à `sha-05424aa3` ;
5. conserver le backup ;
6. produire un rapport d'incident et marquer la promotion **NO GO**.

La restauration PostgreSQL n'est envisagée que si une altération de données ne peut pas être
nettoyée de manière ciblée, après une décision d'incident séparée.

## 14. Statuts et documentation après PASS

Après persistance réussie :

- marquer `PRODUCTION_DEPLOYED` ;
- finaliser `docs/release-notes-v1.1.1.md` ;
- mettre à jour `docs/prod-state.md` ;
- mettre à jour `docs/project-state.md` ;
- mettre à jour `CHANGELOG.md` avec la date de déploiement ;
- compléter la décision Gate `1.1.1` avec l'exécution réelle ;
- tracer backup, tag, digests, opérateur, smoke, nettoyage et rollback disponible.

## 15. Preuves de sortie

- résultat complet du smoke ;
- preuves spécifiques Hotfix ;
- état avant/après du test ;
- preuve de nettoyage ;
- état Keycloak restauré ;
- état Prometheus/Alertmanager ;
- tag persistant et digests actifs ;
- décision finale PASS ou rollback ;
- documents de Production finalisés.

## 16. Décision finale

### PASS

Chief Delivery Officer : `PRODUCTION_DEPLOYED`.

### FAIL

Rollback vérifié, `PRODUCTION_DEPLOYED` non atteint, décision NO GO.

## 17. Résultat d’exécution

Rapport final : `docs/cgpa/09-production/validation-finale-v1.1.1-report.md`.

- Smoke Production : **47 PASS / 0 FAIL**.
- Validation patrimoine/bien et isolation cross-tenant : PASS.
- Données et comptes éphémères nettoyés ; compteurs revenus à l’état de référence.
- Compte de smoke désactivé ; `directAccessGrantsEnabled=false`.
- Tag `sha-0adc4941` persisté dans `.env` avec backup 600.
- API/Web healthy, Flyway 14/14, cinq cibles Prometheus up, aucune alerte.
- Décision : **`PRODUCTION_DEPLOYED`**.
