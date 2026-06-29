# Rapport Validation Finale — Release `1.3.0`

| Champ | Valeur |
|---|---|
| Date | 2026-06-29 |
| `PRODUCTION_DEPLOYED` | **2026-06-29 15:31 UTC** |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-a42d860d` |
| Digest API | `sha256:c3d89f0d6da5cfad55daa0ad921df4be0539757c8ca3384110323e7425290749` |
| Digest Web | `sha256:c30708984117717b8bad0b6447fd009b561680376c7d3a7d60ffa81e1ba8c4ba` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## 1. Smoke test Production

### Prérequis Keycloak

`bailleur-test@test.local` (43443d1e) : `"enabled": true` ✅ — aucune réactivation nécessaire.

### Smoke — 47 PASS / 0 FAIL (15:22 UTC)

```
BASE=https://localhost:18443 \
CACERT=/home/ubuntu/loyertracker/infra/nginx/certs/localhost.pem \
bash /home/ubuntu/loyertracker/infra/smoke/smoke-stack.sh
```

| Section | Résultat |
|---|---|
| 0. Sanity (stack, Flyway V1-V15, pool loyertracker_api NOBYPASSRLS) | **5/5 PASS** |
| 1. JWT Keycloak réel bailleur via Nginx TLS | **2/2 PASS** |
| 2. Parcours bailleur (inscription 409, patrimoine, bien, bail) | **4/4 PASS** |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | **4/4 PASS** |
| 4. Affectation patrimoine, échéances SECURITY DEFINER, pointage, honoraires | **11/11 PASS** |
| 5. Alertes (PREAVIS J+75), audit bailleur | **6/6 PASS** |
| 6. Scoping gestionnaire | **4/4 PASS** |
| 7. Isolation cross-tenant live (2e bailleur) | **9/9 PASS** |
| 8. Garde-fous AuthN/ports | **2/2 PASS** |
| **Total** | **47 PASS / 0 FAIL** ✅ |

Échafaudage révoqué par le script : `directAccessGrants OFF` (loyertracker-spa).

### Détail Flyway (§0)

**15 migrations appliquées (V1→V15)** — inchangé par rapport à `1.2.1` (aucune migration `1.3.0`).

## 2. Nettoyage transactionnel

### Scope identifié

| Bailleur smoke | ID bailleur (table) | Action |
|---|---|---|
| `bailleur-test@test.local` | `c7296c69` | Données smoke purgées, bailleur conservé puis désactivé KC |
| `bailleur2-smoke-1782742536@test.local` | `ebf0cccd` | Données + bailleur supprimés (DB + KC) |
| `bailleur2-smoke-1782551256@test.local` (orphelin) | `18fa1483` | Données + bailleur supprimés (DB + KC) |
| `gest-smoke-1782742536@test.local` | gestionnaire table | Supprimé (DB + KC) |
| `gest-smoke-1782551256@test.local` (orphelin) | gestionnaire table | Supprimé (DB + KC) |

### SQL exécuté (transaction atomique)

Suppression dans l'ordre FK (honoraire → paiement → alerte → audit_log → affectation → garantie
→ bail → invitation → gestionnaire → bien → patrimoine → bailleur bailleur2).

### Compteurs post-nettoyage

| Table | Avant | Après | Attendu |
|---|---|---|---|
| bailleurs | 4 | **2** | jptshilombo + bailleur-test ✅ |
| patrimoines | 5 | **1** | Patrimoine principal (jptshilombo) ✅ |
| biens | 5 | **3** | Biens jptshilombo ✅ |
| baux | 5 | **3** | Baux jptshilombo ✅ |
| affectations | 2 | **0** | ✅ |
| paiements | 95 | **75** | Paiements jptshilombo ✅ |
| honoraires | 20 | **0** | ✅ |
| alertes | 94 | **81** | Alertes jptshilombo ✅ |
| gestionnaires | 3 | **1** | gest-e9-e9test@test.local (réel) ✅ |

### Keycloak

| Compte | Action | Résultat |
|---|---|---|
| `bailleur2-smoke-1782742536` (8c62b057) | Supprimé | ✅ |
| `bailleur2-smoke-1782551256` (c240bcd6) | Supprimé | ✅ |
| `gest-smoke-1782742536` (230d1c37) | Supprimé | ✅ |
| `gest-smoke-1782551256` (dans KC) | Supprimé | ✅ |
| `bailleur-test@test.local` (43443d1e) | Désactivé (`enabled=false`) | ✅ |

## 3. Persistance `.env`

```
LOYERTRACKER_TAG=sha-a42d860d
```

Mis à jour sur `loyertracker-prod-server` (`sed -i` sur `/home/ubuntu/loyertracker/.env`).
Backup `.env.bak-pre-1.3.0` créé (permissions 600) avant modification.
Vérification : `grep LOYERTRACKER_TAG .env` → `sha-a42d860d` ✅, permissions 600 ✅

## 4. Note observabilité — alerte `BackupHeartbeatMissing`

L'alerte `BackupHeartbeatMissing` était active en début de validation finale : le Pushgateway
(volatil, in-memory) avait perdu le heartbeat backup depuis le dernier redémarrage. Cette alerte
est un pattern connu (`prod-state.md` §0C) — non lié au déploiement `1.3.0`.

**Résolution :** heartbeat poussé manuellement vers le Pushgateway (`loyertracker_backup_last_success_epoch`).
Prometheus a scrapé la métrique ; Alertmanager revenu à `[]` avant la fin de la validation.
Le cron backup (`15 2 * * *`) assurera les heartbeats automatiques dès la prochaine exécution.

## 5. Contrôles post-persistance finaux

| Contrôle | Résultat |
|---|---|
| `LOYERTRACKER_TAG=sha-a42d860d` dans `.env` | ✅ |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ |
| Actuator `{"status":"UP"}` | ✅ |
| Prometheus 5/5 `up` | ✅ |
| Alertmanager `[]` | ✅ |

## 6. `PRODUCTION_DEPLOYED` — atteint

**2026-06-29 15:31 UTC** — release `1.3.0` (`sha-a42d860d`) déployée et validée en Production.

Prochaines étapes :
- Hypercare T0 (immédiat), T+12, T+24
- Décision CDO → clôture `1.3.0`
- Post-clôture : CHANGELOG `[Non publié]` → `[1.3.0] — 2026-06-29`, mise à jour `project-state.md`
  et `prod-state.md`
