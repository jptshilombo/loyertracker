-- =====================================================================================
-- LoyerTracker — Migration V9 : génération des alertes de pilotage & accès gestionnaire
-- Sprint S04 / Phase 07 — US-50/51/52, EF-60/61/63/64/65, Expression du besoin Annexe A.7.
--
-- Trois types d'alertes sont générés (le type PREAVIS est REPORTÉ — critère d'acceptation ambigu,
-- arbitrage PO « B ») :
--   * LOYER_EN_RETARD        : un loyer passé en EN_RETARD (V7) non encaissé. `periode` = celle du loyer.
--   * FIN_BAIL               : un bail ACTIF dont le terme est atteint sous 60 jours.
--   * GARANTIE_NON_RESTITUEE : une garantie encore DETENU plus de 30 jours après la fin d'un bail CLOS.
--
-- ANTI-DOUBLON (EF-65) : `uq_alerte_nonlue (type, bien_id, periode) WHERE statut='NON_LUE'` (V1).
-- Comme NULL ≠ NULL en SQL, une `periode` NULL ne dédoublonnerait pas : on DÉRIVE donc une période
-- non-nulle pour FIN_BAIL et GARANTIE (mois du terme du bail). Toutes les alertes portent ainsi un
-- (type, bien_id, periode) complet et l'insertion répétée est idempotente via ON CONFLICT DO NOTHING.
--
-- Job MULTI-BAILLEUR (ADR-01), même patron que V6/V7/V8 : SECURITY DEFINER propriété de
-- `loyertracker_batch` (BYPASSRLS), exécutée par `loyertracker_api` (NOBYPASSRLS). Le `bailleur_id`
-- de chaque alerte est hérité de la donnée source : aucune fuite inter-tenant.
--
-- Les fonctions `alertes_gestionnaire` / `alerte_bailleur_pour_gestionnaire` donnent au gestionnaire
-- un accès en lecture borné à ses affectations ACTIVES (US-52) — celui-ci n'a pas de tenant propre,
-- d'où le recours au patron SECURITY DEFINER (comme les prédicats ReBAC de V3).
-- =====================================================================================

CREATE OR REPLACE FUNCTION generer_alertes()
    RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public
AS $$
DECLARE
    total integer := 0;
    n     integer;
BEGIN
    -- LOYER_EN_RETARD (EF-60) : un loyer marqué EN_RETARD par V7.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT p.bailleur_id, p.bailleur_id, 'LOYER_EN_RETARD', p.bien_id, p.bail_id, p.periode,
               'Loyer ' || p.periode || ' en retard de paiement.'
        FROM paiement p
        WHERE p.statut = 'EN_RETARD'
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
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
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    RETURN total;
END;
$$;

COMMENT ON FUNCTION generer_alertes() IS
    'US-50/51/EF-60/61/63/65/A.7 : génère (idempotent) les alertes LOYER_EN_RETARD, FIN_BAIL et GARANTIE_NON_RESTITUEE pour tous les bailleurs (PREAVIS reporté). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch). Retourne le nombre d''alertes créées.';

ALTER FUNCTION generer_alertes() OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION generer_alertes() TO loyertracker_api;

-- Lecture des alertes d'un gestionnaire, bornée à ses affectations ACTIVES (US-52, EF-64).
CREATE OR REPLACE FUNCTION alertes_gestionnaire(p_gestionnaire_id uuid)
    RETURNS SETOF alerte
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT al.*
    FROM alerte al
    JOIN affectation a ON a.bien_id = al.bien_id AND a.statut = 'ACTIVE'
    WHERE a.gestionnaire_id = p_gestionnaire_id;
$$;

COMMENT ON FUNCTION alertes_gestionnaire(uuid) IS
    'US-52/EF-64 : alertes des biens dont le gestionnaire a une affectation ACTIVE. SECURITY DEFINER (le gestionnaire n''a pas de tenant propre).';

ALTER FUNCTION alertes_gestionnaire(uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION alertes_gestionnaire(uuid) TO loyertracker_api;

-- Vérifie qu'une alerte est dans le périmètre actif d'un gestionnaire ; renvoie son bailleur_id (ou NULL).
CREATE OR REPLACE FUNCTION alerte_bailleur_pour_gestionnaire(p_alerte_id uuid, p_gestionnaire_id uuid)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT al.bailleur_id
    FROM alerte al
    JOIN affectation a ON a.bien_id = al.bien_id AND a.statut = 'ACTIVE'
    WHERE al.id = p_alerte_id AND a.gestionnaire_id = p_gestionnaire_id;
$$;

COMMENT ON FUNCTION alerte_bailleur_pour_gestionnaire(uuid, uuid) IS
    'US-52/EF-64 : bailleur_id d''une alerte SI le gestionnaire y a accès (affectation ACTIVE), sinon NULL. SECURITY DEFINER ; fail-closed pour le marquage « lue ».';

ALTER FUNCTION alerte_bailleur_pour_gestionnaire(uuid, uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION alerte_bailleur_pour_gestionnaire(uuid, uuid) TO loyertracker_api;
