# Rapport d'exécution — Déploiement technique Release `1.2.0`

| Champ | Valeur |
|---|---|
| Date UTC | 2026-06-26 |
| Début | 17:25 UTC |
| Pull images | 17:26 UTC |
| Up ciblé | 17:26 UTC |
| Contrôles terminés | 17:28 UTC |
| Hôte | `loyertracker-prod-server` (`172.31.22.90`) |
| Décision | **PASS technique** |
| Candidat | API/Web `sha-5bf187af` |
| Rollback applicatif | `sha-0adc4941` |
| Rollback schéma | `pg_restore` backup `loyertracker-20260626-182030.dump` |
| `PRODUCTION_DEPLOYED` | **Non atteint** |

## Contrôle avant mutation

| Contrôle | Valeur |
|---|---|
| Hôte | `ip-172-31-22-90` |
| Date UTC | `2026-06-26 17:25:37 UTC` |
| Tag `.env` | `LOYERTRACKER_TAG=sha-0adc4941` |
| Services | 8/8 Up — `api`/`nginx`/`postgres`/`keycloak` `(healthy)` |
| Flyway | 14 migrations, rang max 14, 0 échec |
| Backup pré-déploiement | `loyertracker-20260626-182030.dump` (308 Kio) — RP-120-01 levée |

## Backup `.env`

Avant toute mutation, `.env` sauvegardé sous `.env.pre-1.2.0-20260626-172551`.

## Mise à jour du tag et pull

`LOYERTRACKER_TAG` mis à jour dans `.env` : `sha-0adc4941` → `sha-5bf187af`.

Commande exécutée :

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml docker compose pull api nginx
```

Les images `api` et `nginx` `sha-5bf187af` ont été tirées depuis GHCR. Pull complet sans erreur.

## Services recréés

Commande exécutée :

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml docker compose up -d api nginx
```

| Service | Action | Résultat |
|---|---|---|
| `api` | Recréé | Healthy, restart count 0 |
| `nginx` | Recréé | Healthy, restart count 0 |
| `postgres` | Non recréé | Même container, healthy |
| `keycloak` | Non recréé | Même container, healthy |
| Monitoring | Non recréé | Conteneurs inchangés |

**Warning orphans** : Compose a signalé `prometheus`, `alertmanager`, `pushgateway`, `blackbox` comme orphans (même comportement qu'en `1.1.1`, attendu pour le déploiement ciblé sans overlay monitoring). Aucun `--remove-orphans` utilisé — aucun service de monitoring modifié.

## Contrôles techniques post-déploiement

| Contrôle | Résultat |
|---|---|
| Image API | `sha-5bf187af` ✅ |
| Digest API | `sha256:3e511356e723acb0a9769f494b1b574bfa9342f4cb0d419a23168166112cca0d` |
| Image Web | `sha-5bf187af` ✅ |
| Digest Web | `sha256:36493866018fc2cbd7a15eed86a378bd836ef472378ea998e25a83ea9eca2520` |
| API healthy | `(healthy)`, restart count 0 ✅ |
| Nginx healthy | `(healthy)`, restart count 0 ✅ |
| PostgreSQL | Inchangé — `(healthy)` ✅ |
| Keycloak | Inchangé — `(healthy)` ✅ |
| Flyway validé | 15 migrations, rang max 15, 0 échec ✅ |
| **V15 appliquée** | `Successfully applied 1 migration to schema "public", now at version v15` ✅ |
| Logs API | Démarrage réussi en 17,156 s — aucune erreur critique ✅ |
| Logs Nginx | Démarrage réussi — aucune erreur critique ✅ |
| Racine publique | `HTTP 200` ✅ |
| Prometheus cibles | 5/5 `up` (`loyertracker-api`, `blackbox-keycloak`, `blackbox-postgres`, `prometheus`, `pushgateway`) ✅ |
| Alertmanager | 0 alerte active ✅ |

### Détail Flyway — migrations appliquées

| Version | Description | Succès |
|---|---|---|
| V1 | init schema | ✅ |
| V2 | tenant resolution | ✅ |
| V3 | authorization predicates | ✅ |
| V4 | s02 biens baux affectations helpers | ✅ |
| V5 | role applicatif rls | ✅ |
| V6 | s03 echeances loyers | ✅ |
| V7 | s03 loyers en retard | ✅ |
| V8 | s04 honoraires | ✅ |
| V9 | s04 alertes | ✅ |
| V10 | s04 alerte preavis | ✅ |
| V11 | quittances ventilation loyer adresse bailleur | ✅ |
| V12 | patrimoine type bien | ✅ |
| V13 | affectations patrimoine | ✅ |
| V14 | honoraires patrimoine | ✅ |
| **V15** | **affectations exceptions** | **✅** |

### Extrait log Flyway (UTC)

```
2026-06-26T17:26:14Z  Successfully validated 15 migrations (execution time 00:00.084s)
2026-06-26T17:26:15Z  Successfully applied 1 migration to schema "public", now at version v15
2026-06-26T17:26:24Z  Started LoyerTrackerApplication in 17.156 seconds
```

## Invariant `.env`

`.env` a été modifié avant le déploiement (`LOYERTRACKER_TAG=sha-5bf187af`) et persiste après.
Un backup `.env.pre-1.2.0-20260626-172551` a été créé avant la mutation.

Conséquence : si la validation finale (`PRODUCTION_DEPLOYED`) est confirmée, aucune nouvelle
mutation de `.env` n'est nécessaire pour le tag. En cas de rollback, restaurer
`sha-0adc4941` dans `.env` puis `docker compose up -d api nginx`.

## Tests non exécutés

- Aucun smoke métier (réservé à la Validation finale)
- Aucune création de données de test
- Aucune modification Keycloak
- Aucun changement de secret
- Aucun statut `PRODUCTION_DEPLOYED`

## Rollback disponible

| Élément | Valeur |
|---|---|
| Tag rollback applicatif | `sha-0adc4941` |
| Digest API rollback | `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016` |
| Digest Web rollback | `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` |
| Rollback schéma V15 | `pg_restore` backup `loyertracker-20260626-182030.dump` — procédure : Gate Production `1.2.0` §Rollback |
| Responsable | DevSecOps Lead, coordination Release Manager |

## Décision

**Déploiement technique PASS.**

L'artefact `sha-5bf187af` est en cours d'exécution en Production. La migration V15
("affectations exceptions") a été appliquée avec succès. Tous les services sont healthy,
restart count 0. Aucune alerte active.

La seule action suivante autorisée est la **Validation finale `1.2.0`** (smoke 47/0,
preuves V15, nettoyage, persistance `PRODUCTION_DEPLOYED`).
