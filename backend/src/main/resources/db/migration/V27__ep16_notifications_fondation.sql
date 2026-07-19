-- =====================================================================================
-- LoyerTracker — Migration V27 : EP-16 Sprint N (Fondation) — Notifications multicanales
-- via Twilio, ADR-18/D-NOTIF-001. Kickoff K1→K8 tranché par le PO le 2026-07-19, GO explicite
-- sur plan-execution-ep16-notifications.md reçu le même jour (Sprint N uniquement).
--
-- Migration strictement additive (5 nouvelles tables, aucune table existante modifiée) —
-- rollback applicatif trivial. Aucun envoi réel possible à ce stade : aucune dépendance Twilio,
-- feature flags à `false` par défaut (application.yml), NoopNotificationProvider seul fournisseur.
--
-- Modèle Option B (ADR-18) : notification_preference/event/outbox/delivery sous RLS
-- bailleur_isolation standard ; notification_template référentiel global sans RLS (patron
-- type_bien, V12). notification_outbox porte son propre bailleur_id (dénormalisé depuis
-- notification_event) : ajustement du schéma esquissé par l'ADR (qui l'omettait), nécessaire
-- pour respecter la RLS bailleur_isolation exigée pour "les quatre premières" tables — l'ADR
-- indiquait explicitement ce modèle comme "à confirmer/ajuster au Plan d'Exécution".
--
-- Deux voies d'alimentation de l'Outbox (ADR-18 §2), toutes deux actives dès ce sprint :
--   Voie A (batch) : extension de generer_alertes() ci-dessous, pour LOYER_EN_RETARD/FIN_BAIL/
--     PREAVIS/GARANTIE_NON_RESTITUEE — fan-out limité au bailleur (même destinataire que
--     l'alerte in-app existante, `destinataire_id = bailleur_id`) : aucune fonction de fan-out
--     gestionnaire/locataire n'existe aujourd'hui à ce niveau, hors périmètre de ce sprint.
--   Voie B (transactionnelle) : écriture inline côté Java dans QuittanceCertifieeService/
--     GarantieService/PaiementService/BailService (QUITTANCE_DISPONIBLE/GARANTIE_DEBITEE/
--     PAIEMENT_RECU/BAIL_CREE/BAIL_CLOS), même patron que AuditService.enregistrer(...).
--
-- Idempotence (ADR-18 §4) : contrainte unique réelle en base sur notification_outbox
-- (event_id, recipient_id, notification_type, channel) — jamais une simple vérification
-- applicative.
-- =====================================================================================

-- --- 1. NotificationPreference (US-119, K3 formulaire LoyerTracker) --------------------
CREATE TABLE notification_preference (
    id                 UUID PRIMARY KEY,
    bailleur_id        UUID NOT NULL REFERENCES bailleur (id),
    recipient_type     VARCHAR(20) NOT NULL
                       CHECK (recipient_type IN ('BAILLEUR', 'GESTIONNAIRE', 'LOCATAIRE')),
    recipient_id       UUID NOT NULL,
    phone_e164         VARCHAR(20),
    preferred_channel  VARCHAR(20) NOT NULL DEFAULT 'IN_APP'
                       CHECK (preferred_channel IN ('IN_APP', 'WHATSAPP', 'SMS')),
    fallback_channel   VARCHAR(20) CHECK (fallback_channel IN ('SMS')),
    whatsapp_opt_in    BOOLEAN NOT NULL DEFAULT false,
    sms_opt_in         BOOLEAN NOT NULL DEFAULT false,
    consent_at         TIMESTAMPTZ,
    consent_source     VARCHAR(50),
    language           VARCHAR(5) NOT NULL DEFAULT 'fr',
    enabled            BOOLEAN NOT NULL DEFAULT true,
    date_creation      TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_desactivation TIMESTAMPTZ,
    UNIQUE (bailleur_id, recipient_type, recipient_id)
);

CREATE INDEX idx_notification_preference_bailleur ON notification_preference (bailleur_id);

ALTER TABLE notification_preference ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_preference FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON notification_preference
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON notification_preference TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_preference TO loyertracker_api;

-- --- 2. NotificationEvent (US-120, recipient-agnostic — fan-out dans l'Outbox) --------
CREATE TABLE notification_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id     UUID NOT NULL REFERENCES bailleur (id),
    event_type      VARCHAR(40) NOT NULL
                    CHECK (event_type IN (
                        'QUITTANCE_DISPONIBLE', 'LOYER_EN_RETARD', 'FIN_BAIL', 'PREAVIS',
                        'GARANTIE_NON_RESTITUEE', 'GARANTIE_DEBITEE', 'PAIEMENT_RECU',
                        'BAIL_CREE', 'BAIL_CLOS')),
    aggregate_type  VARCHAR(30) NOT NULL
                    CHECK (aggregate_type IN ('BAIL', 'GARANTIE', 'QUITTANCE', 'PAIEMENT')),
    aggregate_id    UUID NOT NULL,
    payload_version SMALLINT NOT NULL DEFAULT 1,
    payload_minimal JSONB NOT NULL,
    date_creation   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_event_bailleur ON notification_event (bailleur_id);

ALTER TABLE notification_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_event FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON notification_event
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON notification_event TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_event TO loyertracker_api;

-- --- 3. NotificationOutbox (US-120, RSV-EP16-01/02 — idempotence + concurrence) -------
CREATE TABLE notification_outbox (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id        UUID NOT NULL REFERENCES bailleur (id),
    event_id           UUID NOT NULL REFERENCES notification_event (id),
    recipient_id       UUID NOT NULL,
    notification_type  VARCHAR(40) NOT NULL,
    channel            VARCHAR(20) NOT NULL CHECK (channel IN ('WHATSAPP', 'SMS')),
    statut             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (statut IN ('PENDING', 'PROCESSING', 'PROCESSED', 'RETRY', 'DEAD')),
    attempt_count      INTEGER NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at          TIMESTAMPTZ,
    processed_at       TIMESTAMPTZ,
    last_error_code    VARCHAR(50),
    date_creation      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_outbox_idempotence
        UNIQUE (event_id, recipient_id, notification_type, channel)
);

CREATE INDEX idx_notification_outbox_bailleur ON notification_outbox (bailleur_id);
-- Support de la réclamation par lot (FOR UPDATE SKIP LOCKED, RSV-EP16-02) : PENDING/RETRY dus.
CREATE INDEX idx_notification_outbox_statut_next
    ON notification_outbox (statut, next_attempt_at);

ALTER TABLE notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_outbox FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON notification_outbox
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON notification_outbox TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_outbox TO loyertracker_api;

-- --- 4. NotificationDelivery (ledger append-only, patron garantie_movement V20) -------
-- Aucune écriture applicative en Sprint N (aucun envoi réel) : table créée pour compléter le
-- modèle de données sans migration non additive ultérieure ; alimentée à partir du Sprint N+1
-- (callbacks Twilio réels).
CREATE TABLE notification_delivery (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id         UUID NOT NULL REFERENCES bailleur (id),
    event_id            UUID NOT NULL REFERENCES notification_event (id),
    recipient_id        UUID NOT NULL,
    channel             VARCHAR(20) NOT NULL CHECK (channel IN ('WHATSAPP', 'SMS')),
    provider            VARCHAR(20) NOT NULL DEFAULT 'TWILIO',
    provider_message_id VARCHAR(100),
    statut              VARCHAR(20) NOT NULL
                        CHECK (statut IN ('QUEUED', 'ACCEPTED', 'SENT', 'DELIVERED', 'READ',
                                           'FAILED', 'UNDELIVERED', 'CANCELLED')),
    attempt_count       INTEGER NOT NULL DEFAULT 1,
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    failed_at           TIMESTAMPTZ,
    error_code          VARCHAR(50),
    error_category      VARCHAR(20) CHECK (error_category IN ('TEMPORAIRE', 'PERMANENT')),
    date_creation       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_delivery_bailleur ON notification_delivery (bailleur_id);
CREATE INDEX idx_notification_delivery_event ON notification_delivery (event_id);

ALTER TABLE notification_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_delivery FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON notification_delivery
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON notification_delivery TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_delivery TO loyertracker_api;

-- --- 5. NotificationTemplate (référentiel global, patron type_bien V12) ---------------
-- Aucun template créé par ce sprint (aucun compte Twilio provisionné, §19 hors périmètre) :
-- table vide, alimentée au Sprint N+1.
CREATE TABLE notification_template (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(50) NOT NULL,
    channel              VARCHAR(20) NOT NULL CHECK (channel IN ('WHATSAPP', 'SMS')),
    language             VARCHAR(5) NOT NULL DEFAULT 'fr',
    version              INTEGER NOT NULL DEFAULT 1,
    provider_template_id VARCHAR(100),
    approval_status      VARCHAR(20) NOT NULL DEFAULT 'BROUILLON'
                         CHECK (approval_status IN ('BROUILLON', 'SOUMIS', 'APPROUVE', 'REJETE')),
    enabled              BOOLEAN NOT NULL DEFAULT false,
    date_creation        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, channel, language, version)
);

COMMENT ON TABLE notification_template IS
    'Référentiel administrable des templates de notification (ADR-18 §Templates) — partagé entre bailleurs, sans RLS, patron type_bien (V12).';

GRANT SELECT, INSERT, UPDATE, DELETE ON notification_template TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON notification_template TO loyertracker_api;

-- --- 6. Extension de generer_alertes() — Voie A (ADR-18 §2) ---------------------------
-- Chaque bloc existant (LOYER_EN_RETARD/FIN_BAIL/PREAVIS/GARANTIE_NON_RESTITUEE) alimente
-- désormais, en plus de `alerte`, un `notification_event` + fan-out `notification_outbox` pour
-- le même destinataire que l'alerte in-app (le bailleur — `destinataire_id` est déjà
-- systématiquement `bailleur_id` dans ce batch, aucun fan-out gestionnaire/locataire à ce
-- niveau). Le fan-out ne crée une ligne Outbox que si une NotificationPreference existe,
-- est active (`enabled`) et opt-in pour son canal préféré (jamais IN_APP, qui ne passe pas par
-- l'Outbox). `total` (valeur de retour, alertesCreees) reste calculé UNIQUEMENT à partir des
-- lignes `alerte` insérées, par construction (les CTE `evt`/`fan` ne sont jamais comptées) :
-- aucune régression sur les tests existants qui vérifient ce compteur (S04AlertesAuditIntegrationTest).
-- Les CTE de type INSERT s'exécutent toujours intégralement même si leur résultat n'est
-- sélectionné par aucune autre partie de la requête (sémantique documentée de PostgreSQL pour
-- les data-modifying CTE), donc `evt`/`fan` produisent bien leurs effets malgré l'absence de
-- référence depuis le `SELECT` final.
CREATE OR REPLACE FUNCTION generer_alertes(p_preavis_jours integer DEFAULT 90)
    RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public
AS $$
DECLARE
    total integer := 0;
    n     integer;
BEGIN
    -- LOYER_EN_RETARD (EF-60) : un loyer marqué EN_RETARD par V7, sur un bail encore ACTIF
    -- (V25/US-118 : un bail CLOS n'émet plus jamais cette alerte).
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT p.bailleur_id, p.bailleur_id, 'LOYER_EN_RETARD', p.bien_id, p.bail_id, p.periode,
               'Loyer ' || p.periode || ' en retard de paiement.'
        FROM paiement p
        JOIN bail b ON b.id = p.bail_id
        WHERE p.statut = 'EN_RETARD'
          AND b.statut = 'ACTIF'
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING bailleur_id, bien_id, bail_id, periode),
    evt AS (
        INSERT INTO notification_event (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
        SELECT bailleur_id, 'LOYER_EN_RETARD', 'BAIL', bail_id,
               jsonb_build_object('bienId', bien_id, 'periode', periode)
        FROM ins
        RETURNING id, bailleur_id),
    fan AS (
        INSERT INTO notification_outbox (bailleur_id, event_id, recipient_id, notification_type, channel)
        SELECT e.bailleur_id, e.id, e.bailleur_id, 'LOYER_EN_RETARD', p.preferred_channel
        FROM evt e
        JOIN notification_preference p
          ON p.bailleur_id = e.bailleur_id AND p.recipient_type = 'BAILLEUR' AND p.recipient_id = e.bailleur_id
        WHERE p.enabled
          AND p.preferred_channel <> 'IN_APP'
          AND ((p.preferred_channel = 'WHATSAPP' AND p.whatsapp_opt_in)
               OR (p.preferred_channel = 'SMS' AND p.sms_opt_in))
        ON CONFLICT (event_id, recipient_id, notification_type, channel) DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    -- FIN_BAIL (EF-61) : bail ACTIF dont le terme est atteint sous 60 jours. periode = mois du terme.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT b.bailleur_id, b.bailleur_id, 'FIN_BAIL', b.bien_id, b.id,
               to_char(b.date_fin, 'YYYY-MM'),
               'Fin de bail prévue le ' || to_char(b.date_fin, 'YYYY-MM-DD') || '.'
        FROM bail b
        WHERE b.statut = 'ACTIF'
          AND b.date_fin IS NOT NULL
          AND b.date_fin <= current_date + 60
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING bailleur_id, bien_id, bail_id, periode),
    evt AS (
        INSERT INTO notification_event (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
        SELECT bailleur_id, 'FIN_BAIL', 'BAIL', bail_id,
               jsonb_build_object('bienId', bien_id, 'periode', periode)
        FROM ins
        RETURNING id, bailleur_id),
    fan AS (
        INSERT INTO notification_outbox (bailleur_id, event_id, recipient_id, notification_type, channel)
        SELECT e.bailleur_id, e.id, e.bailleur_id, 'FIN_BAIL', p.preferred_channel
        FROM evt e
        JOIN notification_preference p
          ON p.bailleur_id = e.bailleur_id AND p.recipient_type = 'BAILLEUR' AND p.recipient_id = e.bailleur_id
        WHERE p.enabled
          AND p.preferred_channel <> 'IN_APP'
          AND ((p.preferred_channel = 'WHATSAPP' AND p.whatsapp_opt_in)
               OR (p.preferred_channel = 'SMS' AND p.sms_opt_in))
        ON CONFLICT (event_id, recipient_id, notification_type, channel) DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    -- PREAVIS (EF-62) : bail ACTIF dont le terme entre dans la bande de préavis ]J+60 ; J+preavis].
    -- Borne basse > J+60 : exclusion mutuelle avec FIN_BAIL. periode = mois du terme.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT b.bailleur_id, b.bailleur_id, 'PREAVIS', b.bien_id, b.id,
               to_char(b.date_fin, 'YYYY-MM'),
               'Échéance de préavis : fin de bail prévue le ' || to_char(b.date_fin, 'YYYY-MM-DD') || '.'
        FROM bail b
        WHERE b.statut = 'ACTIF'
          AND b.date_fin IS NOT NULL
          AND b.date_fin > current_date + 60
          AND b.date_fin <= current_date + p_preavis_jours
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING bailleur_id, bien_id, bail_id, periode),
    evt AS (
        INSERT INTO notification_event (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
        SELECT bailleur_id, 'PREAVIS', 'BAIL', bail_id,
               jsonb_build_object('bienId', bien_id, 'periode', periode)
        FROM ins
        RETURNING id, bailleur_id),
    fan AS (
        INSERT INTO notification_outbox (bailleur_id, event_id, recipient_id, notification_type, channel)
        SELECT e.bailleur_id, e.id, e.bailleur_id, 'PREAVIS', p.preferred_channel
        FROM evt e
        JOIN notification_preference p
          ON p.bailleur_id = e.bailleur_id AND p.recipient_type = 'BAILLEUR' AND p.recipient_id = e.bailleur_id
        WHERE p.enabled
          AND p.preferred_channel <> 'IN_APP'
          AND ((p.preferred_channel = 'WHATSAPP' AND p.whatsapp_opt_in)
               OR (p.preferred_channel = 'SMS' AND p.sms_opt_in))
        ON CONFLICT (event_id, recipient_id, notification_type, channel) DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    -- GARANTIE_NON_RESTITUEE (EF-63) : garantie DETENU > 30 jours après la fin d'un bail CLOS.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT g.bailleur_id, g.bailleur_id, 'GARANTIE_NON_RESTITUEE', b.bien_id, g.bail_id,
               to_char(b.date_fin, 'YYYY-MM'),
               'Garantie non restituée plus de 30 jours après la fin du bail.'
        FROM garantie g
        JOIN bail b ON b.id = g.bail_id
        WHERE g.statut = 'DETENU'
          AND b.statut = 'CLOS'
          AND b.date_fin IS NOT NULL
          AND b.date_fin < current_date - 30
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING bailleur_id, bien_id, bail_id, periode),
    evt AS (
        INSERT INTO notification_event (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
        SELECT bailleur_id, 'GARANTIE_NON_RESTITUEE', 'BAIL', bail_id,
               jsonb_build_object('bienId', bien_id, 'periode', periode)
        FROM ins
        RETURNING id, bailleur_id),
    fan AS (
        INSERT INTO notification_outbox (bailleur_id, event_id, recipient_id, notification_type, channel)
        SELECT e.bailleur_id, e.id, e.bailleur_id, 'GARANTIE_NON_RESTITUEE', p.preferred_channel
        FROM evt e
        JOIN notification_preference p
          ON p.bailleur_id = e.bailleur_id AND p.recipient_type = 'BAILLEUR' AND p.recipient_id = e.bailleur_id
        WHERE p.enabled
          AND p.preferred_channel <> 'IN_APP'
          AND ((p.preferred_channel = 'WHATSAPP' AND p.whatsapp_opt_in)
               OR (p.preferred_channel = 'SMS' AND p.sms_opt_in))
        ON CONFLICT (event_id, recipient_id, notification_type, channel) DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    RETURN total;
END;
$$;

COMMENT ON FUNCTION generer_alertes(integer) IS
    'US-50/51/EF-60/61/62/63/65/A.7 : génère (idempotent) les alertes LOYER_EN_RETARD, FIN_BAIL, PREAVIS (bande ]J+60 ; J+p_preavis_jours]) et GARANTIE_NON_RESTITUEE pour tous les bailleurs. SECURITY DEFINER (owner BYPASSRLS loyertracker_batch). Retourne le nombre d''alertes créées (notification_event/outbox exclus du compteur, V27/EP-16 Sprint N, voie A). V25/EP-13/US-118 : LOYER_EN_RETARD désormais restreinte aux baux ACTIF.';

ALTER FUNCTION generer_alertes(integer) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION generer_alertes(integer) TO loyertracker_api;
