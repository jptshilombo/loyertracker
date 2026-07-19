# Addendum Backlog — Epic EP-16 (Notifications multicanales via Twilio)

| Champ | Valeur |
|-------|--------|
| Document de référence | `product-backlog.md`, `addendum-backlog-ep13-fin-de-bail.md`, `addendum-backlog-ep15-personnes.md` — **non modifiés** |
| Statut | **Proposé** — cadrage documentaire ; K1→K8 (ADR-18) **entièrement ouverts**, aucun GO possible avant tranchage PO |
| Date | 2026-07-19 |
| Décision liée | `ADR-18-notifications-multicanales-twilio.md` |
| Plan d'exécution | `plan-execution-ep16-notifications.md` (proposé, non approuvé) |

> **Numérotation — collision détectée et corrigée.** US-01→118 sont déjà occupées. Le prompt de
> mission suggérait de repartir à US-115 (en se fondant sur US-113/114, plus haut numéro visible
> dans EP-15) mais une vérification exhaustive du dépôt montre que **US-115 à US-118 sont déjà
> occupées par EP-13 (Fin de bail)**, un epic distinct mené en parallèle d'EP-15
> (`addendum-backlog-ep13-fin-de-bail.md`, déjà en Production sous la release `1.11.0`). Ce
> document introduit donc **US-119 à US-126** sous l'epic **EP-16**, sans collision réelle
> vérifiée à ce jour (2026-07-19).

---

## EP-16 — Notifications multicanales via Twilio

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-16 | **Notifications multicanales via Twilio** — extension des alertes in-app existantes par un canal externe (WhatsApp, secours SMS), sans n8n, sans dépendance directe du domaine métier envers Twilio | Non planifié — cadrage seul à ce stade | Must |

### US-119 — Préférences, coordonnées et consentement

**En tant que** destinataire (bailleur, gestionnaire, ou locataire selon K1), **je veux** définir
mon numéro, mon canal préféré et mon consentement **afin de** recevoir des notifications externes
uniquement si je l'ai explicitement autorisé.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un destinataire de mon périmètre **W** je définis `phone_e164`/`preferred_channel`/`fallback_channel`/`whatsapp_opt_in`/`sms_opt_in` **T** ces valeurs sont persistées avec `consent_at`/`consent_source` renseignés. **G** un destinataire désinscrit (`enabled=false`) **W** un événement de notification le concerne **T** aucun envoi externe n'est tenté pour lui, immédiatement. |
| Dépendances | Aucune |
| Priorité | Must |
| Points | 8 |
| Risques | RSV-EP16-04 (consentement absent ou mal recueilli) |
| Source | ADR-18 §Consentement ; EF-121 ; BF-107 |
| Sprint cible | Sprint N — Fondation |

### US-120 — Modèle Notification et Outbox transactionnelle

**En tant que** système, **je veux** persister un événement de notification et son Outbox dans la
même transaction que l'opération métier **afin de** garantir qu'aucune notification n'est perdue
ni dupliquée, sans jamais coupler l'opération métier à un appel réseau externe.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une transaction métier émettant un événement notifiable (quittance émise, garantie débitée, ou batch d'alertes) **W** la transaction commite **T** `notification_event` (+ `notification_outbox` par destinataire/canal éligible) est persisté dans la même transaction, protégé par RLS. **G** un `event_id`/`recipient_id`/`notification_type`/`channel` déjà traité **W** une réexécution survient (retry, redémarrage, replay batch) **T** aucune seconde ligne n'est créée (contrainte unique). |
| Dépendances | US-119 (préférences nécessaires pour le fan-out par destinataire éligible) |
| Priorité | Must |
| Points | 13 |
| Risques | RSV-EP16-01 (double alimentation de l'Outbox mal isolée), RSV-EP16-02 (traitement concurrent) |
| Source | ADR-18 §2/§3/§4 ; EF-113/114/115/116 ; BF-106/108 |
| Sprint cible | Sprint N — Fondation |

### US-121 — Abstraction fournisseur et adaptateur Twilio

**En tant que** développeur, **je veux** une interface `NotificationProvider` et une implémentation
`TwilioNotificationProvider` **afin de** ne jamais exposer le SDK Twilio, les credentials ni les
Content SID au domaine métier, et de pouvoir remplacer Twilio sans réécrire les règles métier.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** le code du domaine métier (`paiements`, `garanties`, `alertes`) **W** revue de dépendances **T** aucune référence au SDK Twilio, à un Account SID ou à un numéro expéditeur. **G** l'application démarre sans configuration Twilio **W** au boot **T** aucune erreur bloquante, `NOTIFICATIONS_EXTERNAL_ENABLED=false` par défaut. |
| Dépendances | US-120 |
| Priorité | Must |
| Points | 8 |
| Risques | Aucun nouveau |
| Source | ADR-18 §5 ; EF-117/123 |
| Sprint cible | Sprint N — Fondation |

### US-122 — WhatsApp transactionnel P0

**En tant que** locataire ou bailleur, **je veux** recevoir un message WhatsApp pour les
événements P0 (quittance disponible, loyer en retard, garantie débitée) **afin de** être informé
sans avoir à me connecter au dashboard.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un événement P0 avec un destinataire consentant WhatsApp **W** le `NotificationDispatcher` le traite **T** un message WhatsApp est envoyé via un template `APPROUVE`, jamais avec un texte codé en dur ; le lien de quittance réutilise le mécanisme HMAC existant (EP-14), jamais de PDF en base64. **G** un template non approuvé **W** tentative d'envoi **T** la ligne passe en `DEAD`, aucun envoi. |
| Dépendances | US-119, US-120, US-121 |
| Priorité | Must |
| Points | 13 |
| Risques | RSV-EP16-04, RSV-EP16-06 (référentiel de templates global potentiellement insuffisant) |
| Source | ADR-18 §6/§7/§Templates ; EF-118 ; BF-106 |
| Sprint cible | Sprint N+1 — WhatsApp P0 |

### US-123 — Callbacks, suivi de livraison et retries

**En tant que** système, **je veux** traiter les callbacks de statut Twilio de façon sécurisée et
idempotente **afin de** maintenir un historique de livraison fiable sans jamais dupliquer un envoi.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un callback avec signature `X-Twilio-Signature` valide **W** reçu sur l'endpoint public dédié **T** transition d'état idempotente de `NotificationDelivery`. **G** une signature invalide **W** reçue **T** rejet immédiat, aucune information révélée. **G** une erreur temporaire **W** évaluée **T** `RETRY` avec backoff ; erreur permanente **T** `DEAD` direct, jamais de retry. |
| Dépendances | US-120, US-121 |
| Priorité | Must |
| Points | 8 |
| Risques | RSV-EP16-02 |
| Source | ADR-18 §Sécurité/§Statuts ; EF-120 ; ENF-94/97 ; BF-109 |
| Sprint cible | Sprint N+1 — WhatsApp P0 |

### US-124 — SMS fallback contrôlé

**En tant que** bailleur, **je veux** qu'un échec WhatsApp permanent puisse déclencher un SMS de
secours uniquement si je (ou le destinataire) l'ai explicitement autorisé **afin de** ne pas
générer de coût ni de sollicitation non désirée par défaut.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un échec WhatsApp `PERMANENT` sur un destinataire `sms_opt_in=true` et la politique de fallback activée **W** évalué par le dispatcher **T** un unique SMS est tenté. **G** `sms_opt_in=false` ou politique désactivée **W** même échec **T** aucun SMS n'est envoyé. |
| Dépendances | US-122, US-123 |
| Priorité | Should |
| Points | 5 |
| Risques | RSV-EP16-03 (dérive budgétaire) |
| Source | ADR-18 §6 ; EF-119/124 ; BF-110 — proposition à confirmer (K5) |
| Sprint cible | Sprint N+2 — SMS fallback et UX |

### US-125 — Interface de préférences et historique

**En tant que** bailleur (et, selon K1, gestionnaire ou locataire), **je veux** consulter et
modifier mes préférences de notification et l'historique des envois **afin de** garder le
contrôle sur les communications reçues.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un utilisateur authentifié **W** il consulte `/api/notifications/preferences/...`/`/api/notifications` **T** seules les données de son propre périmètre RLS sont visibles ; toute modification est immédiatement effective (désinscription notamment). |
| Dépendances | US-119, US-123 |
| Priorité | Should |
| Points | 8 |
| Risques | Aucun nouveau |
| Source | ADR-18 §Sécurité ; EF-121/122 ; BF-107/111 — proposition à confirmer (K6) |
| Sprint cible | Sprint N+2 — SMS fallback et UX |

### US-126 — Observabilité, sécurité et exploitation

**En tant que** DevSecOps Lead, **je veux** des métriques, alertes, un runbook et des garde-fous
budgétaires **afin de** exploiter Twilio comme un service externe critique de façon sûre et
supervisée.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** la chaîne de notification en fonctionnement **W** scrape Prometheus **T** métriques `notification.*` disponibles, sans PII en label. **G** un plafond mensuel approché **W** évalué **T** envois limités/arrêtés + alerte. **G** un incident Twilio **W** survenu **T** runbook applicable, kill switch opérationnel. |
| Dépendances | US-120, US-121, US-122 |
| Priorité | Must |
| Points | 8 |
| Risques | RSV-EP16-03, RSV-EP16-05 |
| Source | ADR-18 §Coûts/§Rollback ; EF-123/124 ; ENF-96 |
| Sprint cible | Sprint N+2 — SMS fallback et UX (socle observabilité dès Sprint N) |

---

## Récapitulatif & priorisation

| Story | Points | Priorité | Sprint cible |
|-------|--------|----------|---------------|
| US-119 — Préférences, coordonnées et consentement | 8 | Must | N |
| US-120 — Modèle Notification et Outbox transactionnelle | 13 | Must | N |
| US-121 — Abstraction fournisseur et adaptateur Twilio | 8 | Must | N |
| US-122 — WhatsApp transactionnel P0 | 13 | Must | N+1 |
| US-123 — Callbacks, suivi de livraison et retries | 8 | Must | N+1 |
| US-124 — SMS fallback contrôlé | 5 | Should | N+2 |
| US-125 — Interface de préférences et historique | 8 | Should | N+2 |
| US-126 — Observabilité, sécurité et exploitation | 8 | Must | N/N+2 |
| **Total EP-16** | **71** | — | — |

## Dépendances & risques (synthèse)

- **K1→K8 (ADR-18)** : **aucun tranché** à ce stade — huit points ouverts, aucun GO possible avant
  arbitrage PO (voir rapport de mission pour la présentation complète).
- **RSV-EP16-01/02** (isolation des deux voies d'alimentation de l'Outbox, traitement concurrent) :
  points de conception à verrouiller dès US-120, avant tout codage des US suivantes.
- **RSV-EP16-03** (dérive budgétaire) : dépend de US-124/US-126 — aucune activation Production tant
  que le plafond n'est pas opérationnel.
- **RSV-EP16-04** (consentement) : dépend directement de K3 — bloquant pour US-122/124.
- **RSV-EP16-05** (observabilité) : dépend de US-126 et de l'extension additive de
  `observability-governance.md`.
- **RSV-EP16-06** (référentiel de templates global) : accepté comme limitation du P0, en
  surveillance.
- Aucun Sprint ne peut démarrer avant un GO explicite du PO sur `plan-execution-ep16-notifications.md`,
  lui-même conditionné au tranchage préalable de K1→K8.
