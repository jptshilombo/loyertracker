# ADR-18 — Notifications multicanales via Twilio, sans n8n (EP-16)

| Champ | Valeur |
|-------|--------|
| Statut | **Acceptée — kickoff K1→K8 clos (2026-07-19) et GO explicite du PO sur `plan-execution-ep16-notifications.md` reçu (2026-07-19).** Sprint N (Fondation) autorisé à démarrer ; Sprints N+1/N+2 restent soumis chacun à un GO distinct |
| Date | 2026-07-19 |
| Origine | Instruction PO du 2026-07-19 (« formaliser EP-16 notifications multicanales Twilio ») |
| Décision | **D-NOTIF-001** |
| Principe | Additif — n'invalide, ne rejoue ni ne modifie aucun Gate, décision, ADR ou risque déjà statué (ADR-14/15/16/17 notamment) |

## Contexte

LoyerTracker dispose aujourd'hui d'un système d'alertes **in-app uniquement** (US-50/51/52, EP-04) :
quatre types (`LOYER_EN_RETARD`, `FIN_BAIL`, `PREAVIS`, `GARANTIE_NON_RESTITUEE`), générés par la
fonction SQL idempotente `generer_alertes(p_preavis_jours)` (`SECURITY DEFINER`, owner
`loyertracker_batch`, recréée en V9→V10→V25), appelée par `AlertesScheduler` (cron quotidien
`07:00 Europe/Paris`) ou manuellement via `POST /api/batch/alertes`. L'anti-doublon repose sur un
index unique partiel réel, `uq_alerte_nonlue (type, bien_id, periode) WHERE statut = 'NON_LUE'`
(`V1__init_schema.sql`), consommé par `ON CONFLICT ... DO NOTHING` — mécanisme éprouvé en
Production depuis EP-04.

Aucun canal externe (SMS, WhatsApp, e-mail) n'existe. Aucune trace de n8n, de webhook sortant, de
SDK Twilio, ni de concept de consentement ou de préférence de notification n'a été trouvée dans le
code (recherche exhaustive backend + frontend). Le PO souhaite étendre le produit avec des
notifications WhatsApp/SMS via Twilio, **sans introduire n8n ni aucun orchestrateur externe**, et
sans que Twilio devienne une dépendance directe du domaine métier.

EP-14 (Quittances certifiées, ADR-15) a déjà résolu un problème voisin — transmettre une preuve à
un tiers sans exposer de PII inutile — via un lien public HMAC non expirant
(`TokenQuittanceService`, `PublicQuittanceController` sous `/api/public/receipts/...`, whitelisté
en sécurité, fonctions `SECURITY DEFINER` `lire_quittance_publique`/`lire_pdf_quittance_publique`,
journalisation `quittance_verification_log` sans `GRANT` direct à `loyertracker_api`). Ce mécanisme
est directement réutilisable pour l'avis WhatsApp de quittance disponible (§Décision, point 5).

## Problème

Concevoir l'architecture d'un système de notifications externes qui :

1. déclenche des notifications à partir d'événements métier existants sans coupler Twilio au
   domaine (`PaiementService`, `GarantieService`, `AlerteService`, contrôleurs REST, transactions
   métier, fonctions SQL métier) ;
2. garantit qu'aucune indisponibilité Twilio n'affecte une opération financière (paiement, débit
   de garantie, création de bail, émission de quittance) ;
3. garantit l'idempotence des envois (retry, redémarrage, timeout, réexécution batch, callback
   dupliqué) ;
4. permet le remplacement futur de Twilio, ou l'ajout d'un second fournisseur, sans réécrire les
   règles métier ;
5. traite consentement, sécurité des callbacks, minimisation RGPD et garde-fous budgétaires ;
6. s'articule avec le mécanisme d'alertes in-app existant **sans le remplacer** — l'in-app reste
   la source de vérité fonctionnelle, disponible même si Twilio est indisponible.

## Constats de l'analyse du code réel

Trois explorations factuelles ont précédé toute décision (backend, schéma/RLS, config+frontend) —
ce sont des faits vérifiés, pas des hypothèses :

1. **Aucune alerte n'est aujourd'hui créée de façon synchrone dans une transaction métier.**
   `PaiementService.pointer(...)` et `GarantieService.{creer,restituer,retenirSurLoyer,complementer}`
   sont `@Transactional`, écrivent systématiquement une ligne `audit_log` **dans la même
   transaction** (`AuditService.enregistrer(...)`, INSERT natif via `EntityManager`, jamais de
   `@Transactional` propre — commentaire de classe : *« la transaction de l'opération métier
   appelante »*), mais **ne créent jamais d'alerte**. `LOYER_EN_RETARD` et
   `GARANTIE_NON_RESTITUEE` ne sont surfacés que par le batch SQL `generer_alertes()`, qui lit un
   état déjà posé par ailleurs (`marquer_loyers_en_retard()` pour le premier, l'état de la
   `Garantie` + `Bail.CLOS` pour le second).
2. **`AuditService.enregistrer(...)` est le seul précédent réel d'écriture synchrone
   intra-transaction dans ce projet** — c'est le patron à reproduire pour toute émission
   d'événement de notification déclenchée depuis un service métier (voir §Décision, point 2).
3. **Pattern RLS strictement homogène** sur toutes les tables métier (`ENABLE`+`FORCE ROW LEVEL
   SECURITY`, policy nommée `bailleur_isolation`, `USING (bailleur_id =
   NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid)`) — appliqué sans exception
   depuis `V1`, y compris sur `patrimoine` (V12), `garantie_movement` (V20), `quittance` (V22),
   `locataire` (V24). `type_bien` est le seul contre-exemple documenté (référentiel global
   partagé, sans `bailleur_id`).
4. **Pattern ledger append-only déjà éprouvé** : `garantie_movement` (ADR-14, V20) — mouvement
   immuable, `solde_apres` capturé par ligne, cache dénormalisé synchrone sur la table parente
   (`garantie.solde_actuel`), `utilisateur`/`motif`/`commentaire` pour la traçabilité, aucun
   `DELETE` en usage normal. C'est le modèle direct pour `notification_delivery`.
5. **Aucun champ de préférence, de consentement ni de canal n'existe** sur `Locataire`
   (`telephone`/`email` nullable), `Gestionnaire` (`telephone`/`email`) ni `Bailleur` (pas de
   `telephone` du tout). Recherche exhaustive (`consentement|preference|opt.in|opt.out|consent`) :
   **aucun résultat**, ni backend ni frontend.
6. **Aucun secret, aucune dépendance Twilio, aucune trace n8n** dans `pom.xml`, `package.json`,
   `docker-compose*.yml`, CI. La convention de secrets (`${VAR}` obligatoire ou `${VAR:}` avec
   dégradation documentée si optionnel, cf. `QUITTANCE_HMAC_SECRET`) et de nommage
   (`<SOUS-SYSTÈME>_<RÔLE>`, ex. `KEYCLOAK_ADMIN_PASSWORD`, `QUITTANCE_HMAC_SECRET`) est directement
   transposable à `TWILIO_*`.
7. **Aucun service tiers dans Docker Compose** au-delà d'`api`/`nginx`/`postgres`/`keycloak` +
   overlay monitoring optionnel. Le pattern de déploiement ciblé (`--no-deps api nginx`) implique
   que tout traitement Outbox doit vivre **dans le processus `api` existant** (ex. un
   `@Scheduled` supplémentaire, à l'image d'`AlertesScheduler`) plutôt que dans un nouveau service
   Compose, sauf décision explicite contraire au Plan d'Exécution.
8. **Pattern d'endpoint public déjà éprouvé et transposable au callback Twilio** :
   `PublicQuittanceController` (`/api/public/receipts/...`, whitelist Spring Security, sans oracle,
   vérification cryptographique substituant l'authentification) + route Nginx dédiée avec
   `limit_req_zone` propre. Un callback de statut Twilio suivrait ce même triple patron
   (contrôleur whitelisté + location Nginx + zone de limitation dédiée + vérification de signature
   applicative — `X-Twilio-Signature` au lieu du token HMAC des quittances).
9. **`docs/cgpa/observability-governance.md` documente explicitement l'absence actuelle de
   service externe critique** (« hors périmètre actuel ») — cette évolution change ce périmètre et
   devra être répercutée dans ce document au Plan d'Exécution (additif, pas de réécriture).
10. **Aucune page frontend de gestion des locataires/gestionnaires n'existe** (le modèle
    `Locataire` n'est exposé qu'en API) — toute UI de préférences de notification est donc à
    construire depuis zéro, pas à greffer sur un écran existant.

## Options étudiées

| # | Option | Verdict |
|---|--------|---------|
| 1 | Appels Twilio directs dans les services métier (`PaiementService`, `GarantieService`, `AlerteService`, contrôleurs) | **Rejetée** — couple le domaine à un fournisseur tiers, viole le principe d'indépendance des opérations financières vis-à-vis d'un appel réseau externe, rend le remplacement de Twilio impossible sans réécrire le métier |
| 2 | Module interne + Outbox transactionnelle + adaptateur Twilio derrière une abstraction `NotificationProvider` | **Recommandée — D-NOTIF-001** |
| 3 | n8n comme orchestrateur de notifications | **Rejetée pour ce périmètre** — introduirait un composant d'exploitation supplémentaire (conteneur, base, secrets, supervision) sur un hôte de production dédié à faible capacité (`t3.medium`), sans bénéfice sur les exigences d'idempotence/transactionnalité qui doivent de toute façon être garanties côté LoyerTracker ; pourrait être réétudié séparément, hors EP-16 |
| 4 | Broker externe (Kafka/RabbitMQ) pour le découplage événementiel | **Différée** — disproportionnée à ce stade (aucun autre consommateur d'événements métier que ce système de notifications ; PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` suffit à un débit de quelques dizaines de notifications/jour, cf. §Volumétrie) |
| 5 | Fournisseur managé différent de Twilio | **Non retenue comme décision immédiate** — reste possible sans réécriture grâce à l'abstraction `NotificationProvider` (§Décision, point 4) |

## Décision D-NOTIF-001

**LoyerTracker utilisera directement Twilio comme fournisseur de transport des notifications
WhatsApp et SMS, sans n8n, encapsulé derrière une abstraction interne `NotificationProvider`.** Les
alertes in-app existantes restent la source de vérité fonctionnelle, disponibles même en cas
d'indisponibilité de Twilio, indépendantes du succès ou de l'échec d'un message externe. Les
notifications externes sont une **extension** des alertes existantes, jamais leur remplacement.

### 1. Architecture cible

```text
Transaction métier LoyerTracker (Paiement, Garantie, Quittance, Bail)
        │
        ├── données métier
        ├── audit_log                    (déjà existant, même transaction)
        ├── alerte in-app                (déjà existant — batch OU synchrone selon l'événement)
        └── notification_event + notification_outbox   (nouveau — même transaction)
                    │
                    ▼ (après commit, traitement séparé)
          NotificationDispatcher (@Scheduled, dans le processus `api` existant)
                    │
                    ▼
           NotificationProvider (interface)
                    │
                    ▼
             TwilioNotificationProvider
              ├── WhatsApp
              └── SMS
```

L'appel réseau Twilio intervient **après le commit** de la transaction métier, dans un traitement
`@Scheduled` séparé (même patron que `AlertesScheduler`) qui lit `notification_outbox` avec
verrouillage non bloquant (`SELECT ... FOR UPDATE SKIP LOCKED`, idiome PostgreSQL natif adapté à
un pattern Outbox concurrent). Aucune indisponibilité Twilio n'annule un paiement, un débit de
garantie, une création de bail, une émission de quittance ou une modification de statut.

### 2. Deux voies d'alimentation de l'Outbox — constat architectural clé

L'analyse du code réel (§Constats, point 1) impose de distinguer deux voies, et non une seule
« transaction métier » générique :

**Voie A — événements déjà batch-driven** (`LOYER_EN_RETARD`, `FIN_BAIL`, `PREAVIS`,
`GARANTIE_NON_RESTITUEE`) : l'événement n'existe aujourd'hui que comme résultat d'un batch SQL
(`generer_alertes()`). L'Outbox est alimentée en **étendant cette même fonction SQL**
(`SECURITY DEFINER`, transaction unique, réutilise l'anti-doublon `uq_alerte_nonlue` déjà prouvé) —
aucune modification de `PaiementService`/`GarantieService` requise, conforme à l'interdiction §4.1
sans aucune exception à négocier.

**Voie B — événements réellement transactionnels** (`QUITTANCE_DISPONIBLE`, `GARANTIE_DEBITEE`,
`PAIEMENT_RECU`, `BAIL_CREE`, `BAIL_CLOS`) : l'événement correspond à une transition métier
synchrone déjà `@Transactional` (`QuittanceCertifieeService.emettre(...)`,
`GarantieService.retenirSurLoyer(...)`, `PaiementService.pointer(...)`, `BailService.creer`/
`cloturer`). L'Outbox est alimentée par un **appel applicatif inline dans cette même transaction**,
exactement sur le modèle déjà en place pour `AuditService.enregistrer(...)` : ce n'est **pas** un
appel Twilio (interdit par §4.1), c'est une écriture de plus dans la même transaction — même
catégorie que l'audit, pas une exception à la règle.

Cette distinction n'introduit **aucun nouvel appel** dans les services métier au-delà de ce qu'ils
font déjà pour l'audit — elle documente simplement *où* placer l'appel selon la nature réelle de
l'événement, plutôt que d'imposer un unique point d'entrée générique qui n'existe pas dans le code
actuel.

### 3. Transactional Outbox

Voir §2 ci-dessus pour le mécanisme de production. Consommation : `NotificationDispatcher`
(`@Scheduled`, cadence configurable, ex. `${app.notifications.dispatch.cron:*/1 * * * *}`) lit
`notification_outbox` en statut `PENDING`/`RETRY` avec `next_attempt_at <= now()`, verrouille par
lot (`FOR UPDATE SKIP LOCKED`), traite hors transaction métier.

### 4. Idempotence

Clé d'idempotence canonique : `event_id + recipient_id + notification_type + channel`, portée par
une contrainte unique sur `notification_outbox` (et non une simple vérification applicative) — même
discipline que `uq_alerte_nonlue`/`uq_quittance_paiement_emise` : la garantie doit être en base, pas
seulement dans le code. Un `event_id` rejoué (retry, redémarrage, réexécution batch) ou un callback
Twilio reçu plusieurs fois ne doit jamais produire un second envoi.

### 5. Abstraction fournisseur

Interface `NotificationProvider` (package proposé `com.loyertracker.notifications.provider`),
implémentation initiale `TwilioNotificationProvider`. Les services métier ne connaissent ni le SDK
Twilio, ni les Account SID, ni les numéros expéditeurs, ni les Content SID WhatsApp, ni les codes
d'erreur Twilio — ils ne connaissent que `NotificationOutbox`/`NotificationEvent`. Le remplacement
futur de Twilio (ou l'ajout d'un second fournisseur) reste possible sans toucher au domaine.

### 6. Canaux

`IN_APP` (référence obligatoire, déjà existant) ; `WHATSAPP` (canal externe principal) ; `SMS`
(secours configurable, jamais automatique sans politique explicite — génère un coût distinct par
message, cf. K5).

### 7. Réutilisation du mécanisme de lien sécurisé des quittances (EP-14)

Pour `QUITTANCE_DISPONIBLE`, le message WhatsApp transmet l'URL déjà produite par
`QuittanceCertifieeService.emettre(...)` (`{urlBaseVerification}/verify/receipt/{id}?token=...&v=...`),
**jamais le PDF en base64**. Aucune modification du mécanisme HMAC/`TokenQuittanceService` — simple
consommation en lecture de l'URL déjà construite. La route frontend `/verify/receipt/:id` est déjà
publique (hors `authGuard`), donc immédiatement adaptée à un destinataire locataire non
authentifié dans LoyerTracker.

### 8. Aucun composant n8n

Confirmé par construction (§Options, point 3) : aucun conteneur, stack, workflow, webhook sortant,
base ou secret n8n. n8n reste explicitement hors périmètre d'EP-16, ré-étudiable séparément.

## Modèle de données recommandé (Option B — confirmée par l'analyse)

L'analyse du code confirme l'**Option B** (modèle `Notification` séparé, relié à l'événement
métier) plutôt que l'**Option A** (étendre `Alerte`) :

- `Alerte` porte un anti-doublon **(`type`, `bien_id`, `periode`)** déjà en Production et éprouvé
  — y ajouter une dimension canal/destinataire/statut de livraison changerait la sémantique d'une
  contrainte déjà utilisée par du code existant (`generer_alertes()`), avec un risque de
  régression sur un mécanisme critique et sans précédent dans ce projet de modification d'un index
  unique partiel déjà en charge en Production.
- `Alerte.destinataireId` est **mono-destinataire** (polymorphe mais un seul par ligne) — plusieurs
  événements (ex. `QUITTANCE_DISPONIBLE`) doivent notifier plusieurs destinataires
  (locataire **et** bailleur) sur des canaux différents, ce qu'un modèle séparé permet nativement
  par fan-out (une ligne `NotificationEvent`, plusieurs lignes `NotificationOutbox`).
- Mélanger l'historique fournisseur (retries, callbacks, codes d'erreur Twilio) dans la table
  `alerte` casserait la lecture actuelle du dashboard in-app (`AlerteService.lister`,
  `alertes-liste.component.ts`) sans aucun bénéfice.

**Modèle minimal retenu pour le premier lot** (à confirmer/ajuster au Plan d'Exécution) :

### `NotificationPreference`

Porte `bailleur_id` (RLS `bailleur_isolation` standard) même quand le destinataire réel est un
gestionnaire ou un locataire de ce bailleur — même patron polymorphe que
`Alerte.destinataireId`/`AuditLog.acteurId` (pas de FK stricte vers un type de destinataire
unique).

```text
id                 UUID PK (assigné application, comme locataire/quittance — pas gen_random_uuid())
bailleur_id        UUID NOT NULL REFERENCES bailleur(id)
recipient_type     VARCHAR(20) NOT NULL CHECK (IN ('BAILLEUR','GESTIONNAIRE','LOCATAIRE'))
recipient_id       UUID NOT NULL   -- polymorphe, sans FK stricte (patron Alerte/AuditLog)
phone_e164         VARCHAR(20)
preferred_channel  VARCHAR(20) NOT NULL DEFAULT 'IN_APP' CHECK (IN ('IN_APP','WHATSAPP','SMS'))
fallback_channel   VARCHAR(20) CHECK (IN ('SMS')) -- nullable, seul SMS peut être fallback (K5)
whatsapp_opt_in    BOOLEAN NOT NULL DEFAULT false
sms_opt_in         BOOLEAN NOT NULL DEFAULT false
consent_at         TIMESTAMPTZ
consent_source     VARCHAR(50)  -- ex. 'FORMULAIRE_LOYERTRACKER','SAISIE_BAILLEUR'
language           VARCHAR(5) NOT NULL DEFAULT 'fr'
enabled            BOOLEAN NOT NULL DEFAULT true
date_creation      TIMESTAMPTZ NOT NULL DEFAULT now()
date_desactivation TIMESTAMPTZ  -- patron archiver()/restaurer() (Locataire/Gestionnaire)
```

### `NotificationEvent`

Recipient-agnostique (le fan-out se fait dans `NotificationOutbox`).

```text
id              UUID PK DEFAULT gen_random_uuid()
bailleur_id     UUID NOT NULL REFERENCES bailleur(id)
event_type      VARCHAR(40) NOT NULL CHECK (IN ('QUITTANCE_DISPONIBLE','LOYER_EN_RETARD', ...))
aggregate_type  VARCHAR(30) NOT NULL   -- 'BAIL','GARANTIE','QUITTANCE','PAIEMENT'
aggregate_id    UUID NOT NULL
payload_version SMALLINT NOT NULL DEFAULT 1
payload_minimal JSONB NOT NULL  -- strictement les variables de template, jamais de PII étendue
date_creation   TIMESTAMPTZ NOT NULL DEFAULT now()
```

### `NotificationOutbox`

```text
id               UUID PK DEFAULT gen_random_uuid()
event_id         UUID NOT NULL REFERENCES notification_event(id)
recipient_id     UUID NOT NULL
notification_type VARCHAR(40) NOT NULL
channel          VARCHAR(20) NOT NULL CHECK (IN ('WHATSAPP','SMS'))
statut           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 CHECK (IN ('PENDING','PROCESSING','PROCESSED','RETRY','DEAD'))
attempt_count    INTEGER NOT NULL DEFAULT 0
next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now()
locked_at        TIMESTAMPTZ
processed_at     TIMESTAMPTZ
last_error_code  VARCHAR(50)
date_creation    TIMESTAMPTZ NOT NULL DEFAULT now()

UNIQUE (event_id, recipient_id, notification_type, channel)  -- clé d'idempotence, §4
```

### `NotificationDelivery`

Modèle ledger append-only, calqué sur `garantie_movement` (ADR-14) : jamais de `DELETE`, une ligne
par tentative réelle envoyée au fournisseur (pas par retry logique — un retry qui échoue avant
envoi réseau reste sur `NotificationOutbox`).

```text
id                  UUID PK DEFAULT gen_random_uuid()
bailleur_id         UUID NOT NULL REFERENCES bailleur(id)
event_id            UUID NOT NULL REFERENCES notification_event(id)
recipient_id        UUID NOT NULL
channel             VARCHAR(20) NOT NULL
provider            VARCHAR(20) NOT NULL DEFAULT 'TWILIO'
provider_message_id VARCHAR(100)
statut              VARCHAR(20) NOT NULL
                    CHECK (IN ('QUEUED','ACCEPTED','SENT','DELIVERED','READ','FAILED','UNDELIVERED','CANCELLED'))
attempt_count       INTEGER NOT NULL DEFAULT 1
sent_at             TIMESTAMPTZ
delivered_at        TIMESTAMPTZ
read_at             TIMESTAMPTZ
failed_at           TIMESTAMPTZ
error_code          VARCHAR(50)
error_category      VARCHAR(20) CHECK (IN ('TEMPORAIRE','PERMANENT'))
date_creation       TIMESTAMPTZ NOT NULL DEFAULT now()
```

### `NotificationTemplate`

**Référentiel global** (comme `type_bien`), non scopé `bailleur_id` : aucune preuve dans le code
qu'un bailleur personnalise jamais un texte (quittances, alertes — tout est actuellement codé en
dur), donc pas de personnalisation par bailleur au premier lot.

```text
id                   UUID PK DEFAULT gen_random_uuid()
code                 VARCHAR(50) NOT NULL  -- ex. 'LOYER_EN_RETARD_V1'
channel              VARCHAR(20) NOT NULL
language             VARCHAR(5) NOT NULL DEFAULT 'fr'
version              INTEGER NOT NULL DEFAULT 1
provider_template_id VARCHAR(100)  -- Content SID Twilio
approval_status      VARCHAR(20) NOT NULL DEFAULT 'BROUILLON'
                     CHECK (IN ('BROUILLON','SOUMIS','APPROUVE','REJETE'))
enabled              BOOLEAN NOT NULL DEFAULT false
date_creation        TIMESTAMPTZ NOT NULL DEFAULT now()

UNIQUE (code, channel, language, version)
```

Toutes les tables `bailleur_id`-scopées suivent le patron RLS standard (`ENABLE`+`FORCE ROW LEVEL
SECURITY`, policy `bailleur_isolation`) sans exception. `NotificationTemplate` est un référentiel
global sans RLS, comme `type_bien`. Le traitement multi-bailleur de l'Outbox par
`NotificationDispatcher` nécessitera soit le rôle `loyertracker_batch` (BYPASSRLS) + fonctions
`SECURITY DEFINER` dédiées (patron `generer_alertes`), soit une itération bailleur par bailleur
sous RLS classique — à trancher au Plan d'Exécution, aucun précédent exact dans ce projet pour un
job batch multi-bailleur traitant une table de « travail en cours » (par opposition aux jobs de
lecture/génération existants).

## Statuts

Voir tableaux `NotificationOutbox`/`NotificationDelivery` ci-dessus. Distinction stricte :
- **Statut interne** (`NotificationOutbox.statut`) : cycle de vie de la tentative de traitement.
- **Statut fournisseur** (`NotificationDelivery.statut`) : reflet des statuts Twilio (`queued`,
  `sent`, `delivered`, `read`, `failed`, `undelivered`).
- **Terminal** : `PROCESSED`/`DEAD` (Outbox), `DELIVERED`/`READ`/`FAILED`/`UNDELIVERED`/`CANCELLED`
  (Delivery).
- `SENT`/`DELIVERED` **ne constituent jamais une preuve de lecture** ni une preuve juridique de
  notification — seul `READ` (retour WhatsApp, non garanti selon la config du destinataire) s'en
  approche, sans valeur probante formelle.
- Erreur temporaire → `RETRY` avec `next_attempt_at` en backoff exponentiel plafonné ; erreur
  permanente (numéro invalide, opt-out fournisseur) → `DEAD` direct, jamais de retry. Délai maximal
  et nombre de tentatives à fixer au Plan d'Exécution. Reprise manuelle : réinitialisation ciblée
  d'une ligne `DEAD` vers `PENDING` par un opérateur, jamais automatique.

## Sécurité

**Secrets** (hors dépôt, convention `${VAR}`/`${VAR:}` déjà en place) : `TWILIO_ACCOUNT_SID`,
`TWILIO_AUTH_TOKEN`, `TWILIO_API_KEY`, `TWILIO_API_SECRET`, `TWILIO_WHATSAPP_FROM`,
`TWILIO_SMS_FROM`, `TWILIO_STATUS_CALLBACK_BASE_URL`. Ne pas imposer Auth Token **et** API Key
simultanément sans justification : privilégier une **clé API restreinte** (moindre privilège) si
le mode Twilio utilisé le permet, l'Auth Token maître restant un secours à protéger davantage.
Secrets distincts par environnement (dev/staging/prod), comme `KEYCLOAK_API_CLIENT_SECRET` l'est
déjà.

**Webhooks Twilio** : endpoint dédié whitelisté (patron `PublicQuittanceController` +
`location /api/public/` Nginx avec `limit_req_zone` propre), validation obligatoire de la
signature Twilio (`X-Twilio-Signature`), rejet des signatures invalides, idempotence (clé
`provider_message_id` + type d'événement callback), taille de payload limitée, aucune donnée
interne révélée dans la réponse, journalisation des seules métadonnées nécessaires (pas de contenu
de message).

**Données personnelles** : payload Twilio minimisé (`payload_minimal` JSONB, variables de
template uniquement) — jamais adresse complète, données d'identité, informations bancaires,
contenu intégral du bail, PDF base64, données d'un autre bien ou d'un autre tenant.

**Documents** : jamais de PDF en base64 — réutilisation du lien de vérification HMAC déjà produit
par `QuittanceCertifieeService`/`TokenQuittanceService` (§Décision, point 7), sans contourner le
token, la version, le statut ni le hash existants.

**Cloisonnement** : toutes les nouvelles tables `bailleur_id`-scopées protégées par RLS
`bailleur_isolation`, tests cross-tenant obligatoires (patron déjà systématique dans ce projet,
ex. `PatrimoineServiceTest`, tests d'intégration S02-S04) — un gestionnaire ne doit jamais voir les
notifications d'un autre gestionnaire ou d'un autre bailleur.

## Consentement et préférences

Aucun mécanisme équivalent n'existe aujourd'hui (§Constats, point 5) — c'est une notion
entièrement nouvelle. La présence d'un numéro de téléphone **n'est jamais** un consentement
implicite. `NotificationPreference` distingue coordonnée téléphonique, canal préféré/secours,
opt-in WhatsApp, opt-in SMS, désinscription, chacun daté (`consent_at`) et sourcé
(`consent_source`). Mode de recueil et rétention tranchés par le PO le 2026-07-19 : formulaire
natif LoyerTracker (K3, `consent_source = 'FORMULAIRE_LOYERTRACKER'`), rétention des métadonnées de
livraison alignée sur l'audit métier existant (K7). Impact à vérifier sur `Gestionnaire`,
`Locataire` et éventuellement `Bailleur` (aucun n'a de champ téléphone systématiquement renseigné
aujourd'hui).

## Templates WhatsApp

Concept formalisé (`NotificationTemplate`) sans création des templates réels dans cette mission :
code interne (ex. `LOYER_EN_RETARD_V1`, `QUITTANCE_DISPONIBLE_V1`, `GARANTIE_DEBITEE_V1`), langue,
version, Content SID Twilio, statut d'approbation, variables autorisées, taille maximale,
désactivation, fallback si template non approuvé. Les textes ne sont **jamais** codés en dur dans
les services métier — toujours résolus via `NotificationTemplate`.

## Coûts et garde-fous budgétaires

Compteur par canal et par type de notification, coût estimé, plafond mensuel configurable, arrêt
ou limitation en cas de dépassement, **jamais** de fallback SMS illimité, alertes sur hausse
anormale du volume. Métriques (convention `<domaine>.<action>{tags}`, sans préfixe `loyertracker.`
générique — patron `quittance.verifications{resultat}` déjà en place) :

```text
notification.outbox.pending
notification.outbox.oldest.seconds
notification.delivery.total{channel,status}
notification.delivery.failures.total{channel,error_category}
notification.retry.total{channel}
notification.dead_letter.total
notification.provider.latency.seconds
```

**Aucun numéro de téléphone dans les labels Prometheus.** `docs/cgpa/observability-governance.md`
devra être étendu (additif) pour couvrir Twilio comme service externe critique, aujourd'hui
explicitement listé comme hors périmètre.

## Feature flags et kill switch

```text
NOTIFICATIONS_EXTERNAL_ENABLED=false
TWILIO_WHATSAPP_ENABLED=false
TWILIO_SMS_ENABLED=false
NOTIFICATION_DRY_RUN=true
```

Premier flag booléen `app.notifications.*.enabled` de ce projet — s'insère naturellement dans le
bloc `app:` existant d'`application.yml` (aux côtés de `app.cors`, `app.invitation`,
`app.alertes.preavis`). Démarrage par défaut sûr : aucun message externe, aucune erreur bloquante,
alertes in-app toujours fonctionnelles. Kill switch opérationnel : désactivation immédiate de tout
envoi externe, ou uniquement WhatsApp, ou uniquement SMS, ou un type de notification, ou un tenant
si nécessaire.

## Rollback

Migration(s) additive(s) uniquement (nouvelles tables, aucune modification de table existante) —
rollback applicatif trivial : redéploiement du tag précédent, `NOTIFICATIONS_EXTERNAL_ENABLED=false`
en dernier recours si un incident survient après activation. Aucun impact sur `Alerte`, `Paiement`,
`Garantie`, `Quittance` existants.

## Volumétrie (pour justifier l'absence de broker, §Options point 4)

Baseline Production actuelle (`docs/prod-state.md`) : 3 bailleurs, 8 biens, 8 baux — un volume de
notifications de l'ordre de quelques dizaines par jour au P0, très loin du seuil où un broker
événementiel externe apporterait un bénéfice face à son coût d'exploitation sur un hôte
`t3.medium` déjà à 8 conteneurs.

## Conséquences

- ✅ Débloque un canal de communication proactif (WhatsApp/SMS) sans jamais coupler Twilio au
  domaine métier.
- ✅ Réutilise trois patrons déjà éprouvés en Production (RLS `bailleur_isolation`, ledger
  append-only `garantie_movement`, lien HMAC des quittances) — aucune invention architecturale non
  justifiée par le code existant.
- ✅ L'in-app reste pleinement fonctionnel et indépendant, y compris si Twilio est indisponible ou
  jamais activé (feature flags à `false` par défaut).
- ⚠️ Introduit la première notion de « travail en cours » à traiter par un worker concurrent
  (`FOR UPDATE SKIP LOCKED`) — aucun précédent exact dans ce projet ; nécessitera une attention de
  test particulière (verrouillage concurrent, double traitement).
- ⚠️ Introduit un service externe critique (Twilio) dans le périmètre d'observabilité, jusqu'ici
  explicitement hors périmètre (`observability-governance.md`).
- ⚠️ Coût d'exploitation réel (Twilio facturé au message) — nécessite un plafond budgétaire
  opérationnel dès le premier envoi réel, pas seulement au P1.
- ✅ Kickoff K1→K8 tranché par le PO le 2026-07-19 (quatre recommandations par défaut adoptées —
  K1, K2, K5, K8 ; quatre décisions propres du PO en l'absence de recommandation — K3, K4, K6, K7)
  et GO explicite du PO reçu le même jour sur `plan-execution-ep16-notifications.md` — le Sprint N
  (Fondation) est autorisé à démarrer. Les Sprints N+1/N+2 restent soumis chacun à un GO distinct,
  après clôture Gate Staging/Production du sprint précédent.

## Impacts sécurité

RBAC/ReBAC inchangés pour les entités existantes. Nouvelle surface : lecture/écriture des
préférences de notification (réservée au titulaire et, conformément à K1/K6 tranchés le
2026-07-19, au bailleur pour l'ensemble de son périmètre et au gestionnaire pour les
biens/baux qui lui sont affectés) ; nouvel endpoint callback public (whitelisté, vérifié par
signature Twilio, jamais par JWT). Aucune nouvelle fonction `SECURITY DEFINER` cross-tenant n'est nécessaire
pour les préférences elles-mêmes (RLS standard suffit) ; une fonction dédiée pourrait être
nécessaire pour le traitement batch multi-bailleur de l'Outbox (à confirmer au Plan d'Exécution).

## Impacts RGPD (ADR-03)

Nouvelle catégorie de donnée personnelle (numéro de téléphone, historique de livraison) à intégrer
au flux d'export/effacement RGPD déjà en place (`RgpdService.exporter`/`anonymiserLocataire`) —
l'anonymisation d'un `Locataire` devra couvrir `NotificationPreference`/`NotificationDelivery`
associés. Détail à trancher au Plan d'Exécution (K7 — rétention).

## Registre des risques (RSV-EP16)

| # | Risque | Mitigation proposée | Statut |
|---|--------|----------------------|--------|
| RSV-EP16-01 | Double alimentation de l'Outbox (voie batch et voie transactionnelle) mal isolée, produisant un doublon d'événement pour un même fait métier | Un seul type d'événement par voie, jamais les deux pour le même `event_type` — à vérifier explicitement au Plan d'Exécution et par test dédié | Ouvert — point de conception à verrouiller avant codage |
| RSV-EP16-02 | Traitement concurrent de l'Outbox par plusieurs threads/instances produisant un double envoi | `FOR UPDATE SKIP LOCKED` + contrainte unique d'idempotence (§4) ; test de concurrence dédié requis | Ouvert — mitigation de conception, à prouver par test |
| RSV-EP16-03 | Dérive budgétaire si le plafond mensuel n'est pas opérationnel dès le premier envoi réel | Aucune activation Production tant que plafond + compteurs + kill switch ne sont pas opérationnels (cf. §14 du prompt, recommandation reprise au Plan d'Exécution) | Accepté comme condition de Gate Production, non bloquant pour le cadrage |
| RSV-EP16-04 | Consentement absent ou mal recueilli conduisant à un envoi non désiré (risque réglementaire WhatsApp Business/RGPD) | Aucune activation d'un canal externe sans `opt_in` explicite et daté ; aucun message hors template approuvé | Accepté par le PO (2026-07-19) — K3 tranché (formulaire LoyerTracker), risque résiduel assumé, mitigation à implémenter au Sprint N+1 |
| RSV-EP16-05 | Introduction d'un service externe critique non couvert par l'observabilité actuelle | Extension additive de `observability-governance.md` au Plan d'Exécution, avant toute activation Production | Ouvert — à traiter au Plan d'Exécution |
| RSV-EP16-06 | `NotificationTemplate` référentiel global pourrait s'avérer insuffisant si un bailleur demande une personnalisation de texte | Aucune preuve actuelle de ce besoin (tout texte est aujourd'hui codé en dur, y compris pour les quittances) — accepté comme limitation du P0, réévaluable si demande PO explicite | Accepté (en surveillance) |

## Décisions — kickoff clos le 2026-07-19 (PO)

> **Le PO a tranché K1→K8 le 2026-07-19.** Quatre points disposaient d'une recommandation par
> défaut dans `plan-execution-ep16-notifications.md` (K1, K2, K5, K8) — **toutes adoptées sans
> modification**. Les quatre autres (K3, K4, K6, K7) n'avaient **aucune recommandation par
> défaut** documentée (le Plan renvoyait explicitement à un arbitrage PO sans proposition) — le PO
> a donné une décision propre pour chacun, reprise ci-dessous. **Aucun GO n'est donné sur le Plan
> d'Exécution par ce tranchage** : il reste requis, distinctement, avant tout Sprint.

### K1 — Destinataires par événement — **Tranché (PO, 2026-07-19) : recommandation adoptée**

**Décision** : P0 = locataire pour quittance disponible/garantie débitée/loyer en retard ; bailleur
et gestionnaire pour le suivi opérationnel, selon autorisations et préférences
(`NotificationPreference`). Dimensionne le fan-out de `NotificationEvent` → `NotificationOutbox`.

### K2 — Canal principal — **Tranché (PO, 2026-07-19) : recommandation adoptée**

**Décision** : `IN_APP` obligatoire (déjà existant, jamais désactivable), `WHATSAPP` canal externe
principal, `SMS` en secours uniquement (cf. K5).

### K3 — Mode de recueil du consentement — **Tranché (PO, 2026-07-19) : formulaire LoyerTracker**

**Décision** : le consentement (`whatsapp_opt_in`/`sms_opt_in`, `consent_at`, `consent_source =
'FORMULAIRE_LOYERTRACKER'`) est recueilli via un formulaire natif dans l'application, opt-in
explicite saisi par ou pour le destinataire — aucune présomption de consentement à partir de la
seule présence d'un numéro de téléphone.

**Alternatives écartées** : preuve externe saisie par le bailleur (pas de trace vérifiable côté
LoyerTracker), invitation avec confirmation par lien (réservé pour une itération future si le
formulaire s'avère insuffisant), OTP (jugé disproportionné pour le P0).

### K4 — Stratégie de numéro Twilio — **Tranché (PO, 2026-07-19) : réutiliser un numéro existant**

**Décision** : réutilisation d'un numéro déjà détenu, sous réserve d'éligibilité Twilio/WhatsApp
Business au moment du provisionnement réel (Sprint N+1, hors périmètre de ce cadrage). Aucun
numéro Twilio dédié à provisionner par défaut.

### K5 — Fallback SMS — **Tranché (PO, 2026-07-19) : recommandation adoptée**

**Décision** : pas de fallback SMS automatique au premier pilote — `fallback_channel` reste
configurable dans le modèle de données mais non activé par défaut (`TWILIO_SMS_ENABLED=false`).

### K6 — Historique visible — **Tranché (PO, 2026-07-19) : bailleur + gestionnaire sur son périmètre**

**Décision** : le bailleur voit l'historique complet de son périmètre ; un gestionnaire ne voit que
l'historique des notifications liées aux biens/baux qui lui sont affectés — cohérent avec le
RBAC/RLS déjà en place pour les autres écrans Gestionnaire (aucun accès cross-gestionnaire).

### K7 — Rétention des métadonnées de livraison — **Tranché (PO, 2026-07-19) : alignement sur l'audit métier**

**Décision** : `NotificationDelivery` suit la même politique de rétention que `audit_log`, sans
durée fixe distincte définie par cet arbitrage — confirme la recommandation partielle déjà écrite
au Plan d'Exécution.

### K8 — Stratégie de release — **Tranché (PO, 2026-07-19) : recommandation adoptée**

**Décision** : déployer le socle désactivé (`NOTIFICATIONS_EXTERNAL_ENABLED=false` et dérivés),
valider Staging à chaque Sprint, puis activer progressivement uniquement après validation complète
du P0 (Sprint N+2 clos en GO).

## Kickoff tranché par le PO le 2026-07-19

| # | Question | Décision | Statut |
|---|----------|----------|--------|
| K1 | Destinataires par événement | Locataire (P0) ; bailleur/gestionnaire pour le suivi opérationnel | ✅ Tranché |
| K2 | Canal principal | IN_APP obligatoire, WHATSAPP principal, SMS secours | ✅ Tranché |
| K3 | Mode de recueil du consentement | Formulaire LoyerTracker | ✅ Tranché |
| K4 | Stratégie de numéro | Réutiliser un numéro existant | ✅ Tranché |
| K5 | Fallback SMS | Aucun fallback automatique au premier pilote | ✅ Tranché |
| K6 | Historique visible | Bailleur (tout) + gestionnaire (son périmètre) | ✅ Tranché |
| K7 | Rétention des métadonnées de livraison | Alignement sur l'audit métier existant | ✅ Tranché |
| K8 | Stratégie de release | Socle désactivé → Staging → activation progressive post-P0 | ✅ Tranché |

**Le kickoff K1→K8 est clos et le PO a donné son GO explicite sur
`plan-execution-ep16-notifications.md` le 2026-07-19 : le Sprint N (Fondation) est autorisé à
démarrer.** Les Sprints N+1/N+2 restent soumis chacun à un GO distinct.

## Compatibilité et migration

Aucune migration créée par ce cadrage. Migration(s) future(s) strictement additive(s) (nouvelles
tables uniquement, aucune colonne ajoutée aux tables existantes) — numéro à réserver au Plan
d'Exécution après confirmation qu'aucune collision n'existe avec une migration alors en cours
(dernière migration réelle à ce jour : **V26**, donc **V27** le prochain numéro libre, à
reconfirmer au moment du codage). Rollback applicatif trivial (aucune suppression de colonne,
contrairement à V26/Sprint C EP-15).
