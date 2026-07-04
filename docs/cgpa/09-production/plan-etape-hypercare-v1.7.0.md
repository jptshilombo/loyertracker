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

## Checkpoint combiné T+12 (rattrapage) / T+24 (anticipé) — 2026-07-04 10:18 UTC

**Statut : PASS**

**Qualification des écarts de fenêtre (décision PO du 2026-07-04, précédents `1.3.0`/`1.4.0`)** :
le serveur de production était **volontairement éteint pendant la nuit** (pratique documentée —
produit non annoncé publiquement) et a été démarré le 2026-07-04 à ~09:09 UTC. Le T+12
(fenêtre 01:15–02:15 UTC) était donc **matériellement inexécutable** (hôte hors tension), et le
T+24 est exécuté par anticipation (~3 h 27 avant l'ancre de 13:45 UTC) sur décision explicite
du PO. Checkpoint unique valant T+12 en rattrapage et T+24 anticipé.

| Contrôle | Résultat |
|---|---|
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ PASS (up ~1 h 09, démarrage propre au boot de l'hôte) |
| `LOYERTRACKER_TAG=sha-6a358eb6` persisté dans `.env` | ✅ PASS — zéro dérive |
| Digest API `sha256:485c8574…` | ✅ conforme au Gate Production |
| Digest Web `sha256:70ae97f2…` | ✅ conforme au Gate Production |
| Flyway | ✅ 20/20 `success` |
| Actuator | ✅ `{"status":"UP"}` |
| Prometheus | ✅ 5/5 cibles `up` |
| Alertmanager | ⚠️ 1 alerte `BackupHeartbeatMissing` — **qualifiée** (cf. ci-dessous), exclue des critères de suspension par ce plan (RSV-T24-01) |
| p99 latence (1 h) | ✅ ~24,7 ms |
| 5xx rate (1 h) | ✅ 0 (aucun point de données) |
| 401 (1 h) | ✅ ~2 — négligeable |
| Hikari pending | ✅ 0 |
| Capacité hôte | ✅ charge 0,00/0,02/0,01 ; mémoire dispo 2,0 Gio ; disque 31 Go libres (81 %) |
| Logs API — erreurs depuis le boot | ✅ **0** ; les seules entrées ERROR du conteneur datent du 2026-07-03 (2× `duplicate key bailleur_keycloak_id_key` + WARN associés — smoke inscription 409, déjà qualifiées au T0) |
| Invariant `garantie.solde_actuel = Σ mouvements` | ✅ PASS — 3/3 garanties cohérentes (2100,00 / 600,00 / 600,00), 3 mouvements |

**Qualification `BackupHeartbeatMissing` + métriques pushgateway absentes** : le pushgateway ne
persiste pas ses métriques au redémarrage — le boot de 09:09 UTC les a purgées (heartbeat backup,
batchs alertes/loyers). De plus, le cron de backup (02:15 UTC quotidien) n'a pas pu jouer cette
nuit, l'hôte étant éteint. Dernier backup réel : `loyertracker-20260703-131331.dump`
(2026-07-03 13:13 UTC, ~21 h — **sous le seuil de 26 h**). L'alerte se résorbera au prochain
passage du cron (2026-07-05 02:15 UTC si l'hôte reste allumé) ; sans impact sur le verdict.

**Décision au checkpoint : PASS.** Les critères techniques de clôture sont satisfaits ; la
clôture de la release `1.7.0` reste suspendue à la **décision CDO GO** (étape distincte).

---

## Post-clôture (après décision CDO GO au T+24)

- Mettre à jour `docs/project-state.md` (bandeau de clôture).
- Mettre à jour `docs/prod-state.md` §0H (statut clôture).
- Créer `docs/cgpa/09-production/cloture-release-v1.7.0.md`.
- ~~Traiter RP-160-03~~ **✅ Fait le 2026-07-03** — `CHANGELOG.md` scindé en `[1.5.0]`/`[1.6.0]`/
  `[1.7.0]`.
