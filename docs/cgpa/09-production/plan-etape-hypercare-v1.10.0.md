# Plan Hypercare — Release `1.10.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-15 ~12:39 UTC |
| T0 | 2026-07-15 ~13:26 UTC — **PASS** |
| T+12 | 2026-07-16 ~00:39 UTC ± 30 min — **restant à instruire** |
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

## Checkpoint T+12 — cible 2026-07-16 ~00:39 UTC

**Restant à instruire.** Compte tenu du pattern récurrent documenté depuis `1.4.0` (l'hôte
`loyertracker-prod-server` est intentionnellement éteint hors opération, produit non annoncé
publiquement), un écart de fenêtre est probable si l'hôte est éteint à l'heure cible — auquel
cas un contrôle live en rattrapage sera qualifié, comme pour `1.7.0`→`1.9.0`.

## Checkpoint T+24 — cible 2026-07-16 ~12:39 UTC

**Restant à instruire.**
