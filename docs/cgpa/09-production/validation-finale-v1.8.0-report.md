# Rapport Validation Finale — Release `1.8.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-04 |
| Heure UTC | ~16:28–16:45 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Release | `1.8.0` — `sha-2c5f43c7` |
| Smoke | **59 PASS / 0 FAIL — au premier passage** |
| Nettoyage | **0 résidu vérifié** (DB + Keycloak) |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint le 2026-07-04 à ~16:45 UTC** |

## 1. Smoke Production

`bailleur-test@test.local` (`43443d1e-…` KC) : réactivé temporairement (`enabled=true`) **sur
instruction explicite du PO**, pattern identique à `1.2.1`→`1.7.0` — redésactivé après le run
(§2). Invocation canonique :

```
sudo env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

**Résultat : 59 PASS / 0 FAIL au premier passage** — premier smoke Production de l'historique
sans aucun correctif préalable (le compteur Flyway 21 était aligné depuis la PR #171, et le
dépôt hôte synchronisé au Préflight). Couverture : sanity Flyway 21/21, pool
`loyertracker_api` NOSUPERUSER/NOBYPASSRLS, JWT réels, parcours S01→S04, RGPD
export/effacement, isolation cross-tenant, garde-fous AuthN/ports. Échafaudage
`directAccessGrants` révoqué automatiquement par le script (vérifié `false` après).

## 2. Nettoyage transactionnel

### Scope identifié (RUN_ID `1783182448`)

Aucun orphelin d'une session antérieure (vérifié par requête ciblée sur les patrons
`*-smoke-%@test.local` avant nettoyage).

| Entité smoke | ID | Action |
|---|---|---|
| `bailleur-test@test.local` | `c7296c69-…` (DB) / `43443d1e-…` (KC) | Données smoke purgées (1 patrimoine, 1 bien, 1 bail + dépendances), bailleur conservé puis **redésactivé** KC |
| `bailleur2-smoke-1783182448@test.local` | `1d769827-…` (DB) / `f01adb27-…` (KC) | Données + bailleur supprimés (DB + KC) |
| `gest-smoke-1783182448@test.local` | `98f131a3-…` (DB) / `113e4998-…` (KC) | Supprimé (DB + KC) |

SQL exécuté en **transaction atomique** (même patron que `1.7.0`) : honoraires (9), paiements
(9), alertes (6), audit_log (3), affectation (1), invitation (1), bail (1), bien (1),
patrimoines (2), gestionnaire (1), bailleur (1). **Aucune table `garantie`/`garantie_movement`
affectée** — les 3 garanties réelles appartiennent à `jptshilombo`, non touchées.

### Compteurs post-nettoyage — identiques à la baseline pré-smoke

| Table | Avant smoke | Après nettoyage | Conforme |
|---|---|---|---|
| bailleurs | 2 | **2** | ✅ jptshilombo + bailleur-test |
| gestionnaires | 1 | **1** | ✅ gest-e9 (réel) |
| patrimoines | 1 | **1** | ✅ |
| biens / baux | 3 / 3 | **3 / 3** | ✅ |
| paiements | 75 | **75** | ✅ |
| honoraires / affectations / invitations | 0 / 0 / 0 | **0 / 0 / 0** | ✅ |
| alertes | 81 | **81** | ✅ |
| audit_log | 2 | **2** | ✅ |
| **garanties / mouvements** | 3 / 3 | **3 / 3** | ✅ **inchangés** |

### Keycloak

`bailleur2-smoke` et `gest-smoke` supprimés ; `bailleur-test` **redésactivé**
(`enabled=false` vérifié) ; aucun compte smoke restant ; `directAccessGrantsEnabled=false`
vérifié sur `loyertracker-spa`.

## 3. État final de la stack

| Contrôle | Résultat |
|---|---|
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 |
| Tag | `sha-2c5f43c7` (`.env` persisté, digests conformes au Gate) |
| Erreurs API depuis le déploiement | **1**, qualifiée : `duplicate key` du test d'inscription du smoke (contrainte d'unicité exercée volontairement — même qualification que `1.7.0` T0) |
| Prometheus / Alertmanager | 5/5 up / `[]` |
| Site public | `https://loyertracker.loyerpro.org` → HTTP 200 |

## 4. Verdict

| Critère | Statut |
|---|---|
| Smoke 59/0 au premier passage | ✅ PASS |
| Nettoyage 0 résidu (DB + KC), garanties réelles intactes | ✅ PASS |
| `bailleur-test` redésactivé, échafaudage révoqué | ✅ PASS |
| 1 erreur API qualifiée attendue, 0 inattendue | ✅ PASS |
| Observabilité nominale, site public UP | ✅ PASS |

**Validation finale PASS — `PRODUCTION_DEPLOYED` atteint le 2026-07-04 (~16:45 UTC).**

Prochaine étape : **hypercare `1.8.0`** (T0 + T+12/T+24 selon la pratique de l'hôte éteint la
nuit, précédent qualifié `1.7.0`) puis clôture de release — décisions et exécutions distinctes.
Plan à créer : `plan-etape-hypercare-v1.8.0.md`.
