# Plan Hypercare — Release `1.12.0` (Sprint C EP-15)

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-19 ~10:51 UTC (déploiement technique `api`/`nginx`) — voir correction méthodologique ci-dessous |
| T0 | 2026-07-19 ~11:27 UTC — **PASS** |
| T+12 | 2026-07-19 ~22:51 UTC ± 30 min |
| T+24 | 2026-07-20 ~10:51 UTC ± 30 min |
| Tag surveillé | `sha-359f4d63` |
| Rollback | `sha-cba13f52` **non viable seul** après V26 (non additive, RSV-EP15-03) — restauration du backup post-migration `loyertracker-20260719-115220.dump` requise |

## Correction méthodologique — horodatages `PRODUCTION_DEPLOYED`

Les rapports `deploiement-technique-v1.12.0-report.md` (« Fenêtre 11:48–11:52 UTC ») et
`validation-finale-v1.12.0-report.md` (« `PRODUCTION_DEPLOYED` ~11:57 UTC »), ainsi que
`prod-state.md` §0M et `project-state.md`, portent des horodatages **estimés sans vérification
`date -u` au moment des faits** — ni même tirés de `uptime` (piège déjà documenté au T+24 de
`plan-etape-hypercare-v1.10.0.md`), simplement estimés au fil de la session.

**Preuve exacte** (`docker inspect --format='{{.State.StartedAt}}' loyertracker-api-1`/`-nginx-1`,
vérifiée au T0 ci-dessous) : les conteneurs `api`/`nginx` ont démarré à
**`2026-07-19T10:51:05Z`** — soit **~57 minutes plus tôt** que l'horodatage écrit dans les
rapports existants.

**Aucun impact sur le fond** : la santé, Flyway 26/26, le backfill 8/8 et `restart=0` sont tous
vérifiés indépendamment de l'heure exacte, et restent valides. Seule la précision de l'horodatage
`PRODUCTION_DEPLOYED` est corrigée ici, sans réécrire les rapports déjà mergés dans `main`
(interdiction CLAUDE.md de réécrire l'historique) — ce document fait foi pour l'horodatage
corrigé. **Leçon renforcée** : ne jamais estimer un horodatage UTC en cours de session ; toujours
exécuter `date -u` (ou `docker inspect StartedAt`, format ISO 8601 UTC natif) au moment du
contrôle.

## Critères de suspension

- restart inattendu, service non healthy ou dérive de tag/digest ;
- erreur 5xx ou régression sur `POST /api/locataires`, `POST .../baux` (contrat `locataireId`),
  `DELETE /api/locataires/{id}/effacement`, RBAC/ReBAC ou garde `@authz.peutAccederBien` du
  nouvel endpoint `GET /api/biens/{bienId}/locataires` ;
- écart du backfill V26 (`bail.locataire_id` NULL, orphelin, ou colonnes legacy réapparues) ;
- hausse anormale des 5xx, pool Hikari en attente ou alerte non qualifiée ;
- `bailleur-test` ou `directAccessGrants` retrouvés actifs de façon inattendue.

## Checkpoint T0 — 2026-07-19 ~11:27 UTC (`date -u` vérifié)

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke | 63/0 au premier passage (validation finale) |
| Stack | 8/8 actifs, 4/4 healthy, restart=0 |
| Tag / digests | `sha-359f4d63` ; API `ea040492bb5a…`, Web `e70ebc7ba7d7…` |
| Conteneurs `api`/`nginx` — `StartedAt` exact | `2026-07-19T10:51:05Z` (référence corrigée du déploiement technique) |
| Flyway | 26/26 (V26) |
| Backfill V26 | 8/8 baux avec `locataire_id`, 0 orphelin ; colonnes `locataire_nom`/`locataire_email` toujours absentes |
| Keycloak | `bailleur-test` désactivé ; `directAccessGrantsEnabled=false` sur `loyertracker-spa` |
| Santé | `/healthz` 200 (vérifié avec `-k`, certificat local auto-signé), site public 200 |
| Observabilité | Prometheus 5/5 ; Alertmanager 0 alerte active |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| Logs Nginx (15 min) | 0 ligne 5xx |
| Logs API (15 min) | 0 entrée `ERROR` |
| Capacité | disque 30 Gio libres (21 %) ; mémoire ~1,9 Gio disponible ; charge 0,33/0,13/0,10 |

**Décision T0 : PASS — hypercare active.** La clôture reste interdite avant T+12, T+24 et la
décision CDO finale. Prochain checkpoint cible : T+12 le 2026-07-19 ~22:51 UTC ± 30 min.
