# Rapport Préflight + Backup + Reconstitution (A1) — Release `1.7.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-03 |
| Heure UTC | ~13:00–13:20 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.7.0` — candidat `sha-6a358eb6` |
| Production actuelle | `1.6.0` — `sha-2da27182` |
| Verdict préflight | **PASS** |
| Verdict backup | **PASS** |
| Verdict reconstitution A1 (RSV-PROD-S9-01) | **PASS — condition exécutée et vérifiée** |

## 1. Contrôles de santé (lecture seule)

### Services

| Service | État | Restart |
|---|---|---|
| `loyertracker-api-1` | `(healthy)` | 0 |
| `loyertracker-nginx-1` | `(healthy)` | 0 |
| `loyertracker-postgres-1` | `(healthy)` | 0 |
| `loyertracker-keycloak-1` | `(healthy)` | 0 |
| `loyertracker-prometheus-1` | Up | 0 |
| `loyertracker-alertmanager-1` | Up | 0 |
| `loyertracker-pushgateway-1` | Up | 0 |
| `loyertracker-blackbox-1` | Up | 0 |

**8/8 conteneurs Up — 4/4 `(healthy)` — restart count 0.**

### Tag en production (avant déploiement technique)

| Élément | Valeur |
|---|---|
| `LOYERTRACKER_TAG` | `sha-2da27182` (Production `1.6.0` — conforme, inchangé depuis hypercare T+12) |

### Flyway

| Contrôle | Résultat |
|---|---|
| Migrations appliquées | **19/19** (V1→V19) — V20 pas encore appliquée |

### Capacité hôte (~13:17 UTC)

| Ressource | Valeur | Seuil |
|---|---|---|
| Disque `/` | 32 Gio libres (18 %) | > 5 % |
| Mémoire disponible | 2,0 Gio | > 500 Mio |
| Charge (load avg 1/5/15 min) | 0,00 / 0,04 / 0,00 | < 1,5 |

### Observabilité

| Contrôle | Résultat |
|---|---|
| Prometheus cibles `up` | 5/5 |
| Alertmanager alertes actives | `[]` — 0 (résorbée à l'hypercare T+12, cf. `plan-etape-hypercare-v1.6.0.md`) |

## 2. Backup pré-déploiement

### Fichiers créés (13:13:31 UTC, via `infra/backup/backup-postgres.sh`)

| Fichier | Taille | SHA-256 |
|---|---|---|
| `loyertracker-20260703-131331.dump` | 316 Kio | `3e83a27796046a4d8d59a22087a9a3d9527f1e6c47acc3a3df78d25b60e88727` |
| `loyertracker-20260703-131331.globals.sql` | 1,1 Kio | `47190864add468385c7abb90c53d84c50cce33feba3de3cbb749ab879bfe6612` |

Permissions 600. Heartbeat de sauvegarde poussé avec succès.

### Vérification `pg_restore --list`

**Total : 730 entrées** — schéma, données, index, RLS, ACL, intégrité confirmée.

## 3. Reconstitution A1 (RSV-PROD-S9-01)

### Contexte

Le Gate Production `1.7.0` (`gate-production-v1.7.0-decision.md` §4) a documenté une anomalie
réelle : 2 des 3 baux de Production (`659ea02c-…`, `cb653273-…`) portent `bail.depot_garantie =
600.00` sans aucune ligne `garantie` correspondante. Sans intervention, la migration V20
(déploiement `1.7.0`) ferait afficher `depotGarantie: 0` pour ces 2 baux (API + export RGPD).

Arbitrage PO (2026-07-03, jordan) : **option A1 retenue** — reconstituer les 2 lignes `garantie`
manquantes **avant** l'application de V20, pendant que `bail.depot_garantie` existe encore, afin
que le backfill de V20 génère ensuite normalement leurs mouvements `DEPOT_INITIAL`.

### Recomptage de contrôle (immédiatement avant intervention)

```sql
SELECT b.id, b.depot_garantie, g.id AS garantie_id
FROM bail b LEFT JOIN garantie g ON g.bail_id = b.id ORDER BY b.date_debut;
```

Résultat : identique à l'analyse du Gate — 2 baux orphelins confirmés, aucun écart depuis.

### Intervention exécutée

```sql
INSERT INTO garantie (bailleur_id, bail_id, montant, type_garantie, date_depot, statut, montant_retenu)
VALUES
  ('a765ae10-3df6-4d3a-921b-e3cc0c44531f', '659ea02c-79b6-4559-8401-2996e1d62152', 600.00, 'CAUTION', '2024-12-05', 'DETENU', 0.00),
  ('a765ae10-3df6-4d3a-921b-e3cc0c44531f', 'cb653273-047d-42f5-a6c1-c73d88c7f5cc', 600.00, 'CAUTION', '2024-12-05', 'DETENU', 0.00);
```

Valeurs reprises de `bail.depot_garantie` (montant) et `bail.date_debut` (date de dépôt),
`type_garantie = 'CAUTION'` par cohérence avec la garantie sœur déjà existante sur le même
bailleur (`550f1d84-…`, même `bailleur_id`, même `date_depot`). `montant_retenu = 0` (aucune
retenue historique connue pour ces 2 baux). Exécuté via `docker compose exec postgres psql`
(rôle `loyertracker`, connexion directe — action administrative hors flux applicatif, même
patron que l'application de l'adresse réelle du patrimoine au Gate Production `1.6.0`).

**Résultat : `INSERT 0 2`.**

### Vérification post-intervention

```sql
SELECT b.id, b.depot_garantie, g.id AS garantie_id, g.montant, g.type_garantie, g.statut
FROM bail b LEFT JOIN garantie g ON g.bail_id = b.id ORDER BY b.date_debut;
```

| bail_id | depot_garantie | garantie_id | montant | type_garantie | statut |
|---|---|---|---|---|---|
| `8c905d18-…` | 2100.00 | `550f1d84-…` (préexistante) | 2100.00 | CAUTION | DETENU |
| `659ea02c-…` | 600.00 | `ef87b3aa-…` (**créée**) | 600.00 | CAUTION | DETENU |
| `cb653273-…` | 600.00 | `01754057-…` (**créée**) | 600.00 | CAUTION | DETENU |

**3/3 baux ont désormais une garantie correspondante, montants cohérents.** Total garanties en
base : 3 (confirmé par `SELECT count(*) FROM garantie`).

**RSV-PROD-S9-01 : condition exécutée et vérifiée — levée.**

## 4. Rollback disponible

| Scénario | Procédure |
|---|---|
| Avant migration V20 (état actuel) | `pg_restore` du dump `loyertracker-20260703-131331.dump` annulerait aussi les 2 lignes `garantie` reconstituées ci-dessus — cohérent, aucun état intermédiaire incohérent |
| Après déploiement `1.7.0` (V20 appliquée) | **Aucun rollback applicatif seul viable** (RSV-S9-03, acceptée par le PO) — seule la restauration du backup pré-déploiement permet un retour arrière sûr, avec perte des mouvements de garantie enregistrés entre le déploiement et le rollback |

## 5. Verdict

| Critère | Statut |
|---|---|
| Services 8/8 Up, 4/4 healthy, restart=0 | ✅ PASS |
| Tag `sha-2da27182` conforme à la Production `1.6.0` | ✅ PASS |
| Flyway 19/19 | ✅ PASS |
| Prometheus 5/5 up, Alertmanager `[]` | ✅ PASS |
| Capacité hôte (disque 32 Gio, mémoire 2,0 Gio, charge 0,00) | ✅ PASS |
| Backup `pg_dump -Fc` créé (316 Kio) | ✅ PASS |
| `pg_restore --list` OK (730 entrées) | ✅ PASS |
| Permissions 600, SHA-256 consigné | ✅ PASS |
| Reconstitution A1 exécutée et vérifiée (RSV-PROD-S9-01) | ✅ **PASS — levée** |

**Préflight PASS. Backup vérifié. RSV-PROD-S9-01 levée par exécution de l'option A1.**

Les deux conditions bloquantes du Gate Production `1.7.0`
(`gate-production-v1.7.0-decision.md` §7) sont désormais satisfaites.

Prochaine étape autorisée : déploiement technique `1.7.0` (`api` + `nginx`, migration V20), sous
décision distincte.
