# Rapport d'exécution — Préflight et sauvegarde Production `1.2.0`

| Champ | Valeur |
|---|---|
| Date UTC | 2026-06-26 |
| Heure exécution | 17:18–18:21 UTC |
| Hôte | `loyertracker-prod-server` (`172.31.22.90`) |
| Décision | **PASS** |
| Production courante | `1.1.1` — `sha-0adc4941` |
| Candidat | `1.2.0` — `sha-5bf187af` |
| Déploiement exécuté | **Non** |

## Phase A — Préflight en lecture seule

| Contrôle | Résultat |
|---|---|
| Hostname | `ip-172-31-22-90` ✅ |
| Date UTC | 2026-06-26 17:18:10 UTC |
| NTP | Synchronisé (`NTPSynchronized=yes`) |
| Docker | 29.1.3 |
| Docker Compose | 2.40.3+ds1-0ubuntu1~24.04.1 |
| Commit dépôt hôte | `05424aa3df0f03f5267f2862df978b8622ad6838` |
| Worktree hôte | 3 fichiers non suivis connus (`.env.pre-1.1.0-*`, `.env.pre-1.1.1-*`, `.env_bkp`) — inchangés, déjà documentés |
| Espace disque | 33 Gio libres / 38 Gio — 15 % utilisé ✅ |
| Mémoire disponible | 2,1 Gio / 3,7 Gio ✅ |
| Swap | 0 ✅ |
| Charge | 0,11 / 0,30 / 0,24 (2 vCPU) ✅ |
| Services applicatifs | `api`, `nginx`, `postgres`, `keycloak` — `(healthy)` ; 8/8 Up ✅ |
| Restart count | 0 sur 8 conteneurs ✅ |
| Image API | `ghcr.io/jptshilombo/loyertracker-api:sha-0adc4941` ✅ |
| Image Web | `ghcr.io/jptshilombo/loyertracker-web:sha-0adc4941` ✅ |
| Tag courant | `LOYERTRACKER_TAG=sha-0adc4941` (`.env`) — conforme à Production `1.1.1` ✅ |
| Flyway | 14 migrations, rang max 14, 0 échec ✅ |
| Issuer Keycloak | `https://loyertracker.loyerpro.org/auth/realms/loyertracker` ✅ |
| Prometheus cibles | 5/5 `up` (`loyertracker-api`, `blackbox-keycloak`, `blackbox-postgres`, `prometheus`, `pushgateway`) ✅ |
| Alertmanager | 0 alerte active ✅ |
| Cron backup | `15 2 * * *` — une entrée ✅ |

### Note — Redémarrage récent et heartbeat absent

Le serveur avait un uptime de 7 minutes au moment du préflight (redémarrage récent non planifié).
Le metric `loyertracker_backup_last_success_epoch` était absent de Prometheus (Pushgateway non
encore alimenté après le redémarrage). Le dernier dump cron datait du 2026-06-25 03:15 UTC
(~38 h), dépassant le seuil d'alerte de 26 h.

Cette situation correspond à l'exception prévue par le plan : l'absence d'un backup récent est
précisément ce que la Phase B corrige. Le Alertmanager n'avait pas encore évalué l'alerte
(scrape cycles insuffisants depuis le redémarrage), ce qui explique l'affichage de 0 alertes
actives à ce stade — cohérent avec la situation observée pour le préflight `1.1.1`.

**Point de contrôle A : PASS** — anomalie heartbeat attendue, levée par la Phase B.

## Phase B — Sauvegarde Production

Commande exécutée :

```bash
cd ~/loyertracker
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml ./infra/backup/backup-postgres.sh
```

| Élément | Valeur |
|---|---|
| Dump | `loyertracker-20260626-182030.dump` |
| Taille | 308 Kio |
| Permissions | `600` |
| SHA-256 | `4ed4e837be0a567fd65d5959cd2664a05ab8dada279d635730ff782b2fee0b7d` |
| Globals | `loyertracker-20260626-182030.globals.sql` |
| Taille globals | 1,1 Kio |
| Permissions globals | `600` |
| SHA-256 globals | `aee7eb7c8f375e1949baa61e5b3adaea626a6eb61454f3f21a4a413a690fcf65` |
| `pg_restore --list` | OK — script : `OK  dump vérifié : 308K` |
| Heartbeat | Poussé : `OK  heartbeat de sauvegarde poussé vers http://127.0.0.1:9091` |
| Heartbeat age post-push | 22 s ✅ |
| Rétention | Appliquée (quotidien 7 j, hebdo 28 j) |

## Vérification de non-mutation

| Contrôle | Résultat |
|---|---|
| Services post-backup | 8/8 Up, `api`/`nginx`/`postgres`/`keycloak` `(healthy)` ✅ |
| Image API | `sha-0adc4941` — inchangée ✅ |
| Image Web | `sha-0adc4941` — inchangée ✅ |
| Prometheus 5/5 | `up` post-backup ✅ |
| Tag `.env` | `sha-0adc4941` — inchangé ✅ |
| Flyway | 14/14 — inchangé ✅ |
| Candidat `sha-5bf187af` | Non tiré sur l'hôte ✅ |
| Pull / up / down exécutés | Aucun ✅ |
| `.env` modifié | Non ✅ |

## Rollback disponible

| Élément | Valeur |
|---|---|
| Tag rollback applicatif | `sha-0adc4941` (images actuellement exécutées en Production) |
| Digest API | `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016` |
| Digest Web | `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` |
| Rollback schéma V15 | Restauration backup `loyertracker-20260626-182030.dump` (pg_restore) — procédure documentée Gate Production §Rollback |
| Responsable | DevSecOps Lead, coordination Release Manager |

## Décision

**Préflight PASS.**

La réserve `RP-120-01` (backup pré-déploiement) est **levée**. Le backup
`loyertracker-20260626-182030.dump` est vérifié (`pg_restore --list` OK, permissions 600,
checksums produits, heartbeat scrappé).

Cette décision autorise l'exécution du déploiement technique `1.2.0` selon le plan de
déploiement à produire.
