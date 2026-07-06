# Plan Hypercare — Release `1.9.0`

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-06 ~17:57 UTC |
| T0 | 2026-07-06 ~17:58 UTC — **PASS** |
| T+12 | 2026-07-07 ~05:57 UTC ± 30 min ; rattrapage à qualifier si l'hôte est éteint |
| T+24 | 2026-07-07 ~17:57 UTC ± 30 min |
| Tag surveillé | `sha-75646d8f` |
| Rollback | `sha-2c5f43c7` via `.env.bak-pre-1.9.0` ; V22 additive |

## Critères de suspension

- restart inattendu, service non healthy ou dérive de tag/digest ;
- erreur 5xx ou régression sur émission, PDF, QR, vérification ou téléchargement public ;
- incohérence `content_hash`/`pdf_hash`, fuite de token ou comportement d'oracle ;
- écart de l'invariant ledger ;
- hausse anormale des 5xx, pool Hikari en attente ou alerte non qualifiée.

## Checkpoint T0 — 2026-07-06 ~17:58 UTC

**Statut : PASS**

| Contrôle | Résultat |
|---|---|
| Smoke | 62/0 au premier passage |
| Stack | 8/8 actifs, 4/4 healthy, restart=0 |
| Tag / digests | `sha-75646d8f`, API `3c2279…71aff`, Web `f0146f…19a04` |
| Flyway / V22 | 22/22 ; 3 tables présentes |
| Données | nettoyage 0 résidu ; ledger 3/3 ; 0 quittance avant activité réelle |
| Keycloak | `bailleur-test` désactivé ; `directAccessGrants=false` |
| Santé | `/healthz` ok, Actuator UP, site public 200 |
| Observabilité | Prometheus 5/5, Alertmanager 0 alerte |
| Logs API | 2 erreurs d'unicité attendues du smoke, 0 inattendue |
| Capacité | disque 31 Gio libres ; mémoire 1,9 Gio disponible ; charge 0,23/0,23/0,12 |

**Décision T0 : PASS — hypercare active.** La clôture reste interdite avant T+12, T+24 et la
décision CDO finale.

## Checkpoint T+12

À compléter après exécution.

## Checkpoint T+24

À compléter après exécution. Après PASS, produire la décision CDO de clôture `1.9.0`.
