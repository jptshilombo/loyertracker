# Plan Hypercare — Release `1.8.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-04 ~16:45 UTC |
| T0 | 2026-07-04 ~16:35 UTC (checkpoint réalisé dans la session de déploiement) |
| T+12 | 2026-07-05 ~04:45 UTC ± 30 min — **fenêtre probablement inexécutable** (hôte volontairement éteint la nuit, pratique documentée ; précédent qualifié `1.7.0`) : à rattraper au premier démarrage de l'hôte, avec qualification PO |
| T+24 | 2026-07-05 ~16:45 UTC ± 30 min |
| Tag surveillé | `sha-2c5f43c7` |
| Rollback disponible | **Rollback applicatif seul viable** (V21 additive — contraste avec `1.7.0`/RSV-S9-03) : `cp .env.bak-pre-1.8.0 .env` puis recréation ciblée `api`+`nginx` sur `sha-6a358eb6`. Restauration du backup (`loyertracker-20260704-170836.dump`) en dernier recours |

## Critères de suspension (tout FAIL → évaluation immédiate)

- Restart inattendu d'un conteneur applicatif (`api`, `nginx`, `keycloak`, `postgres`).
- Régression fonctionnelle sur le parcours bailleur/gestionnaire (dashboard vide, erreur sur le
  panneau Garanties, historique des mouvements vide ou désordonné pour des mouvements de jours
  distincts).
- Erreur 500 sur tout endpoint garantie (`retenue-loyer`, `complement`, `mouvements`,
  `mouvements/export`, `restitution`).
- Écart entre `garantie.solde_actuel` et Σ des mouvements (invariant central du ledger).
- Paiement couvert par une retenue sans `garantie_movement_id` lié, ou transition de statut
  incohérente après une retenue réelle.
- Augmentation anormale des erreurs 5xx sur Prometheus.
- Alerte Alertmanager hors `BackupHeartbeatMissing` (récurrente connue et qualifiée — pushgateway
  purgé au boot + cron 02:15 UTC non joué hôte éteint, cf. RSV-T24-01 et précédents
  `1.4.0`/`1.7.0` ; exclue des critères de suspension par ce plan).

## Rollback si nécessaire

Contrairement à `1.7.0`, un **rollback applicatif seul est sûr** :

```
cd ~/loyertracker
cp .env.bak-pre-1.8.0 .env
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx
```

La colonne `paiement.garantie_movement_id` (V21) reste en place, ignorée par l'ancien code.
Limite documentée (Gate `1.8.0` §3) : les mouvements `RETENUE_LOYER`/`COMPLEMENT` créés entre le
déploiement et le rollback resteraient en base — soldes et invariant cohérents, mais transitions
de paiement non « explicables » dans l'UI `1.7.0`. Restauration complète
(`infra/backup/restore-postgres.sh --yes ~/loyertracker-backups/daily/loyertracker-20260704-170836.dump`)
uniquement en dernier recours (annule aussi V21).

---

## Checkpoint T0 — 2026-07-04 ~16:35 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke 59/0 au premier passage | ✅ PASS — `validation-finale-v1.8.0-report.md` |
| `LOYERTRACKER_TAG=sha-2c5f43c7` persisté dans `.env` | ✅ PASS |
| `bailleur-test` désactivé dans Keycloak | ✅ PASS (`enabled: false`) |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ PASS |
| Flyway 21/21 | ✅ PASS |
| Digest API | ✅ `sha256:bab66aa3…` — conforme au Gate Production |
| Digest Web | ✅ `sha256:9c8915f0…` — conforme au Gate Production |
| Actuator `{"status":"UP"}` | ✅ PASS |
| Prometheus 5/5 `up` | ✅ PASS |
| Alertmanager | ✅ `[]` — 0 alerte active |
| Capacité hôte | ✅ disque 31 Gio libres (19 %), mémoire 2,0 Gio dispo, charge 0,01/0,18/0,16 |
| p99 latence (10 min) | ✅ ~81 ms (fenêtre incluant la charge du smoke qui vient de s'achever — même profil que le T0 `1.7.0`) |
| 5xx rate (10 min) | ✅ 0 (aucun point de données) |
| Hikari pending | ✅ 0 |
| Heartbeat backup | ✅ âge ~26 min (poussé au Préflight) — largement sous le seuil (26 h) |
| Logs API — erreurs depuis le déploiement | ✅ 1× `duplicate key` — attendue (smoke POST inscription 409, même qualification que `1.7.0`), aucune erreur inattendue |
| Invariant `garantie.solde_actuel = Σ mouvements` | ✅ PASS — 3/3 garanties cohérentes |

**Décision T0 : PASS — hypercare sous surveillance.**

T+12 prévu : **2026-07-05 ~04:45 UTC** (tolérance ±30 min) — rattrapage qualifié attendu si
l'hôte est éteint (précédent `1.7.0`).
T+24 prévu : **2026-07-05 ~16:45 UTC** (tolérance ±30 min).

---

## Post-clôture (après décision CDO GO au T+24)

- Mettre à jour `docs/project-state.md` (bandeau de clôture).
- Mettre à jour `docs/prod-state.md` §0I (statut clôture).
- Créer `docs/cgpa/09-production/cloture-release-v1.8.0.md`.
- Statuer sur OBS-S10-01 (observation cosmétique — traiter via critère métier secondaire dans un
  sprint ultérieur, ou accepter en l'état).
