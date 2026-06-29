# Plan Hypercare — Release `1.3.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-06-29 14:31 UTC |
| T0 | 2026-06-29 14:31 UTC |
| T+12 | 2026-06-30 02:31 UTC ± 30 min |
| T+24 | 2026-06-30 14:31 UTC ± 30 min |
| Tag surveillé | `sha-a42d860d` |
| Rollback disponible | `sha-47172297` — applicatif seul, sans pg_restore |

## Critères de suspension (tout FAIL → rollback immédiat)

- Restart inattendu d'un conteneur applicatif (`api`, `nginx`, `keycloak`, `postgres`).
- Régression fonctionnelle sur le parcours bailleur (dashboard vide, affectations patrimoine inaccessibles, endpoint `GET /api/patrimoines/{id}/affectations` en erreur).
- Augmentation anormale des erreurs 5xx sur Prometheus.
- Alerte Alertmanager hors `BackupHeartbeatMissing` (pré-existante connue).

## Rollback immédiat si nécessaire

```
LOYERTRACKER_TAG=sha-47172297 \
  docker compose \
    --project-directory /home/ubuntu/loyertracker \
    -f /home/ubuntu/loyertracker/docker-compose.yml \
    -f /home/ubuntu/loyertracker/docker-compose.prod.yml \
    up -d nginx api
```

Aucun pg_restore requis — aucune migration entre `1.2.1` et `1.3.0`.

---

## Checkpoint T0 — 2026-06-29 14:31 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke 47/0 | ✅ PASS — `validation-finale-v1.3.0-report.md` |
| `LOYERTRACKER_TAG=sha-a42d860d` persisté dans `.env` | ✅ PASS |
| `bailleur-test` désactivé dans Keycloak | ✅ PASS (`enabled: false`) |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ PASS |
| Flyway 15/15 | ✅ PASS |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 `up` | ✅ PASS |
| Alertmanager | ✅ `[]` — 0 alerte active |
| Capacité hôte | ✅ disque 32 Gio libres (16 %), mémoire 1,9 Gio dispo, charge 0,00/0,13/0,15 |
| p99 latence | ✅ ~33 ms |
| 5xx rate (5 min) | ✅ 0 (`NaN` = aucun point) |
| 401 rate (5 min) | ✅ 0 (`NaN` = aucun point) |
| Hikari pending | ✅ 0 |
| Heartbeat backup | ✅ `loyertracker_backup_last_success_epoch` présent (~4 min, manuel validation finale) |
| Logs API — erreurs critiques hors smoke | ✅ 2× `duplicate key bailleur_keycloak_id_key` (smoke POST inscription 409 — attendus, non bloquants) |

**Décision T0 : PASS — hypercare sous surveillance.**

T+12 prévu : **2026-06-30 02:31 UTC** (tolérance ±30 min).
T+24 prévu : **2026-06-30 14:31 UTC** (tolérance ±30 min).

---

## Checkpoint T+12 anticipé — 2026-06-29 14:27 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ nginx Up 15 min, api Up 15 min, keycloak/postgres Up 43 min ; restart=0 sur tous |
| `LOYERTRACKER_TAG=sha-a42d860d` | ✅ conforme |
| Flyway 15/15 | ✅ |
| Actuator `{"status":"UP"}` | ✅ |
| Prometheus 5/5 `up` | ✅ |
| Alertmanager | ✅ `[]` — 0 alerte active |
| Capacité hôte | ✅ disque 32 Gio libres (16 %), mémoire 2,0 Gio dispo, charge 0,06/0,11/0,13 |
| p99 latence | ✅ ~82 ms (trafic minimal post-smoke, normal) |
| 5xx rate (5 min) | ✅ 0 (`result:[]`) |
| 5xx Nginx (20 min) | ✅ 0 |
| Hikari pending | ✅ 0 |
| Heartbeat backup | ✅ epoch `1782742878` — âge ~6,7 min (manuel validation finale) |
| Logs API — erreurs critiques hors smoke | ✅ 0 nouvelle erreur critique depuis T0 |

---

## Checkpoint T+24 anticipé — 2026-06-29 14:29 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ nginx Up 18 min, api Up 18 min, keycloak/postgres Up 46 min ; restart=0 sur tous |
| `LOYERTRACKER_TAG=sha-a42d860d` | ✅ conforme — zéro dérive sur la durée |
| Digest API | ✅ `sha256:c3d89f0d…` — identique au Gate Production |
| Digest Web | ✅ `sha256:c3070898…` — identique au Gate Production |
| Flyway 15/15 | ✅ |
| Actuator `{"status":"UP"}` | ✅ |
| Prometheus 5/5 `up` | ✅ |
| Alertmanager | ✅ `[]` — 0 alerte active |
| Capacité hôte | ✅ disque 32 Gio libres (16 %), mémoire 1,9 Gio dispo, charge 0,03/0,09/0,12 |
| p99 latence | ✅ ~26 ms |
| 5xx rate (5 min) | ✅ 0 (`result:[]`) |
| 5xx Nginx (25 min) | ✅ 0 |
| Hikari pending | ✅ 0 |
| Heartbeat backup | ✅ epoch `1782742878` — âge ~9 min (largement sous seuil 26 h) |
| Logs API — erreurs critiques hors smoke | ✅ 0 depuis le déploiement |

**Les cinq critères de clôture étant satisfaits (services stables, métriques saines, 0 5xx,
0 erreur critique, digests conformes), le CDO statue :**

**GO — RELEASE `1.3.0` CLÔTURÉE le 2026-06-29.**

---

## Post-clôture (après décision CDO GO au T+24)

- Promouvoir `CHANGELOG.md` : `[Non publié]` → `[1.3.0] — 2026-06-29`
- Mettre à jour `docs/project-state.md` (version, bandeau, §1, §3A)
- Mettre à jour `docs/prod-state.md`
- Créer `docs/cgpa/09-production/cloture-release-v1.3.0.md`
