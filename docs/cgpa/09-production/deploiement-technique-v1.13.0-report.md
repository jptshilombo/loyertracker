# Rapport Déploiement Technique — Release `1.13.0` (EP-16 Sprint N — Fondation notifications)

| Champ | Valeur |
|---|---|
| Date | 2026-07-22 |
| Fenêtre | ~18:14–18:15 UTC |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Candidat | `sha-e4744d92` |
| Tag précédent | `sha-359f4d63` (`1.12.0`) |
| Autorisation PO | Explicite, après Gate Production GO (`gate-production-sprint-n-ep16-decision.md`) et Préflight PASS (`preflight-backup-v1.13.0-report.md`) |
| Verdict | **PASS technique** |

## Exécution ciblée

Le dépôt Production a été avancé par fast-forward de `c14cdc2` à `5d59b5f` (50 fichiers). Aucun
écart sur `docker-compose.yml`/`docker-compose.prod.yml` (`git diff --stat c14cdc2 HEAD` vide sur
ces deux fichiers) ; seul `.env.example` évolue (quatre indicateurs de notifications, non
secrets). Les images candidates ont été tirées et leurs digests confirmés identiques au
Gate/Préflight avant toute bascule :

- API : `sha256:9e9a331d3a7ee8a65e17235ead3f60c4b916d46086d9d3dd2d0c263ddabfe815` ;
- Web : `sha256:c797934a8d5e629a6e532c50790dad78495a5e1aa5e7d42273e7fc6ccd00d41b`.

Le tag `.env` a été basculé vers `sha-e4744d92` (`.env.bak-pre-1.13.0` disponible depuis le
Préflight ; aucune nouvelle variable requise, les quatre indicateurs de notifications disposant
de valeurs de repli sûres dans `application.yml`). Seuls `api` et `nginx` ont été tirés et
recréés (`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-deps api
nginx`) ; l'avertissement Compose « conteneurs orphelins » (services de l'overlay monitoring,
lancés séparément) est attendu et ignoré, comme à chaque déploiement précédent. PostgreSQL,
Keycloak et les quatre services de monitoring sont restés actifs et n'ont subi aucun redémarrage
(`RestartCount=0` confirmé sur `postgres`/`keycloak`, inchangé). Aucune commande Docker globale
n'a été exécutée.

## Migration et contrôles

Flyway a appliqué `27 - ep16 notifications fondation` avec succès (27/27, aucun échec). Les cinq
tables V27 sont confirmées présentes : `notification_preference`, `notification_event`,
`notification_outbox`, `notification_delivery`, `notification_template`.

| Contrôle | Résultat |
|---|---|
| RLS `ENABLE`+`FORCE` (4 tables tenant-scopées) | `notification_preference`/`notification_event`/`notification_outbox`/`notification_delivery` : `relrowsecurity=t`, `relforcerowsecurity=t` |
| RLS `notification_template` (référentiel global) | `relrowsecurity=f`, `relforcerowsecurity=f` — conforme au design (pas de cloisonnement bailleur) |
| Compteurs post-migration | `notification_preference`=0, `notification_event`=0, `notification_outbox`=0, `notification_delivery`=0, `notification_template`=0 — aucune activité applicative n'a encore alimenté l'Outbox (aucune opération métier quittance/garantie/paiement/bail exécutée depuis le redéploiement), cohérent avec la condition du Gate (0 Outbox/Delivery sans préférence) |

## Contrôles finaux

| Contrôle | Résultat |
|---|---|
| Services | 8/8 actifs, 4/4 healthy, `RestartCount=0` sur `api`/`nginx` (conteneurs recréés, compteur repart à 0) ; `postgres`/`keycloak` inchangés (`RestartCount=0`, non redémarrés) |
| Images actives | `sha-e4744d92`, digests exacts du Gate/Préflight (vérifiés sur les conteneurs) |
| Flyway | V27 appliquée, 27/27 |
| `/healthz` | 200 |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |
| Prometheus / Alertmanager | 5/5 `up` ; 0 alerte active (le `BackupHeartbeatMissing` constaté au Préflight a disparu après le heartbeat poussé par le backup) |
| 5xx / `ERROR` depuis le redéploiement | 0 / 0 |
| `bailleur-test` | `enabled=false` (inchangé, aucune réactivation à ce stade) |
| Rollback | `sha-359f4d63` (images encore présentes localement) — **viable même après application de V27** (additive), sans restauration de backup requise pour un simple retour arrière applicatif |

**Déploiement technique PASS.** La validation finale (smoke Production, réactivation temporaire
autorisée de `bailleur-test@test.local`/`directAccessGrants`) reste une étape distincte, requérant
une autorisation PO explicite dédiée avant exécution — même discipline que toutes les releases
précédentes. `PRODUCTION_DEPLOYED` n'est pas encore prononcé — en attente de cette validation
finale. L'activation de canaux externes (WhatsApp/SMS) reste interdite jusqu'au GO du Sprint N+2
(K8, ADR-18) ; `NotificationProvider` en service reste exclusivement `NoopNotificationProvider`.
