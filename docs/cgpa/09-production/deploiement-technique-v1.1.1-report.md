# Rapport d'exécution — Déploiement technique Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date UTC | 2026-06-24 |
| Début | 14:21:48 UTC |
| Déploiement | 14:22:42 UTC |
| Fin des contrôles | 14:24 UTC |
| Hôte | `loyertracker-prod-server` |
| Décision | **PASS technique** |
| Candidat | API/Web `sha-0adc4941` |
| Rollback | API/Web `sha-05424aa3` |
| `PRODUCTION_DEPLOYED` | **Non atteint** |

## Contrôle avant mutation

- Production accessible.
- API, Web, PostgreSQL et Keycloak healthy.
- Backup `loyertracker-20260624-140441.dump` confirmé par checksum.
- Flyway 14/14.
- Aucune alerte active.
- Images courantes avant déploiement : `sha-05424aa3`.

## Pull et vérification

Seules les images API et Web `sha-0adc4941` ont été tirées.

| Image | Digest vérifié |
|---|---|
| API | `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016` |
| Web | `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` |

Une première commande d'affichage des digests a échoué sur le formatage du template Docker,
après un pull réussi et avant toute recréation. La lecture a été corrigée ; les digests ont été
confirmés avant déploiement.

## Services recréés

| Service | Action | Résultat |
|---|---|---|
| `api` | Recréé | Healthy, restart count 0 |
| `nginx` | Recréé | Healthy, restart count 0 |
| `postgres` | Non recréé | Même container ID, healthy |
| `keycloak` | Non recréé | Même container ID, healthy |
| Monitoring | Non recréé | Conteneurs inchangés |

Compose a signalé les services de monitoring comme « orphans », car l'overlay monitoring
n'était pas inclus dans la commande ciblée. Aucun `--remove-orphans` n'a été utilisé et aucun
service de monitoring n'a été modifié.

## Contrôles techniques

| Contrôle | Résultat |
|---|---|
| API/Web | `sha-0adc4941`, digests attendus |
| PostgreSQL | Ready |
| Flyway | 14 migrations validées, schéma à jour, aucune migration appliquée |
| Keycloak | Healthy, issuer Production inchangé |
| Racine publique | OK |
| Prometheus public | HTTP 404 |
| Cibles Prometheus | 5/5 `up` |
| Alertes | Aucune |
| Logs API | Démarrage réussi, aucune erreur critique |
| Logs Nginx | Démarrage réussi, aucune erreur critique |

Les avertissements de démarrage `commons-logging` et configuration Nginx en lecture seule sont
connus et non bloquants ; les services sont healthy.

## Invariant `.env`

`.env` n'a pas été modifié conformément au plan. Les conteneurs actifs exécutent le candidat,
mais la valeur persistante de `LOYERTRACKER_TAG` reste celle de la Production précédente.

Conséquence : avant de déclarer `PRODUCTION_DEPLOYED`, l'Étape 5 doit :

- en cas de validation finale : persister `sha-0adc4941` de manière contrôlée, puis vérifier
  qu'aucune recréation inattendue n'est déclenchée ;
- en cas d'échec : rollback vers `sha-05424aa3`.

## Tests non exécutés

- aucun smoke métier ;
- aucune création de données de test ;
- aucune modification Keycloak ;
- aucun changement de secret ;
- aucun statut `PRODUCTION_DEPLOYED`.

## Décision

**Déploiement technique PASS.**

L'artefact candidat est en cours d'exécution en Production, en attente de l'Étape 5 —
validation finale ou rollback. La seule action suivante autorisée est la production du plan
détaillé de l'Étape 5.
