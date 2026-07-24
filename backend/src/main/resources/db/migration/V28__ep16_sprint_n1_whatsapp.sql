-- =====================================================================================
-- LoyerTracker — Migration V28 : EP-16 Sprint N+1 (WhatsApp P0) — Notifications
-- multicanales via Twilio, ADR-18/D-NOTIF-001. GO explicite du PO reçu le 2026-07-24,
-- distinct de celui du Sprint N (release `1.13.0`, clôturée le même jour).
--
-- Migration strictement additive : seed de référentiel (notification_template) + une
-- fonction SECURITY DEFINER de découverte cross-tenant en lecture seule. Aucune table
-- modifiée, aucune colonne ajoutée — rollback applicatif trivial.
--
-- Reste hors périmètre : tout appel réseau Twilio réel exécuté par cette migration
-- elle-même (aucun secret n'est disponible en migration SQL) ; l'envoi réel est piloté
-- par le code applicatif (TwilioNotificationProvider, NotificationDispatcher), lui-même
-- inactif tant que TWILIO_WHATSAPP_ENABLED=false (K8, valeur par défaut inchangée).
-- =====================================================================================

-- --- 1. Templates P0 (US-122) -----------------------------------------------------------
-- Soumis/approuvés au sens applicatif uniquement (le mécanisme de statut, pas une réelle
-- soumission à l'approbation Twilio, hors périmètre de cette mission — plan-execution-
-- ep16-notifications.md §Sprint N+1). Approuvés dès la seed pour permettre la vérification
-- manuelle en Sandbox exigée par le critère GO du sprint ; un template non approuvé (test
-- dédié) reste un état atteignable en base sans passer par cette seed.
INSERT INTO notification_template (code, channel, language, version, approval_status, enabled)
VALUES
    ('QUITTANCE_DISPONIBLE', 'WHATSAPP', 'fr', 1, 'APPROUVE', true),
    ('LOYER_EN_RETARD',      'WHATSAPP', 'fr', 1, 'APPROUVE', true),
    ('GARANTIE_DEBITEE',     'WHATSAPP', 'fr', 1, 'APPROUVE', true);

-- --- 2. Découverte cross-tenant des bailleurs ayant de l'Outbox dû (US-123) -------------
-- Lecture seule, strictement bornée à un SELECT DISTINCT bailleur_id : NotificationDispatcher
-- (Java, rôle loyertracker_api, RLS FORCE) ne peut pas lister les bailleurs concernés sans
-- connaître déjà leur identifiant (aucun contexte GUC multi-tenant côté application) — même
-- besoin que generer_alertes()/calculer_honoraires(), résolu par le même patron : une
-- fonction SECURITY DEFINER dédiée, jamais un contournement RLS générique. Le traitement
-- détaillé de chaque ligne (accès à notification_preference/notification_event) reste
-- ensuite exécuté par bailleur, avec le contexte GUC positionné (TenantContext), RLS
-- pleinement appliquée — cette fonction ne fait que révéler la liste des tenants à traiter.
CREATE FUNCTION notification_bailleurs_en_attente()
    RETURNS TABLE (bailleur_id uuid)
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
    STABLE
AS $$
    SELECT DISTINCT o.bailleur_id
    FROM notification_outbox o
    WHERE o.statut IN ('PENDING', 'RETRY') AND o.next_attempt_at <= now();
$$;

COMMENT ON FUNCTION notification_bailleurs_en_attente() IS
    'US-123, EP-16 Sprint N+1 : liste (lecture seule) les bailleurs ayant au moins une ligne notification_outbox PENDING/RETRY due. SECURITY DEFINER (owner BYPASSRLS loyertracker_batch) — le traitement détaillé par bailleur reste ensuite RLS-scopé (TenantContext positionné avant tout accès notification_preference/notification_event).';

ALTER FUNCTION notification_bailleurs_en_attente() OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION notification_bailleurs_en_attente() TO loyertracker_api;

-- --- 3. Application idempotente d'un callback de statut Twilio (US-123) -----------------
-- Le callback Twilio (TwilioCallbackController) est un endpoint PUBLIC non authentifié : aucun
-- contexte tenant (GUC app.current_bailleur_id) n'est disponible pour satisfaire la RLS FORCE de
-- notification_delivery. Même patron que lire_quittance_publique/journaliser_evenement_quittance
-- (V22) : SECURITY DEFINER, jamais un contournement RLS générique côté application. La logique de
-- progression (idempotence — un callback dupliqué ou hors ordre n'entraîne aucune transition
-- supplémentaire ; READ/FAILED/UNDELIVERED/CANCELLED sont terminaux) est appliquée atomiquement
-- (SELECT ... FOR UPDATE puis UPDATE conditionnel dans la même transaction fonction).
CREATE FUNCTION notification_delivery_appliquer_statut(
        p_provider_message_id varchar, p_nouveau_statut varchar,
        p_error_code varchar, p_error_category varchar)
    RETURNS boolean
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public
AS $$
DECLARE
    statut_actuel varchar;
    rang_actuel   integer;
    rang_nouveau  integer;
BEGIN
    SELECT statut INTO statut_actuel
    FROM notification_delivery
    WHERE provider_message_id = p_provider_message_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN false;
    END IF;

    IF statut_actuel IN ('READ', 'FAILED', 'UNDELIVERED', 'CANCELLED') THEN
        RETURN false; -- terminal : aucune transition supplémentaire (callback tardif/dupliqué)
    END IF;

    rang_actuel := CASE statut_actuel
        WHEN 'QUEUED' THEN 0 WHEN 'ACCEPTED' THEN 1 WHEN 'SENT' THEN 2
        WHEN 'DELIVERED' THEN 3 WHEN 'READ' THEN 4 ELSE -1 END;
    rang_nouveau := CASE p_nouveau_statut
        WHEN 'QUEUED' THEN 0 WHEN 'ACCEPTED' THEN 1 WHEN 'SENT' THEN 2
        WHEN 'DELIVERED' THEN 3 WHEN 'READ' THEN 4 ELSE -1 END;

    -- rang_nouveau = -1 (FAILED/UNDELIVERED/CANCELLED) : toujours une progression valide tant que
    -- l'état courant n'est pas déjà terminal (déjà exclu ci-dessus). Sinon, uniquement en avant.
    IF rang_nouveau <> -1 AND rang_nouveau <= rang_actuel THEN
        RETURN false; -- callback dupliqué ou hors ordre (K7)
    END IF;

    UPDATE notification_delivery
    SET statut = p_nouveau_statut,
        sent_at        = CASE WHEN p_nouveau_statut = 'SENT' THEN now() ELSE sent_at END,
        delivered_at   = CASE WHEN p_nouveau_statut = 'DELIVERED' THEN now() ELSE delivered_at END,
        read_at        = CASE WHEN p_nouveau_statut = 'READ' THEN now() ELSE read_at END,
        failed_at      = CASE WHEN p_nouveau_statut IN ('FAILED', 'UNDELIVERED') THEN now() ELSE failed_at END,
        error_code     = CASE WHEN p_nouveau_statut IN ('FAILED', 'UNDELIVERED') THEN p_error_code ELSE error_code END,
        error_category = CASE WHEN p_nouveau_statut IN ('FAILED', 'UNDELIVERED') THEN p_error_category ELSE error_category END
    WHERE provider_message_id = p_provider_message_id;

    RETURN true;
END;
$$;

COMMENT ON FUNCTION notification_delivery_appliquer_statut(varchar, varchar, varchar, varchar) IS
    'US-123, EP-16 Sprint N+1 : applique un callback de statut Twilio de façon idempotente (progression uniquement, jamais en arrière ; terminal = READ/FAILED/UNDELIVERED/CANCELLED). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch) : le callback public (TwilioCallbackController) n''a aucun contexte tenant, même patron que lire_quittance_publique/journaliser_evenement_quittance (V22). Retourne true si une transition a été appliquée.';

ALTER FUNCTION notification_delivery_appliquer_statut(varchar, varchar, varchar, varchar) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION notification_delivery_appliquer_statut(varchar, varchar, varchar, varchar) TO loyertracker_api;
