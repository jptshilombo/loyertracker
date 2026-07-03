# Plan Hypercare — Release `1.7.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-03 ~13:35 UTC |
| T0 | 2026-07-03 ~13:45 UTC (checkpoint réalisé ~10 min après `PRODUCTION_DEPLOYED`) |
| T+12 | 2026-07-04 ~01:45 UTC ± 30 min |
| T+24 | 2026-07-04 ~13:45 UTC ± 30 min |
| Tag surveillé | `sha-6a358eb6` |
| Rollback disponible | **Aucun rollback applicatif seul viable** (RSV-S9-03, acceptée par le PO) — `bail.depot_garantie` supprimée par V20 ; seule la restauration complète du backup pré-déploiement (`loyertracker-20260703-131331.dump`) permet un retour arrière sûr |

## Critères de suspension (tout FAIL → évaluation immédiate)

- Restart inattendu d'un conteneur applicatif (`api`, `nginx`, `keycloak`, `postgres`).
- Régression fonctionnelle sur le parcours bailleur (dashboard vide, création/consultation de
  garantie en erreur, `depotGarantie` incohérent sur un bail réel).
- Erreur 500 sur tout endpoint `garantie` (`POST .../garanties`, `POST .../garanties/{id}/restitution`).
- Écart entre `garantie.solde_actuel` et la somme des mouvements `garantie_movement` (violation de
  l'invariant central de ce sprint).
- Augmentation anormale des erreurs 5xx sur Prometheus.
- Alerte Alertmanager hors `BackupHeartbeatMissing` (pré-existante connue, cf. RSV-T24-01).

## Rollback si nécessaire

**Rappel critique (RSV-S9-03)** : un rollback applicatif seul (redéployer `sha-2da27182` sans
restaurer le schéma) provoquerait une erreur sur **toute requête `bail`** — `bail.depot_garantie`
n'existe plus. Seule option sûre :

```
infra/backup/restore-postgres.sh --yes \
  ~/loyertracker-backups/daily/loyertracker-20260703-131331.dump
LOYERTRACKER_TAG=sha-2da27182 \
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
```

Cette restauration annule aussi la migration V20 et les 2 garanties reconstituées par l'option A1
— à ré-exécuter intégralement (Préflight A1 + déploiement) si un nouveau déploiement `1.7.0` était
retenté après un rollback.

---

## Checkpoint T0 — 2026-07-03 ~13:45 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke 59/0 | ✅ PASS — `validation-finale-v1.7.0-report.md` |
| `LOYERTRACKER_TAG=sha-6a358eb6` persisté dans `.env` | ✅ PASS |
| `bailleur-test` désactivé dans Keycloak | ✅ PASS (`enabled: false`) |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ PASS |
| Flyway 20/20 | ✅ PASS |
| Digest API | ✅ `sha256:485c8574…` — conforme au Gate Production |
| Digest Web | ✅ `sha256:70ae97f2…` — conforme au Gate Production |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 `up` | ✅ PASS |
| Alertmanager | ✅ `[]` — 0 alerte active |
| Capacité hôte | ✅ disque 31 Gio libres (18 %), mémoire 1,9 Gio dispo, charge 0,01/0,12/0,11 |
| p99 latence (10 min) | ✅ ~47 ms (légèrement élevé, cohérent avec la charge du smoke qui vient de s'achever) |
| 5xx rate (10 min) | ✅ 0 (aucun point de données) |
| Hikari pending | ✅ 0 |
| Heartbeat backup | ✅ âge ~31 min (`loyertracker_backup_last_success_epoch`, poussé au Préflight) — largement sous le seuil (26 h) |
| Logs API — erreurs critiques (30 min) | ✅ 1× `duplicate key` — attendue (smoke POST inscription 409), aucune erreur inattendue |
| Invariant `garantie.solde_actuel = Σ mouvements` | ✅ PASS — vérifié à la validation finale (3/3 garanties cohérentes) |

**Décision T0 : PASS — hypercare sous surveillance.**

T+12 prévu : **2026-07-04 ~01:45 UTC** (tolérance ±30 min).
T+24 prévu : **2026-07-04 ~13:45 UTC** (tolérance ±30 min).

---

## Post-clôture (après décision CDO GO au T+24)

- Mettre à jour `docs/project-state.md` (bandeau de clôture).
- Mettre à jour `docs/prod-state.md` §0H (statut clôture).
- Créer `docs/cgpa/09-production/cloture-release-v1.7.0.md`.
- ~~Traiter RP-160-03~~ **✅ Fait le 2026-07-03** — `CHANGELOG.md` scindé en `[1.5.0]`/`[1.6.0]`/
  `[1.7.0]`.
