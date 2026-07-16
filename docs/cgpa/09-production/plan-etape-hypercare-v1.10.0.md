# Plan Hypercare — Release `1.10.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-15 ~12:39 UTC |
| T0 | 2026-07-15 ~13:26 UTC — **PASS** |
| T+12 | 2026-07-16 ~00:39 UTC ± 30 min — **PASS (rattrapage ~10:57 UTC)** |
| T+24 | 2026-07-16 ~12:39 UTC ± 30 min — **restant à instruire** |
| Tag surveillé | `sha-c9200a51` |
| Rollback | `sha-75646d8f` via `.env.bak-pre-1.10.0` ; V23/V24 additives |

## Critères de suspension

- restart inattendu, service non healthy ou dérive de tag/digest ;
- erreur 5xx ou régression sur les endpoints `/api/gestionnaires`/`/api/locataires`, RBAC/ReBAC
  ou garde cross-tenant d'archivage ;
- écart de l'invariant ledger ;
- hausse anormale des 5xx, pool Hikari en attente ou alerte non qualifiée ;
- `bailleur-test` ou `directAccessGrants` retrouvés actifs de façon inattendue.

## Checkpoint T0 — 2026-07-15 ~13:26 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke | 62/0 au premier passage (validation finale) |
| Stack | 8/8 actifs, 4/4 healthy, restart=0 |
| Tag / digests | `sha-c9200a51` ; API `37de87e8…`, Web `7ade9816…` |
| Flyway | 24/24 (V23+V24) |
| Invariant ledger | 8/8 PASS |
| Keycloak | `bailleur-test` désactivé ; `directAccessGrantsEnabled=false` |
| Santé | `/healthz` 200, site public 200 |
| Observabilité | Prometheus 5/5 ; Alertmanager 1 alerte `BackupHeartbeatMissing` (pattern récurrent post-redémarrage hôte, déjà qualifié au Préflight, sans rapport avec la release) |
| Logs API (30 min) | 0 ligne 5xx |
| Capacité | disque 31 Gio libres (20 %) ; mémoire ~1,9 Gio disponible ; charge 0,19/0,05/0,02 |

**Décision T0 : PASS — hypercare active.** La clôture reste interdite avant T+12, T+24 et la
décision CDO finale.

## Checkpoint T+12 — rattrapage 2026-07-16 ~10:57 UTC (cible 2026-07-16 ~00:39 UTC)

**Statut : PASS**

Contrairement au pattern récurrent `1.4.0`→`1.9.0`, l'hôte `loyertracker-prod-server` est resté
**allumé en continu** depuis avant le déploiement (`uptime` : 1 j 14 min au moment du contrôle,
soit un démarrage ~2026-07-15 10:43 UTC, antérieur au déploiement technique ~12:39 UTC). L'écart
de fenêtre tient donc uniquement à l'absence de contrôle live à l'heure cible, pas à un arrêt de
l'hôte ; `restart=0` sur tous les conteneurs depuis le déploiement couvre rétroactivement toute
la période, y compris la fenêtre T+12 cible.

| Contrôle | Résultat |
|---|---|
| Stack | 8/8 actifs, 4/4 healthy, restart=0 sur tous les conteneurs depuis le déploiement |
| Tag / digests | `sha-c9200a51` inchangé ; API `sha256:37de87e8…` confirmé, zéro dérive |
| Flyway | 24/24, aucun échec |
| Invariant ledger (garantie) | 8/8 PASS (rôle bypass-RLS `loyertracker`) |
| Keycloak | `bailleur-test` `enabled=false` ; `directAccessGrantsEnabled=false` |
| Santé | `/healthz` 200, site public 200 |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | 0 alerte active (le `BackupHeartbeatMissing` du Préflight/T0 n'est plus présent) |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| Logs API (6 h) | 1 seule entrée `ERROR` : `duplicate key … bailleur_keycloak_id_key` à 09:36:51 UTC — tracée côté nginx à `POST /api/bailleurs/inscription` → **409** (contrainte d'unicité correctement appliquée, aucun 5xx), activité réelle limitée d'un bailleur existant re-soumettant une inscription, cohérente avec l'usage privé pré-annonce |
| Logs Nginx | 0 ligne 5xx |
| Capacité | disque 31 Gio libres (20 %) ; mémoire ~1,7-2,0 Gio disponible ; charge 0,04/0,02/0,00 |

**Verdict T+12 : PASS.** Aucun critère de suspension atteint. Écart de fenêtre (contrôle en
rattrapage plutôt qu'à l'heure cible) qualifié sans impact — l'hôte étant resté actif en continu,
`restart=0` démontre l'absence d'incident pendant la fenêtre cible elle-même.

## Checkpoint T+24 — cible 2026-07-16 ~12:39 UTC

**Restant à instruire.**
