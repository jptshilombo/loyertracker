# Rapport Préflight + Backup — Release `1.13.0` (EP-16 Sprint N — Fondation notifications)

| Champ | Valeur |
|---|---|
| Date | 2026-07-22, ~17:59–19:00 UTC |
| Hôte | `loyertracker-prod-server` — `18.158.70.88` |
| Candidat | `sha-e4744d92` |
| Digests Staging | API `sha256:9e9a331d3a7ee8a65e17235ead3f60c4b916d46086d9d3dd2d0c263ddabfe815` ; Web `sha256:c797934a8d5e629a6e532c50790dad78495a5e1aa5e7d42273e7fc6ccd00d41b` |
| Production / rollback | `1.12.0` — `sha-359f4d63` |
| Verdict | **PASS** |

## Levée de la réserve bloquante (RSV-PROD-EP16-N-01)

Le Gate Production EP-16 Sprint N (`gate-production-sprint-n-ep16-decision.md`) interdisait tout
Préflight avant clôture formelle de `1.12.0`. Cette clôture a été prononcée le 2026-07-22 (checkpoint
hypercare T+12/T+24 en rattrapage, PASS sous surveillance ; **Décision CDO : GO — Release `1.12.0`
CLÔTURÉE** — `plan-etape-hypercare-v1.12.0.md`, `cloture-release-v1.12.0.md`). **RSV-PROD-EP16-N-01
levée**, ce Préflight est donc autorisé.

## Contrôles lecture seule (Production `1.12.0` avant migration)

- 8/8 conteneurs actifs, 4/4 healthy, **`RestartCount=0`** sur `api`/`nginx`/`postgres`/`keycloak` ;
  tag courant `sha-359f4d63` inchangé depuis la clôture de `1.12.0` (~2h plus tôt).
- Flyway **26/26** ; V27 (cinq nouvelles tables `notification_*`) **pas encore appliquée** — état
  propre avant migration.
- `bailleur-test@test.local` confirmé **désactivé** (`enabled: false`) ;
  `directAccessGrantsEnabled=false` sur `loyertracker-spa`.
- Prometheus **5/5** cibles `up`. Alertmanager : **1 alerte active** `BackupHeartbeatMissing` au
  moment du contrôle — pattern récurrent déjà qualifié (host redémarré ~1h avant ce Préflight,
  cron quotidien 02h15 pas encore repassé) ; **résolue par ce Préflight lui-même** : le backup
  ci-dessous pousse un heartbeat réussi vers le Pushgateway.
- **0 ligne 5xx** (30 dernières minutes) ; **0 entrée `ERROR`** API (30 dernières minutes) ; site
  public `https://loyertracker.loyerpro.org` → **200** ; `/healthz` → **200**.
- 30 Gio disque libres (21 %), ~2,0 Gio mémoire disponible, charge 0,20/0,07/0,02.
- Données métier baseline : 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties,
  1 gestionnaire, **8 locataires**, 7 quittances — cohérent avec la clôture `1.12.0` (§0M,
  `prod-state.md`), une quittance supplémentaire émise depuis (activité privée pré-annonce, non
  anormale).
- Dépôt hôte au commit `c14cdc2`, en retard sur `origin/main` (`77f30ca`) — à synchroniser au
  déploiement technique (`git pull --ff-only`) ; aucun `pull` exécuté durant ce Préflight.
- Aucune commande de mutation applicative exécutée (lecture seule + `pg_dump` + `cp .env`
  uniquement ; aucune commande Docker à portée globale).

## Backup vérifié (avant migration V27)

| Fichier | Taille | Mode | SHA-256 |
|---|---:|---:|---|
| `loyertracker-20260722-190032.dump` | 827770 | 600 | `808fbb162c2f918cfbdce979f689256eb549c259ed1f55b48c1bf7056f1963ca` |
| `loyertracker-20260722-190032.globals.sql` | 1108 | 600 | `f4f596d9483e86b692e8645baa4491da506a3e41407217471f030c6f287f5471` |

`pg_restore --list` : **799 entrées** (vérifié via `docker cp` dans le conteneur `postgres`, même
méthode que les Préflights précédents). Heartbeat de sauvegarde poussé avec succès vers le
Pushgateway (`infra/backup/backup-postgres.sh` — sortie `OK heartbeat de sauvegarde poussé`),
levant l'alerte `BackupHeartbeatMissing` constatée ci-dessus.

## Volet migration V27 — additive (aucune condition renforcée)

Contrairement à `1.12.0`/V26, la migration V27 est **additive** (cinq nouvelles tables
`notification_preference`/`notification_event`/`notification_outbox`/`notification_delivery`/
`notification_template`) : l'application `1.12.0` actuelle ignore ces tables si un rollback
applicatif seul devait être exécuté après migration. Aucun second backup post-migration n'est donc
requis comme condition bloquante (à la différence de V26/RSV-EP15-03) ; le backup pré-migration
ci-dessus suffit comme point de restauration.

## Feature flags et absence de dépendance Twilio (condition 6)

`.env.example` documente quatre nouveaux indicateurs, tous à valeur sûre par défaut et **sans
secret ni credential** : `NOTIFICATIONS_EXTERNAL_ENABLED=false`, `TWILIO_WHATSAPP_ENABLED=false`,
`TWILIO_SMS_ENABLED=false`, `NOTIFICATION_DRY_RUN=true`. Vérifié dans `application.yml`
(`backend/src/main/resources/application.yml:114-119`) : chaque propriété a un **fallback Spring
identique** (`${VAR:false}` / `${NOTIFICATION_DRY_RUN:true}`), donc même en l'absence de ces
variables dans le `.env` hôte actuel, le comportement au démarrage reste sûr par construction.
Aucune configuration ni dépendance Twilio réelle n'est présente dans le candidat (seul fournisseur
livré : `NoopNotificationProvider`).

## Secrets et rollback

- **Aucune nouvelle variable d'environnement/secret requise** pour ce Préflight : les quatre
  indicateurs ci-dessus disposent de valeurs de repli sûres ; leur ajout explicite au `.env` hôte
  reste possible mais non bloquant (`git diff --stat c14cdc2 cc2fd06` sur
  `docker-compose.yml`/`docker-compose.prod.yml` ne montre aucun changement, seul `.env.example`
  évolue).
- `.env.bak-pre-1.13.0` créé, mode 600, `.env` hôte inchangé (tag `sha-359f4d63`, aucun service
  redémarré).
- Rollback `sha-359f4d63` : images API/Web déjà présentes localement sur l'hôte (aucun pull requis
  pour un retour arrière) ; V27 étant additive, ce rollback reste viable **même après** application
  de la migration (contrairement au profil V26/`1.12.0`).

## Verdict

`CHANGELOG.md` `[Non publié]` couvre déjà le Sprint N EP-16 ; promu en `[1.13.0] — 2026-07-22` dans
le même commit que ce rapport (condition 5 du Gate Production).

**Préflight Production `1.13.0` : PASS.** Toutes les conditions bloquantes du Gate Production
(`gate-production-sprint-n-ep16-decision.md`) sont satisfaites : (1) clôture `1.12.0` confirmée,
(2) Production `1.12.0` vérifiée saine en lecture seule, (3) backup base+globals produit et
vérifié, (4) candidat `sha-e4744d92` et rollback `sha-359f4d63` confirmés, (5) changelog promu,
(6) flags externes à valeurs sûres et absence de dépendance Twilio confirmées, (7) smoke canonique
≥63 et contrôle 0 Outbox/Delivery sans préférence restent à exécuter **au déploiement technique**
(prochaine étape), (8) déploiement/rollback devra cibler exclusivement `api`+`nginx`. **Aucun
déploiement exécuté par ce Préflight.** Une instruction explicite distincte reste requise pour
déployer `api` + `nginx` et appliquer la migration V27.
