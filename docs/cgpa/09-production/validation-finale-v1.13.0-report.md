# Rapport Validation Finale — Release `1.13.0` (EP-16 Sprint N — Fondation notifications)

| Champ | Valeur |
|---|---|
| Date | 2026-07-23 |
| `PRODUCTION_DEPLOYED` | **2026-07-23 ~16:13 UTC** (`date -u` vérifié) |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-e4744d92` |
| Digest API | `sha256:9e9a331d3a7ee8a65e17235ead3f60c4b916d46086d9d3dd2d0c263ddabfe815` |
| Digest Web | `sha256:c797934a8d5e629a6e532c50790dad78495a5e1aa5e7d42273e7fc6ccd00d41b` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## Autorisation

Réactivation temporaire de `bailleur-test@test.local` et exécution du smoke Production :
**autorisation PO explicite donnée le 2026-07-23** (« vas-y avec la validation finale »), distincte
des autorisations du Préflight et du déploiement technique — même discipline que toutes les
releases précédentes.

## Anomalie constatée au contrôle d'entrée — corrigée avant smoke

Avant toute action, `bailleur-test@test.local` a été trouvé **`enabled=true`**, alors que le
rapport de déploiement technique du 2026-07-22 l'attestait `enabled=false` sans réactivation
depuis. `directAccessGrantsEnabled` restait `false` sur `loyertracker-spa` (pas de grant direct par
mot de passe possible), mais le compte restait joignable via le flux de connexion navigateur
standard. Cause probable : la stack a redémarré entièrement le 2026-07-23 ~15:45 UTC
(`RestartCount=0` sur les 4 services applicatifs, `StartedAt=2026-07-23T15:45:49Z`) — pattern déjà
documenté (`docs/prod-state.md` gotcha : un cycle d'arrêt/redémarrage complet peut réimporter le
realm et réactiver ce compte par défaut). Aucune trace d'authentification effective sur ce compte
entre le déploiement technique et ce contrôle n'a été recherchée plus avant (hors périmètre de
cette validation) ; le compte a été laissé activé pour le smoke (qui l'exige de toute façon) puis
redésactivé en fin de run (cf. §Nettoyage). **Aucun impact sur `1.13.0`** : condition pré-existante,
sans rapport avec le déploiement V27. Recommandation opérationnelle : surveiller ce gotcha à chaque
redémarrage complet de l'hôte (pattern d'hôte volontairement éteint entre opérations).

## Smoke Production

Invocation :

```
env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

RUN_ID `1784823157`. `bailleur-test@test.local` (`43443d1e-…` KC / `c7296c69-…` DB) déjà activé au
contrôle d'entrée (cf. anomalie ci-dessus), redésactivé après le run.

**Résultat : 63 PASS / 0 FAIL au premier passage** — identique à `1.12.0`. Couverture : sanity
Flyway **27/27**, pool `loyertracker_api` NOSUPERUSER/NOBYPASSRLS, JWT réels via Nginx, parcours
bailleur (inscription, patrimoine, bien, création de `Locataire`, bail créé avec `locataireId`),
invitation → acceptation (Admin API réelle) → JWT gestionnaire, affectation/échéances/pointage/
honoraires, alertes (PREAVIS) et audit, scoping gestionnaire, isolation cross-tenant live (2e
bailleur), RGPD (export, effacement locataire — 403 gestionnaire/204 bailleur, anonymisation
confirmée), garde-fous AuthN/ports, surface publique de vérification des quittances sans oracle.
Échafaudage `directAccessGrants` révoqué automatiquement par le script (vérifié `false` après).

Ce smoke ne couvre **aucun scénario notifications** : les cinq tables `notification_*` (V27)
restent à 0 ligne avant et après le run — conforme au périmètre du Sprint N (fondation Outbox,
aucune surface API/consommateur encore livrée, aucune dépendance Twilio active). Le smoke confirme
uniquement l'absence de régression sur le socle existant après application de V27.

## Nettoyage transactionnel

RUN_ID `1784823157`. Toutes les entités créées par le run ont été identifiées par leur identifiant
réel avant toute suppression, puis supprimées en une seule transaction :

| Entité | Quantité | Détail |
|---|---:|---|
| Audit log | 4 | pointage, validation honoraire, création locataire, effacement (`bailleur_id=c7296c69-…`) |
| Honoraire | 10 | `affectation_id=d045ecb9-…` |
| Paiement | 10 | `bail_id=ea8a95ac-…` |
| Alerte | 6 | `bail_id=ea8a95ac-…` |
| Invitation | 1 | `gest-smoke-1784823157@test.local` |
| Affectation | 1 | patrimoine smoke ↔ gestionnaire smoke |
| Bail | 1 | créé par le run |
| Bien | 1 | créé par le run |
| Locataire | 1 | « Locataire Smoke » (créé puis anonymisé par l'effacement RGPD, supprimé — données 100 % synthétiques) |
| Patrimoine | 2 | 1 sous `bailleur-test`, 1 auto-créé à l'inscription du 2e bailleur |
| Gestionnaire | 1 | `gest-smoke-1784823157@test.local` |
| Bailleur (2e bailleur smoke) | 1 | `bailleur2-smoke-1784823157@test.local` (`12ea43ee-…`) |

Vérifié avant suppression : 0 quittance référencée sur les paiements du run (aucune émission de
quittance déclenchée par ce smoke).

Comptes Keycloak du run supprimés : `gest-smoke-1784823157@test.local`
(`25b0cd54-a7ae-4e15-889a-9574996a1dab`), `bailleur2-smoke-1784823157@test.local`
(`4b8ee7d1-3bc3-480c-a365-a3f53850470b`). `bailleur-test` redésactivé (`enabled=false`),
`directAccessGrantsEnabled=false` confirmé sur `loyertracker-spa`.

## État final

| Contrôle | Résultat |
|---|---|
| Résidus du RUN_ID en base | 0 bailleur, 0 gestionnaire, 0 patrimoine, 0 locataire surnuméraires |
| Baseline métier post-nettoyage | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 8 locataires, 7 quittances — identique à l'état pré-test |
| Services | 8/8 actifs, 4/4 healthy, `RestartCount=0` |
| Flyway | 27/27, inchangé |
| Erreurs API | 0 ligne 5xx |
| Production publique | `https://loyertracker.loyerpro.org` → 200 |
| Prometheus | 5/5 `up` |
| Alertmanager | **1 alerte active** : `BackupHeartbeatMissing` (voir note ci-dessous) |
| `bailleur-test` | `enabled=false`, `directAccessGrantsEnabled=false` |

### Note non bloquante — `BackupHeartbeatMissing`

Identique au constat du Préflight `1.13.0` (`preflight-backup-v1.13.0-report.md`) : le cron de
backup (`15 2 * * *`) ne s'exécute que si l'hôte est allumé à 02h15 UTC ; l'hôte étant
volontairement éteint entre les opérations (produit non annoncé), aucun heartbeat n'a été poussé
depuis le dernier backup manuel du 2026-07-22 ~19:00 UTC. L'alerte est réapparue après le
redémarrage complet du 2026-07-23 ~15:45 UTC, une fois la fenêtre d'absence de métrique dépassée.
**Sans rapport avec V27/`1.13.0`** — pattern d'exploitation déjà documenté, non traité ici (aucun
backup n'a été demandé dans le périmètre de cette validation). Recommandation : arbitrage PO sur la
fréquence du cron vs. le pattern hôte-éteint, ou backup manuel ponctuel si un point de restauration
plus récent est souhaité.

**Validation finale PASS — `PRODUCTION_DEPLOYED` atteint le 2026-07-23 ~16:13 UTC (release
`1.13.0`, `sha-e4744d92`, EP-16 Sprint N).** Migration V27 additive confirmée stable en conditions
réelles, aucune régression sur le socle existant. `NotificationProvider` en service reste
exclusivement `NoopNotificationProvider` ; aucun canal externe (WhatsApp/SMS) activé. Hypercare
(T0/T+12/T+24) et clôture de release restent des étapes distinctes.
