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

## Checkpoint combiné T+12 / T+24 — rattrapage 2026-07-08 ~14:36 UTC

**Statut : PASS sous surveillance**

L'hôte `loyertracker-prod-server` est **intentionnellement éteint** entre les opérations
(produit non annoncé publiquement, politique de coût — pattern déjà appliqué aux hypercare
`1.7.0`/`1.8.0`). Historique CloudTrail des cycles marche/arrêt depuis le déploiement :

| Événement | Horodatage UTC |
|---|---|
| `PRODUCTION_DEPLOYED` (T0) | 2026-07-06 ~17:57 |
| Arrêt | 2026-07-06 18:30 |
| Démarrage | 2026-07-07 14:01 |
| Arrêt | 2026-07-07 14:48 |
| Démarrage (rattrapage) | 2026-07-08 14:28 |

Conséquence : les deux fenêtres cibles sont tombées hôte éteint — **T+12** (cible 2026-07-07
~05:57 UTC, dépassement ~32h40) et **T+24** (cible 2026-07-07 ~17:57 UTC, dépassement ~20h40).
Conformément au §4 du plan (rattrapage explicitement anticipé pour T+12) et au précédent
`1.8.0` (checkpoint combiné hôte éteint la nuit), un **contrôle live unique** couvre les deux
échéances en rattrapage. Aucune alerte Alertmanager n'a été reçue entre-temps (canal Discord
silencieux), et aucun signal de suspension n'a été rapporté par le PO durant l'intervalle.
Qualification de l'écart de fenêtre validée par le PO (instruction explicite de rédiger ce
checkpoint).

| Contrôle | Résultat |
|---|---|
| Stack | 8/8 actifs, 4/4 healthy ; restart=0 **depuis le redémarrage du 2026-07-08 14:28 UTC** (pas de restart inattendu observé sur les fenêtres où l'hôte était actif) |
| Tag / digests | `LOYERTRACKER_TAG=sha-75646d8f` inchangé ; API `sha256:3c2279…71aff`, Web `sha256:f0146f…19a04` — identiques au rapport de déploiement, zéro dérive |
| Flyway | 22/22, aucun échec |
| Invariant ledger | 3/3 PASS (`solde_actuel = Σ(credit-debit)` : 2100,00/2100,00 ; 600,00/600,00 ; 600,00/600,00) |
| Données | 2 bailleurs métier (`jptshilombo@gmail.com`, `annietshibola@gmail.com`) + `bailleur-test` désactivé ; 1 gestionnaire ; 2 patrimoines (activité métier réelle limitée constatée depuis le go-live, cohérente avec un usage privé pré-annonce) ; 3 biens ; 3 baux ; 75 paiements ; **0 quittance** (aucune émission réelle depuis le go-live) |
| Keycloak | `bailleur-test` `enabled=false` ; `loyertracker-spa.directAccessGrantsEnabled=false` (vérifié via `kcadm.sh`) |
| Santé | web `200`, `/actuator/health` `UP` |
| Prometheus | 5/5 cibles `up` (`loyertracker-api`, `blackbox-postgres`, `blackbox-keycloak`, `prometheus`, `pushgateway`) |
| Alertmanager | `[]` — aucune alerte active |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| Logs API (10 min) | aucune erreur/exception inattendue |
| Logs Nginx (30 min) | 0 ligne 5xx |
| Capacité | disque 31 Gio libres (20 %) ; mémoire 1,2 Gio disponible ; charge 0,29/0,23/0,16 |
| Heartbeat backup / cron 02h15 | non joué durant les fenêtres hôte éteint — **qualifié sans impact**, pattern récurrent `1.4.0`→`1.8.0`, résorption au prochain passage du cron pendant que l'hôte est actif |

**Verdict combiné : PASS sous surveillance.** Aucun critère de suspension du §"Critères de
suspension" atteint (pas de restart inattendu sur les fenêtres actives, pas de 5xx, pas de
dérive de tag/digest, invariant ledger cohérent, aucune alerte non qualifiée). Écart de fenêtre
qualifié par le PO (hôte volontairement éteint, cause connue et sans rapport avec la santé de la
release). **Décision CDO de clôture `1.9.0` : voir `cloture-release-v1.9.0.md`.**
