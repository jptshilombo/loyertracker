# Plan Hypercare — Release `1.13.0` (EP-16 Sprint N — Fondation notifications)

| Champ | Valeur |
|---|---|
| `PRODUCTION_DEPLOYED` | 2026-07-23 ~16:13 UTC (`validation-finale-v1.13.0-report.md`, `date -u` vérifié) |
| T0 | 2026-07-23 ~16:37 UTC — **PASS** (après correction d'un résidu de nettoyage, voir ci-dessous) |
| T+12 | cible 2026-07-24 ~04:13 UTC ± 30 min — **en attente** |
| T+24 | cible 2026-07-24 ~16:13 UTC ± 30 min — **en attente** |
| Tag surveillé | `sha-e4744d92` |
| Rollback | `sha-359f4d63` (`1.12.0`) — viable même après application de V27 (additive) |

## Correction — résidu de nettoyage de la validation finale détecté au T0

Le contrôle T0 a trouvé **8 lignes `notification_event`** en base, toutes horodatées
`2026-07-23 16:12:46`–`16:12:48` (fenêtre exacte du smoke de la validation finale, RUN_ID
`1784823157`), référençant `bailleur_id=c7296c69-…` (`bailleur-test`) et `aggregate_id=ea8a95ac-…`
(le bail créé puis supprimé par le nettoyage transactionnel de la validation finale). Cause : la
table `notification_event` — nouvelle avec V27, alimentée en écriture inline par les mêmes
opérations métier que celles exercées par le smoke (`BAIL_CREE`, `PAIEMENT_RECU`,
`LOYER_EN_RETARD` ×5, `PREAVIS`) — n'avait pas été incluse dans le contrôle post-nettoyage de
`validation-finale-v1.13.0-report.md` (seules `notification_preference`/`notification_outbox`
avaient été revérifiées à 0 après coup). **Aucun impact fonctionnel** : ces lignes ne bloquaient
rien (aucune ligne `notification_outbox`/`notification_delivery` dépendante, confirmé avant
suppression) et n'étaient pas exploitables (`NotificationProvider` = `NoopNotificationProvider`,
aucun canal externe actif). Corrigées ici par suppression ciblée des 8 identifiants exacts, dans
une transaction dédiée, **avant** la suite du checkpoint T0. `notification_event` confirmée à 0
ligne après correction. `docs/cgpa/09-production/validation-finale-v1.13.0-report.md` n'est pas
réécrit (interdiction CLAUDE.md de réécrire l'historique) — ce document fait foi pour la
correction. **Leçon** : pour toute nouvelle table alimentée en écriture inline par un smoke déjà
existant (pattern `audit_log`), l'ajouter explicitement à la liste de vérification post-nettoyage
de la validation finale, pas seulement aux tables déjà connues avant le sprint qui l'introduit.

## Critères de suspension

- restart inattendu, service non healthy ou dérive de tag/digest ;
- erreur 5xx ou régression sur le socle existant (aucune nouvelle surface API livrée par ce
  sprint) ;
- activité inattendue sur les tables `notification_*` (toute ligne `notification_outbox`/
  `notification_delivery` serait anormale : aucun consommateur Outbox n'est encore livré) ;
- activation inattendue d'un flag externe (`NOTIFICATIONS_EXTERNAL_ENABLED`/
  `TWILIO_WHATSAPP_ENABLED`/`TWILIO_SMS_ENABLED`) ;
- hausse anormale des 5xx, pool Hikari en attente ou alerte non qualifiée ;
- `bailleur-test` ou `directAccessGrants` retrouvés actifs de façon inattendue.

## Checkpoint T0 — 2026-07-23 ~16:37 UTC (`date -u` vérifié)

**Statut : PASS** (après correction du résidu ci-dessus)

| Contrôle | Résultat |
|---|---|
| Smoke | 63/0 au premier passage (validation finale) |
| Stack | 8/8 actifs, 4/4 healthy, `RestartCount=0` (`StartedAt=2026-07-23T15:45:49Z` sur les 4 services applicatifs — redémarrage complet antérieur au déploiement technique) |
| Tag / digests | `sha-e4744d92` ; API `9e9a331d…`, Web `c797934a…` — conformes |
| Flyway | 27/27 |
| Tables `notification_*` | toutes à 0 ligne après correction du résidu (préférence/événement/Outbox/Delivery/template) |
| Keycloak | `bailleur-test` désactivé ; `directAccessGrantsEnabled=false` sur `loyertracker-spa` |
| Santé | `/healthz` 200, site public 200 |
| Observabilité | Prometheus 5/5 ; **Alertmanager 1 alerte** `BackupHeartbeatMissing` (non bloquante, cron 02h15 manqué par le pattern hôte-éteint, cf. `validation-finale-v1.13.0-report.md`) |
| Pool Hikari | `hikaricp_connections_pending` = 0 |
| Logs Nginx (15 min) | 0 ligne 5xx |
| Logs API (15 min) | 0 entrée `ERROR` |
| Capacité | disque 30 Gio libres (21 %) ; mémoire ~2,0 Gio disponible ; charge 0,23/0,10/0,07 |

**Décision T0 : PASS.** La clôture reste interdite avant T+12, T+24 et la décision CDO finale.
Prochains checkpoints cibles : T+12 le 2026-07-24 ~04:13 UTC ± 30 min, T+24 le 2026-07-24
~16:13 UTC ± 30 min. Conformément au pattern d'exploitation déjà documenté (produit non annoncé
publiquement, hôte volontairement éteint entre les opérations), ces deux checkpoints seront très
probablement exécutés en rattrapage lors d'une prochaine instruction PO explicite, à l'identique
de `1.9.0`/`1.12.0`.
