# Plan Hypercare — Release `1.2.1`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-06-27 09:08 UTC |
| T0 | 2026-06-27 09:08 UTC |
| T+12 | 2026-06-27 21:08 UTC ± 30 min |
| T+24 | 2026-06-28 09:08 UTC ± 30 min |
| Tag surveillé | `sha-47172297` |
| Rollback disponible | `sha-5bf187af` — applicatif seul, sans pg_restore |

## Critères de suspension (tout FAIL → rollback immédiat)

- Restart inattendu d'un conteneur applicatif (`api`, `nginx`, `keycloak`, `postgres`).
- Régression fonctionnelle détectée sur le parcours bailleur (biens non chargés, dashboard vide).
- Augmentation anormale des erreurs 5xx sur Prometheus.
- Alerte Alertmanager hors `BackupHeartbeatMissing` (alerte pré-existante, cf. rapport déploiement).

## Rollback immédiat si nécessaire

```
LOYERTRACKER_TAG=sha-5bf187af \
  docker compose \
    --project-directory /home/ubuntu/loyertracker \
    -f /home/ubuntu/loyertracker/docker-compose.yml \
    -f /home/ubuntu/loyertracker/docker-compose.prod.yml \
    up -d nginx api
```

Aucun pg_restore requis — aucune migration entre `1.2.0` et `1.2.1`.

---

## Checkpoint T0 — 2026-06-27 09:08 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke 47/0 | ✅ PASS — `validation-finale-v1.2.1-report.md` |
| `bailleur-test` désactivé dans Keycloak | ⚠️ Pré-existant — rétabli manuellement (runbook à mettre à jour) |
| Correctif `c1e9c73` vérifié (POST inscription 409 + biens chargés) | ✅ PASS |
| `LOYERTRACKER_TAG=sha-47172297` persisté dans `.env` | ✅ PASS |
| 8/8 conteneurs Up, 4/4 healthy, restart=0 | ✅ PASS |
| Actuator UP | ✅ PASS |
| Prometheus 5/5 | ✅ PASS |
| Alertmanager | ⚠️ `BackupHeartbeatMissing` pré-existante — hors périmètre `1.2.1` |

**Décision T0 : PASS — hypercare sous surveillance.**

---

## Checkpoint T+12 anticipé — 2026-06-27 09:13 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs Up | ✅ nginx Up 16 min (healthy), api Up 16 min (healthy), keycloak Up 46 min (healthy), postgres Up 46 min (healthy), prometheus/alertmanager/pushgateway/blackbox Up 46 min |
| 4/4 restart=0 | ✅ nginx=0, api=0, keycloak=0, postgres=0 |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 cibles `up` | ✅ blackbox-keycloak, blackbox-postgres, loyertracker-api, prometheus, pushgateway |
| Alertmanager | ⚠️ `BackupHeartbeatMissing` active — pré-existante (cron 02:15 UTC pas encore repassé depuis reboot serveur) |
| Logs API — erreurs critiques | ✅ Aucune — uniquement 2× `23505` (`bailleur_keycloak_id_key`) attendues (POST inscription 409 du smoke) |

---

## Checkpoint T+24 anticipé — 2026-06-27 09:15 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs Up | ✅ nginx Up 17 min (healthy), api Up 17 min (healthy), keycloak Up 48 min (healthy), postgres Up 48 min (healthy), prometheus/alertmanager/pushgateway/blackbox Up 48 min |
| 4/4 restart=0 | ✅ nginx=0, api=0, keycloak=0, postgres=0 |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 cibles `up` | ✅ blackbox-keycloak, blackbox-postgres, loyertracker-api, prometheus, pushgateway |
| Alertmanager | ⚠️ `BackupHeartbeatMissing` active — pré-existante (cron 02:15 UTC pas encore repassé depuis reboot serveur) |
| Logs API — erreurs critiques hors smoke | ✅ **0** |

**Décision T+24 : PASS — CDO GO, release `1.2.1` CLÔTURÉE.**

---

## Post-clôture (après décision CDO GO)

- Promouvoir `CHANGELOG.md` : `[Non publié]` → `[1.2.1] — 2026-06-27`
- Mettre à jour `docs/project-state.md` (bandeau, §1, §3A, lever RP-120-03)
- Mettre à jour `docs/prod-state.md`
- Créer `docs/cgpa/09-production/cloture-release-v1.2.1.md`
