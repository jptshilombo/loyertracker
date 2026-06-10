-- =====================================================================================
-- LoyerTracker — Migration V10 : alerte PREAVIS (US-50, EF-62)
-- Sprint S04 / Phase 07 — complète generer_alertes() (V9) avec le 4ᵉ type d'alerte.
--
-- CRITÈRE D'ACCEPTATION (arbitrage PO) : une échéance de préavis est « atteinte » lorsque le terme
-- du bail entre dans la bande de préavis, soit AVANT le terme et à moins de `p_preavis_jours` (90 j
-- par défaut, ADR PO). Pour éviter le double signalement avec FIN_BAIL (qui couvre les ≤ 60 jours),
-- la bande PREAVIS est BORNÉE à l'intervalle ]J+60 ; J+preavis] : un bail dont le terme est sous
-- 60 jours relève déjà de FIN_BAIL (EF-61), au-delà de la bande il ne déclenche encore rien.
--   * PREAVIS : bail ACTIF, terme NON NULL, current_date + 60 < date_fin <= current_date + preavis.
--     `periode` = mois du terme (to_char(date_fin,'YYYY-MM')) — non-nulle pour l'anti-doublon.
--
-- La fonction est RECRÉÉE avec un paramètre `p_preavis_jours` (défaut 90, surchargeable par config
-- `app.alertes.preavis.jours`). Sa signature changeant, on DROP d'abord l'ancienne `generer_alertes()`
-- sans argument (V9) ; l'appel sans argument reste valide via le DEFAULT. Mêmes patrons que V9 :
-- SECURITY DEFINER propriété de `loyertracker_batch` (BYPASSRLS), exécutée par `loyertracker_api`,
-- anti-doublon `ON CONFLICT (type, bien_id, periode) WHERE statut='NON_LUE' DO NOTHING` (EF-65).
-- =====================================================================================

DROP FUNCTION generer_alertes();

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

COMMENT ON FUNCTION generer_alertes(integer) IS
    'US-50/51/EF-60/61/62/63/65/A.7 : génère (idempotent) les alertes LOYER_EN_RETARD, FIN_BAIL, PREAVIS (bande ]J+60 ; J+p_preavis_jours]) et GARANTIE_NON_RESTITUEE pour tous les bailleurs. SECURITY DEFINER (owner BYPASSRLS loyertracker_batch). Retourne le nombre d''alertes créées.';

ALTER FUNCTION generer_alertes(integer) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION generer_alertes(integer) TO loyertracker_api;
