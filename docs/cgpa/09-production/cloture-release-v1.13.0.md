# Clôture Release `1.13.0`

| Champ | Valeur |
|---|---|
| Date de clôture | 2026-07-24 |
| Heure CDO GO | ~09:26 UTC (après checkpoint T+24 par dérogation PO explicite tracée, fenêtre raccourcie ~T+17 — cf. §5) |
| Release | `1.13.0` — EP-16 Sprint N (Fondation notifications, US-119/120/121) |
| Tag Production | `sha-e4744d92` |
| `PRODUCTION_DEPLOYED` | 2026-07-23 ~16:13 UTC (`validation-finale-v1.13.0-report.md`) |
| Statut | **RELEASE CLÔTURÉE** |

## 1. Récapitulatif du cycle `1.13.0`

| Étape | Date | Résultat | Référence |
|---|---|---|---|
| Sprint N (Fondation) codé, `mvn verify` 198/198 verts | 2026-07-19 | Migration V27 additive | `docs/project-state.md` §11 |
| PR #235 fusionnée sur `main` | 2026-07-19T14:22:50Z (`3ed6d3f`) | CI complète verte | `docs/project-state.md` §11 |
| Gate Staging EP-16 Sprint N | 2026-07-19 | GO — `STAGING_DEPLOYED` (`sha-e4744d92`) | `gate-staging-sprint-n-ep16-decision.md` |
| PR #237 fusionnée puis Gate Production EP-16 Sprint N | 2026-07-19T16:42:40Z (`cc2fd06`) | GO sous réserve — `PRODUCTION_READY` (réserve bloquante : clôture `1.12.0`) | `gate-production-sprint-n-ep16-decision.md` |
| Clôture Release `1.12.0` | 2026-07-22 | CDO GO — lève la réserve bloquante ci-dessus | `cloture-release-v1.12.0.md` |
| Préflight Production `1.13.0` | 2026-07-22 | PASS — backup vérifié, V27 additive confirmée | `preflight-backup-v1.13.0-report.md` |
| Déploiement technique | 2026-07-22 | PASS technique — V27 appliquée 27/27, `api`+`nginx` seuls recréés | `deploiement-technique-v1.13.0-report.md` |
| Validation finale (smoke Production) | 2026-07-23 ~16:13 UTC | **63 PASS / 0 FAIL au premier passage** ; `PRODUCTION_DEPLOYED` | `validation-finale-v1.13.0-report.md` |
| Hypercare T0 | 2026-07-23 ~16:37 UTC | PASS (résidu `notification_event` du smoke détecté et corrigé) | `plan-etape-hypercare-v1.13.0.md` |
| Hypercare T+12 | 2026-07-24 ~09:19 UTC | PASS (rattrapage, cible ~04:13 UTC dépassée) | idem |
| Hypercare T+24 | 2026-07-24 ~09:26 UTC | **PASS (dérogation PO explicite tracée, fenêtre raccourcie ~T+17)** | idem |

## 2. Périmètre livré

**EP-16 — Notifications multicanales via Twilio, Sprint N (Fondation), US-119/120/121**

- Migration **V27 additive** : `notification_preference`, `notification_event`, `notification_outbox`, `notification_delivery`, `notification_template` (RLS `bailleur_isolation` sur les quatre premières, référentiel global sans RLS sur la dernière, patron `type_bien`).
- Extension de `generer_alertes()` (voie A, fan-out Outbox limité au bailleur) ; écriture inline voie B dans `QuittanceCertifieeService.emettre` (`QUITTANCE_DISPONIBLE`), `GarantieService.retenirSurLoyer` (`GARANTIE_DEBITEE`), `PaiementService.pointer` (`PAIEMENT_RECU`), `BailService.creer`/`cloturer` (`BAIL_CREE`/`BAIL_CLOS`).
- `NotificationOutboxService` (fan-out par préférence éligible, réclamation par lot `FOR UPDATE SKIP LOCKED`) ; `NotificationPreferenceService` (US-119, aucune interface HTTP en Sprint N) ; `NotificationProvider`/`NoopNotificationProvider` (US-121, seul fournisseur disponible).
- Feature flags `app.notifications.*` (`NOTIFICATIONS_EXTERNAL_ENABLED`/`TWILIO_WHATSAPP_ENABLED`/`TWILIO_SMS_ENABLED`/`NOTIFICATION_DRY_RUN`), tous à valeurs sûres par défaut, non consommés par du code applicatif à ce stade.
- Rollback applicatif seul **viable même après application** de V27 (additive) — contrairement à V26/`1.12.0`, aucun second backup post-migration requis.
- **Aucun envoi réel, aucune dépendance Twilio, aucun compte/credentials/template Twilio, aucune modification Docker/infrastructure.**

## 3. Réserves

| ID | Nature | Statut à la clôture |
|----|--------|---------------------|
| Réserve bloquante du Gate Production Sprint N (clôture `1.12.0` requise avant Préflight) | Gouvernance | ✅ **Levée le 2026-07-22** (`cloture-release-v1.12.0.md`) |
| Écart de fenêtre hypercare T+24 (contrôlé ~T+17 au lieu de T+24 nominal) | Mineur exploitation | ✅ **Assumé et tracé** — dérogation PO explicite après présentation chiffrée de l'écart (double confirmation), pas un écart subi ; aucun critère de suspension observé sur les trois checkpoints |
| Alerte `BackupHeartbeatMissing` (héritée, réapparue à la validation finale) | Mineur exploitation | ✅ **Résolue** — disparue dès le checkpoint T+12 (heartbeat backup redevenu conforme) |
| `RSV-STG-01` (héritée) | Confirmation live `STG-ISOL-01` au prochain déploiement Staging mutualisé | ⚠️ **MAINTENUE** — sans rapport avec `1.13.0` |
| Activation externe (WhatsApp/SMS) | Interdite jusqu'au GO du Sprint N+2 (K8, ADR-18) | Non applicable à `1.13.0` — `NotificationProvider` en service reste exclusivement `NoopNotificationProvider` |

## 4. État de Production au moment de la clôture

| Contrôle | Valeur |
|---|---|
| Tag Production | `sha-e4744d92` |
| Digest API | `sha256:9e9a331d3a7ee8a65e17235ead3f60c4b916d46086d9d3dd2d0c263ddabfe815` |
| Digest Web | `sha256:c797934a8d5e629a6e532c50790dad78495a5e1aa5e7d42273e7fc6ccd00d41b` |
| Flyway | V1→**V27** (27/27) |
| Services | 8/8 Up, 4/4 `(healthy)`, `RestartCount=0` depuis le redémarrage hôte du 2026-07-24 ~09:02 UTC |
| Tables `notification_*` | `preference`/`event`/`outbox`/`delivery`/`template` toutes à **0 ligne** |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | 0 alerte active |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| 5xx / `ERROR` (15 min) | 0 / 0 |
| Données métier (baseline) | 3 bailleurs, 2 patrimoines, 8 biens, 8 baux, 8 garanties, 1 gestionnaire, 8 locataires, 7 quittances |
| `bailleur-test` | `enabled=false` ; `directAccessGrantsEnabled=false` |
| Site public | `https://loyertracker.loyerpro.org` → 200 |

## 5. Décision CDO

**Chief Delivery Officer : GO — Release `1.13.0` CLÔTURÉE le 2026-07-24 (~09:26 UTC).**

- Hypercare complète : T0 PASS, T+12 PASS (rattrapage), **T+24 PASS par dérogation PO explicite tracée** — le contrôle a eu lieu à ~T+17 (17h08 après `PRODUCTION_DEPLOYED`) plutôt qu'à T+24 nominal (~16:13 UTC), un écart chiffré ayant été présenté au PO puis confirmé explicitement à deux reprises avant exécution (« fait le T+24 maintenant et on avance » puis « exécuter maintenant, l'accepter comme T+24 par dérogation PO explicite »). Aucun critère de suspension observé sur l'un des trois checkpoints.
- Fondation notifications (US-119/120/121, migration V27) confirmée stable : tag/digests inchangés depuis le déploiement, Flyway 27/27, les cinq tables `notification_*` toutes à 0 ligne (cohérent avec `NoopNotificationProvider` exclusif, aucune activité applicative externe), 0 5xx, 0 `ERROR`.
- La réserve bloquante posée par le Gate Production Sprint N (clôture `1.12.0`) était déjà levée le 2026-07-22. `RSV-STG-01` (héritée) maintenue, sans rapport avec `1.13.0`.
- Cette clôture satisfait le prérequis **« Sprint N clos en GO »** posé par `plan-execution-ep16-notifications.md` comme condition de démarrage du Sprint N+1 (WhatsApp P0, Twilio Sandbox) — au même titre que les clôtures précédentes pour les séquences Sprints A/B/C d'EP-15.
- Prochaine action autorisée : instruction explicite et distincte du PO pour le **GO du Sprint N+1** (le Plan d'Exécution EP-16 est déjà rédigé et approuvé dans sa portée détaillée pour ce sprint, mais son démarrage reste subordonné à ce GO propre, distinct de celui déjà donné pour le Sprint N). Aucun code, migration, dépendance Twilio ou déploiement n'est autorisé par cette clôture elle-même.
