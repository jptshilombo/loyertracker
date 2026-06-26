# Plan — Hypercare Production 24 heures `1.2.0`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-26 |
| Statut | **Exécution en cours — T0 PASS sous surveillance** |
| Release surveillée | `1.2.0` |
| Tag attendu | `sha-5bf187af` |
| Digests attendus | API `sha256:3e511356e723acb0a9769f494b1b574bfa9342f4cb0d419a23168166112cca0d` / Web `sha256:36493866018fc2cbd7a15eed86a378bd836ef472378ea998e25a83ea9eca2520` |
| Rollback disponible | `sha-0adc4941` + backup `loyertracker-20260626-182030.dump` |
| Dossier de clôture | `docs/cgpa/09-production/cloture-release-v1.2.0.md` |
| Durée | 24 heures à partir du T0 réel |
| T0 réel | 2026-06-26 17:49 UTC (`PRODUCTION_DEPLOYED`) |
| T+12 cible | 2026-06-27 05:49 UTC (fenêtre 05:19–06:19 UTC) |
| T+24 cible | 2026-06-27 17:49 UTC (fenêtre 17:19–18:19 UTC) |

## 1. Objectif

Observer la Production pendant 24 heures sans mutation afin de confirmer la stabilité de la
release `1.2.0` (Sprint 3 Patrimoine US-85, V15, RS-04 + correctif CORS Compose), puis
statuer sur la clôture opérationnelle.

L'hypercare comprend trois snapshots datés : T0, T+12 h et T+24 h. Elle ne rejoue pas le smoke
métier et ne crée aucune donnée de test.

## 2. Périmètre strict

### Autorisé

- Connexions SSH en lecture à `loyertracker-prod-server` (`18.158.70.88`).
- Cibler `COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml`.
- Lire l'état des conteneurs, images, métriques, alertes et logs récents.
- Lire uniquement `LOYERTRACKER_TAG` dans `.env` (sans afficher `.env` en entier).
- Interroger Flyway, Prometheus, Alertmanager et Hikari en lecture seule.

### Interdit

- `docker compose pull`, `up`, `down`, `stop`, `restart`, `rm` ou `exec` avec écriture.
- Toute commande Docker globale ou destructrice.
- Modification de `.env`, des services, du réseau, du Security Group ou de Keycloak.
- Requête métier créant, modifiant ou supprimant une donnée.
- Smoke, restauration, backup manuel ou changement de tag.

## 3. Contrôles à chaque checkpoint

| Contrôle | Seuil de suspension |
|---|---|
| Services | Un service applicatif non-healthy pendant 2 min |
| Restart count | Toute augmentation |
| Tag/Digest | Dérive par rapport à `sha-5bf187af` |
| Flyway | Migration non réussie ou rang ≠ 15 |
| 5xx rate | > 5 % pendant 5 min |
| p99 latence | > 2 s pendant 5 min |
| 401 rate | > 1/s pendant 5 min |
| Hikari pending | > 0 pendant 5 min |
| Backup heartbeat | > 26 h |
| Disque | < 20 % libre |
| Mémoire disponible | < 15 % de manière persistante |

## 4. Spécificités `1.2.0`

- **V15** : la colonne `affectation.type_exception` est en production. Surveiller toute
  erreur SQL liée aux contraintes V15 (CHECK `type_exception IN ('INCLUSION','EXCLUSION')`).
- **Rollback schéma** : non trivial — nécessite `pg_restore` si V15 doit être annulée.
- **RP-120-02** (rollback V15) et **RP-120-03** (c1e9c73 exclu) : réserves maintenues.
- **Correctif cascade dashboard c1e9c73** : non déployé — les 401 de session expirée côté
  dashboard restent possibles mais non bloquants (comportement inchangé vs `1.1.1`).

## 5. Cadence

1. T0 fixé au `PRODUCTION_DEPLOYED` : 2026-06-26 17:49 UTC.
2. T+12 exécuté entre 05:19 et 06:19 UTC le 2026-06-27.
3. T+24 exécuté entre 17:19 et 18:19 UTC le 2026-06-27.
4. Un checkpoint manqué de > 30 min suspend la clôture ; le CDO décide.
