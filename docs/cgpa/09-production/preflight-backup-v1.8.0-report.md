# Rapport Préflight + Backup — Release `1.8.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-04 |
| Heure UTC | ~16:07–16:15 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.8.0` — candidat `sha-2c5f43c7` |
| Production actuelle | `1.7.0` — `sha-6a358eb6` |
| Verdict préflight | **PASS** |
| Verdict backup | **PASS** |
| Verdict vérifications §4.2 (Gate Production `1.8.0`) | **PASS — 4/4, dont confirmation OBS-S10-01 sans objet en Production** |

## 1. Contrôles de santé (lecture seule)

### Services

**8/8 conteneurs Up — 4/4 `(healthy)` (`api`, `nginx`, `postgres`, `keycloak`) — restart=0
partout.** Hôte démarré à ~15:40 UTC (pratique documentée : serveur volontairement éteint hors
sessions, cf. précédent qualifié à l'hypercare `1.7.0`).

### Tag en production (avant déploiement technique)

| Élément | Valeur |
|---|---|
| `LOYERTRACKER_TAG` | `sha-6a358eb6` (Production `1.7.0` — conforme, inchangé depuis la clôture de release) |

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **20/20** (V1→V20) — V21 pas encore appliquée |

### Capacité hôte (~16:07 UTC)

| Ressource | Valeur | Seuil |
|---|---|---|
| Disque `/` | 31 Gio libres (19 % utilisés) | > 5 % libres |
| Mémoire disponible | 2,1 Gio | > 500 Mio |
| Charge (load avg 1/5/15 min) | 0,33 / 0,12 / 0,04 | < 1,5 |

### Observabilité

| Contrôle | Résultat |
|---|---|
| Prometheus cibles `up` | 5/5 |
| Alertmanager | 1 alerte active à l'arrivée : `BackupHeartbeatMissing` (démarrée 15:11 UTC) — **cause récurrente connue et déjà qualifiée** (pushgateway purgé au boot de l'hôte, cron backup 02:15 UTC non joué hôte éteint ; mêmes précédents : hypercare `1.4.0`/`1.7.0`). **Résorbée dans cette session** par le heartbeat poussé par la sauvegarde §2 — Alertmanager `[]` vérifié après |

### Synchronisation du dépôt hôte

`~/loyertracker` était resté sur `c0a5be3` (merge PR #163) — même défaut récurrent que les
déploiements `1.7.0` (40 commits de retard) et S1 Staging. `git pull --ff-only` → `3a4aec6`
(merge PR #176, tête de `main`). Fichiers non suivis (`.env`, backups) non touchés.

## 2. Backup pré-déploiement

### Fichiers créés (16:08:36 UTC, via `infra/backup/backup-postgres.sh`, invocation cron canonique)

| Fichier | Taille | SHA-256 |
|---|---|---|
| `loyertracker-20260704-170836.dump` | 317 Kio | `69aec15ecca26ca1fdc807d55259d4f475f71c365d5217a03ba5764163b40dcd` |
| `loyertracker-20260704-170836.globals.sql` | 1,1 Kio | `fc714186f3765d3f0efd8842f7db2e3646aa327345fc689fb469d76fea5454c5` |

Permissions **600**. Heartbeat de sauvegarde poussé (a résorbé l'alerte `BackupHeartbeatMissing`,
§1). Rétention appliquée (7 quotidiennes + 4 hebdomadaires).

> Nota : l'horodatage du nom de fichier (`170836`) est en heure locale de l'hôte ; l'exécution
> correspond à 16:08:36 UTC.

### Vérification `pg_restore --list`

**Total : 740 entrées** — schéma, données, index, RLS, ACL, intégrité confirmée.

## 3. Vérifications lecture seule §4.2 (conditions du Gate Production `1.8.0`)

Exécutées par requête SQL directe, **strictement en lecture** (aucune mutation applicative,
Docker, `.env` ou DB dans toute cette session hormis la sauvegarde et son heartbeat) :

| # | Contrôle | Résultat |
|---|---|---|
| 1 | `garantie_movement.cree_le IS NOT NULL` sur toutes les lignes | ✅ **3/3** (backfill V20 et reconstitutions A1 comprises) — le tri corrigé RSV-S10-01 disposera de sa clé secondaire sur toutes les lignes |
| 2 | Invariant `garantie.solde_actuel = Σ(crédit − débit)` | ✅ **3/3 PASS** (`550f1d84…` 2100,00 ; `ef87b3aa…` 600,00 ; `01754057…` 600,00) |
| 3 | Colonne `paiement.garantie_movement_id` préexistante | ✅ **Absente** (`information_schema.columns` → 0) — V21 s'appliquera proprement |
| 4 | Cas OBS-S10-01 (garantie avec ≥ 2 mouvements le même jour) | ✅ **0 cas** — l'observation cosmétique consignée au Gate (§8) est confirmée **sans objet en Production**, comme prédit |

## 4. Rollback disponible

| Scénario | Procédure |
|---|---|
| Avant migration V21 (état actuel) | `pg_restore` du dump `loyertracker-20260704-170836.dump` |
| Après déploiement `1.8.0` (V21 appliquée) | **Rollback applicatif seul viable** : retour du tag à `sha-6a358eb6` — l'ancien code ignore la colonne V21, qui reste en place. Limite documentée (Gate §3 Rollback) : les mouvements `RETENUE_LOYER`/`COMPLEMENT` créés entre-temps resteraient en base, cohérents mais non « explicables » dans l'UI `1.7.0`. Restauration du backup en dernier recours |

## 5. Conditions documentaires du Préflight

| Condition | Statut |
|---|---|
| Release notes `1.8.0` | ✅ Rédigées — `docs/release-notes-v1.8.0.md` |
| `CHANGELOG.md` promu `[1.8.0]` daté (2026-07-04), liens de comparaison mis à jour | ✅ Fait (même PR que ce rapport) |

## 6. Verdict

| Critère | Statut |
|---|---|
| Services 8/8 Up, 4/4 healthy, restart=0 | ✅ PASS |
| Tag `sha-6a358eb6` conforme à la Production `1.7.0` | ✅ PASS |
| Flyway 20/20 | ✅ PASS |
| Prometheus 5/5 up ; Alertmanager `[]` (après résorption qualifiée) | ✅ PASS |
| Capacité hôte (disque 31 Gio, mémoire 2,1 Gio, charge 0,33) | ✅ PASS |
| Backup `pg_dump -Fc` + globals créés, permissions 600, SHA-256 consignés | ✅ PASS |
| `pg_restore --list` OK (740 entrées) | ✅ PASS |
| Vérifications §4.2 du Gate (4/4, lecture seule) | ✅ PASS |
| Release notes + CHANGELOG | ✅ PASS |

**Préflight PASS. Backup vérifié. Toutes les conditions de Préflight du Gate Production `1.8.0`
(`gate-production-v1.8.0-decision.md` §7) sont satisfaites.**

Prochaine étape autorisée : **déploiement technique `1.8.0`** (`api` + `nginx`, migration V21
additive), sous décision distincte.
