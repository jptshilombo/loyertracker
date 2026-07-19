# Addendum CDC — Notifications multicanales via Twilio (EP-16)

| Champ | Valeur |
|-------|--------|
| Document de référence | `cahier-des-charges.md` (✅ Validé — Gate 3 Go, 2026-06-04) — **non modifié** |
| Document de référence | `dossier-architecture.md` (✅ Validé — Gate 4 Go, 2026-06-04) — étendu narrativement (additif) |
| Statut de l'addendum | **Proposé** — cadrage documentaire ; aucun codage ni migration SQL engagé. K1→K8 (ADR-18) entièrement ouverts |
| Date | 2026-07-19 |
| Décision liée | `ADR-18-notifications-multicanales-twilio.md` |
| Base besoin | `docs/cgpa/02-expression-besoin/addendum-notifications-multicanales.md` (BF-106→BF-111) |

---

## 1. Exigences fonctionnelles détaillées (addendum)

### 1.1 Production des événements de notification *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-113 | Événement `QUITTANCE_DISPONIBLE` (voie transactionnelle) | ED une quittance certifiée émise (`QuittanceCertifieeService.emettre`) · Q la transaction commite · A une ligne `notification_event` (+ fan-out `notification_outbox` par destinataire/canal éligible) est créée **dans la même transaction**, sans aucun appel réseau Twilio à ce stade. | Must | BF-106 |
| EF-114 | Événements `LOYER_EN_RETARD`/`GARANTIE_NON_RESTITUEE`/`FIN_BAIL`/`PREAVIS` (voie batch) | ED le batch `generer_alertes()` génère une alerte `NON_LUE` · Q l'insertion réussit (`ON CONFLICT ... DO NOTHING`, `uq_alerte_nonlue`) · A un `notification_event` correspondant est produit dans la **même** transaction SQL batch, avec le même anti-doublon (aucune alerte in-app sans notification associée, et réciproquement, sauf préférence de canal désactivée). | Must | BF-106 |
| EF-115 | Événement `GARANTIE_DEBITEE` (voie transactionnelle) | ED `GarantieService.retenirSurLoyer(...)` (ou mouvement équivalent de type `RETENUE_*`) · Q la transaction commite · A un `notification_event` est créé dans la même transaction, sur le modèle de l'écriture `audit_log` déjà en place. | Must | BF-106 |
| EF-116 | Outbox transactionnelle avec idempotence garantie en base | ED un `event_id` déjà traité pour un `recipient_id`/`notification_type`/`channel` donné · Q une seconde tentative d'insertion survient (retry, redémarrage, réexécution batch) · A la contrainte unique `(event_id, recipient_id, notification_type, channel)` empêche tout doublon, sans erreur bloquante côté appelant (`ON CONFLICT DO NOTHING` ou équivalent). | Must | BF-108, ADR-18 §4 |

### 1.2 Fournisseur et envoi *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-117 | Abstraction `NotificationProvider` | ED un service métier · Q il produit un événement de notification · A aucune classe du domaine métier (`paiements`, `garanties`, `alertes`, contrôleurs REST) ne référence le SDK Twilio, un Account SID, un numéro expéditeur ou un Content SID — uniquement `NotificationEvent`/`NotificationOutbox`. | Must | BF-108, ADR-18 §5 |
| EF-118 | Envoi WhatsApp via template approuvé uniquement | ED un `NotificationOutbox` en statut `PENDING`, canal `WHATSAPP` · Q le `NotificationDispatcher` le traite · A l'envoi n'est tenté que si `NotificationTemplate.approval_status = 'APPROUVE'` pour ce `code`/`channel`/`language` ; sinon la ligne passe en `DEAD` avec `last_error_code` explicite, jamais d'envoi avec un texte codé en dur. | Must | BF-106, ADR-18 §Templates |
| EF-119 | Fallback SMS contrôlé | ED un envoi WhatsApp en échec **permanent** (catégorie `PERMANENT`) sur un destinataire ayant `sms_opt_in = true` et la politique de fallback activée (feature flag) · Q le dispatcher évalue le fallback · A un unique envoi SMS est tenté (jamais automatique par défaut, jamais si `sms_opt_in = false`, jamais en doublon avec un WhatsApp déjà `DELIVERED`/`READ`). | Should | BF-110 — **proposition, à confirmer (K5)** |
| EF-120 | Callback de statut Twilio | ED un callback HTTP reçu sur l'endpoint public dédié · Q la signature `X-Twilio-Signature` est vérifiée · A si valide, transition d'état idempotente de `NotificationDelivery` (par `provider_message_id`) ; si invalide, rejet sans traitement ni fuite d'information ; un callback dupliqué ne produit aucune transition supplémentaire. | Must | BF-109, ADR-18 §Sécurité |

### 1.3 Consentement, préférences et historique *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-121 | Gestion des préférences et du consentement | ED un destinataire (bailleur, gestionnaire, ou locataire selon K1) · Q il définit/modifie ses préférences · A `phone_e164`, `preferred_channel`, `fallback_channel`, `whatsapp_opt_in`, `sms_opt_in` sont persistés avec `consent_at`/`consent_source` ; la désinscription (`enabled=false`) empêche tout envoi externe futur immédiatement (pas de fenêtre de tolérance). | Must | BF-107 — **proposition, à confirmer (K3)** |
| EF-122 | Historique de notification consultable | ED un utilisateur autorisé · Q il consulte l'historique de notifications d'un destinataire de son périmètre · A seules les notifications de son propre tenant (RLS `bailleur_isolation`) sont visibles ; un gestionnaire ne voit jamais l'historique d'un autre bailleur ni d'un autre gestionnaire. | Could | BF-111 — **proposition, à confirmer (K6)** |

### 1.4 Exploitation et garde-fous *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-123 | Feature flags et kill switch | ED un environnement sans configuration Twilio explicite · Q l'application démarre · A `NOTIFICATIONS_EXTERNAL_ENABLED`/`TWILIO_WHATSAPP_ENABLED`/`TWILIO_SMS_ENABLED` valent `false` par défaut, aucune erreur au démarrage, alertes in-app pleinement fonctionnelles ; un opérateur peut désactiver immédiatement un canal, un type de notification ou un tenant. | Must | ADR-18 §Feature flags |
| EF-124 | Plafond budgétaire mensuel | ED un volume d'envois approchant un plafond configurable · Q le compteur est évalué · A les envois du canal concerné sont limités ou arrêtés automatiquement, avec alerte dédiée ; aucun fallback SMS n'est jamais illimité. | Must | ADR-18 §Coûts |

---

## 2. Exigences non fonctionnelles (addendum)

| ID | Catégorie | Exigence | Critère d'acceptation | Source |
|----|-----------|----------|-----------------------|--------|
| ENF-94 | Sécurité — Signature des callbacks | Tout callback Twilio non signé valablement est rejeté sans effet de bord. | ED un callback avec une signature `X-Twilio-Signature` invalide ou absente · Q il est reçu · A rejet immédiat (4xx), aucune transition d'état, aucune information révélée dans la réponse. | ADR-18 §Sécurité |
| ENF-95 | RGPD — Minimisation du payload | Le payload transmis à Twilio ne contient jamais de donnée personnelle au-delà des variables de template strictement nécessaires. | ED un `NotificationEvent.payload_minimal` · Q un envoi est déclenché · A aucune adresse complète, donnée d'identité, information bancaire, contenu intégral de bail ou PDF base64 n'est transmis — uniquement les variables déclarées du template. | ADR-18 §Sécurité, §7 |
| ENF-96 | Observabilité — Absence de PII dans les métriques | Aucune métrique Prometheus ne porte de numéro de téléphone ou d'identifiant nominatif en label. | ED les métriques `notification.*` · Q elles sont scrapées · A les labels se limitent à `channel`/`status`/`error_category`, jamais à un identifiant de destinataire ou un numéro. | ADR-18 §Coûts |
| ENF-97 | Intégrité — Traitement concurrent de l'Outbox | Le traitement concurrent de `notification_outbox` par plusieurs threads/instances ne produit jamais de double envoi. | ED deux exécutions concurrentes du `NotificationDispatcher` · Q elles traitent le même lot · A `SELECT ... FOR UPDATE SKIP LOCKED` garantit qu'une même ligne n'est prise en charge que par une seule exécution ; la contrainte unique d'idempotence (EF-116) constitue une seconde ligne de défense. | ADR-18 §3, RSV-EP16-02 |

---

## 3. Registre des règles métier (RM) — *complète le registre ouvert par RM-100→113 (EP-15/EP-13)*

| ID | Règle métier | Exigence(s) liée(s) |
|----|--------------|----------------------|
| RM-114 | Une notification externe ne bloque, ne retarde ni n'annule jamais une opération métier ou financière. | EF-113, EF-114, EF-115 |
| RM-115 | Un même triplet (événement, destinataire, canal) n'est jamais envoyé deux fois. | EF-116 |
| RM-116 | Aucun service métier, contrôleur REST ni fonction SQL métier n'appelle directement Twilio. | EF-117 |
| RM-117 | Aucun message externe n'est envoyé sans consentement explicite, daté et actif pour ce canal précis. | EF-121 |
| RM-118 | Aucun SMS n'est envoyé sans une politique de fallback explicitement activée et un opt-in SMS actif. | EF-119 |
| RM-119 | Un template WhatsApp non approuvé ne peut jamais servir à un envoi réel. | EF-118 |
| RM-120 | Le dépassement d'un plafond budgétaire limite ou arrête automatiquement les envois du canal concerné. | EF-124 |
| RM-121 | Une erreur de livraison permanente ne déclenche jamais de nouvelle tentative automatique. | EF-118, EF-119 |
| RM-122 | Les alertes in-app restent pleinement fonctionnelles indépendamment de la disponibilité ou de l'activation de Twilio. | EF-123 |
| RM-123 | Aucune donnée de préférence, de consentement ou d'historique d'un autre bailleur/tenant n'est jamais visible. | EF-121, EF-122 |

---

## 4. Modèle de données (proposition logique — narratif, non implémenté)

### 4.1 Diagramme logique proposé

```
NotificationPreference (bailleur_id, recipient_type, recipient_id) ── RLS bailleur_isolation
NotificationEvent (bailleur_id, event_type, aggregate_type, aggregate_id) ── RLS bailleur_isolation
        │ 1..N (fan-out par destinataire/canal éligible)
        ▼
NotificationOutbox (event_id, recipient_id, channel) ── UNIQUE (event_id, recipient_id, notification_type, channel)
        │ 1..N (une ligne par tentative réelle envoyée au fournisseur)
        ▼
NotificationDelivery (event_id, recipient_id, channel, provider_message_id) ── ledger append-only (patron garantie_movement)

NotificationTemplate (code, channel, language, version) ── référentiel global, sans RLS (patron type_bien)
```

Détail complet des attributs : `ADR-18-notifications-multicanales-twilio.md` §Modèle de données.

### 4.2 Cardinalités et contraintes d'intégrité (conceptuelles)

| Règle | Contrainte conceptuelle |
|-------|--------------------------|
| RM-115 | `UNIQUE (event_id, recipient_id, notification_type, channel)` sur `notification_outbox` — clé d'idempotence portée en base, pas seulement en applicatif |
| RM-123 | `ENABLE`+`FORCE ROW LEVEL SECURITY` + policy `bailleur_isolation` sur `notification_preference`/`notification_event`/`notification_outbox`/`notification_delivery` — patron identique à `locataire`/`garantie_movement`/`quittance` |
| ADR-18 §Modèle | `notification_template` **sans** `bailleur_id` ni RLS — référentiel global, patron `type_bien` |

### 4.3 Impact migration base de données *(narratif)*

Migration(s) additive(s) uniquement (nouvelles tables, aucune modification de table existante) —
numéro à réserver au Plan d'Exécution après confirmation qu'aucune collision n'existe avec une
migration alors en cours (dernière migration réelle à ce jour : **V26**). Rollback applicatif
trivial.

---

## 5. Contrats d'API impactés (proposition — non implémentée)

| Endpoint (proposé) | Méthode | Description | Sécurité |
|---------------------|---------|--------------|----------|
| `/api/notifications/preferences/{recipientId}` | GET/PUT | Consulter/modifier les préférences de notification d'un destinataire de son périmètre | Titulaire, ou bailleur pour ses gestionnaires/locataires (selon K1) |
| `/api/notifications/preferences/{recipientId}/desinscription` | POST | Désinscription immédiate d'un canal externe | Titulaire, ou bailleur (selon K1) |
| `/api/notifications` *(historique)* | GET | Lister l'historique des notifications d'un destinataire de son périmètre | Scopé RLS, lecture seule (selon K6) |
| `/api/public/notifications/twilio/status` | POST | Callback de statut Twilio (webhook) | Non authentifié JWT — vérification de signature `X-Twilio-Signature` obligatoire, patron `PublicQuittanceController` |

> Contrats détaillés (schémas req/resp) à figer en OpenAPI au début de l'implémentation, après
> approbation du Plan d'Exécution — cohérent avec la pratique déjà actée sur ce projet.

---

## 6. Matrice de traçabilité (addendum)

| Besoin (EB) | Exigence (CDC) | Règle métier | Cas de test prévu |
|-------------|------------------|---------------|---------------------|
| BF-106 | EF-113/114/115 | RM-114/116 | TC-115 événement créé dans la même transaction que la quittance/garantie ; TC-116 événement batch créé avec le même anti-doublon que l'alerte |
| BF-108 | EF-116/117 | RM-114/115/116 | TC-117 rollback métier ⇒ aucune ligne Outbox persistée ; TC-118 retry/redémarrage ⇒ aucun doublon d'envoi |
| BF-106 | EF-118 | RM-119/121 | TC-119 template non approuvé ⇒ `DEAD`, aucun envoi ; TC-120 erreur permanente ⇒ pas de retry |
| BF-110 | EF-119 | RM-118 | TC-121 fallback SMS uniquement si opt-in + politique activée ; TC-122 aucun fallback si opt-in absent |
| BF-109 | EF-120 | — | TC-123 callback signature invalide ⇒ rejeté ; TC-124 callback dupliqué ⇒ aucune transition supplémentaire |
| BF-107 | EF-121 | RM-117/123 | TC-125 désinscription ⇒ aucun envoi externe immédiat ; TC-126 isolation cross-tenant des préférences |
| BF-111 | EF-122 | RM-123 | TC-127 isolation cross-tenant de l'historique |
| — | EF-123/124 | RM-120/122 | TC-128 démarrage sans config Twilio ⇒ sûr, in-app fonctionnel ; TC-129 dépassement de plafond ⇒ envois limités |

---

## 7. Score de maturité de l'addendum (/20)

| Axe | Note (0–4) | Commentaire |
|-----|-----------|-------------|
| Complétude | 1 | Huit points de kickoff (K1→K8) intégralement ouverts — cadrage architectural seul, aucun arbitrage produit rendu |
| Qualité | 4 | Critères ED/Q/A testables sur chaque EF, ancrés sur des points de code réels (transactions Paiement/Garantie/Quittance déjà identifiées) |
| Sécurité | 4 | Signature callback, minimisation RGPD, RLS systématique, absence de PII dans les métriques — tous couverts par des exigences dédiées (ENF-94→97) |
| Traçabilité | 4 | Matrice BF→EF→RM→TC complète, numérotation vérifiée sans collision (EF-113+, RM-114+, ENF-94+) |
| Automatisation | 0 | Aucun code, aucune migration — conforme à la contrainte documentaire de cette mission |
| **Total** | **13/20** | « Solide » côté qualité/sécurité/traçabilité mais **complétude délibérément minimale** tant que K1→K8 ne sont pas tranchés par le PO — ne constitue pas un Gate ; qualifie la maturité documentaire avant tout Plan d'Exécution GO |
