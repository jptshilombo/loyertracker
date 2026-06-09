-- =====================================================================================
-- LoyerTracker — Migration V7 : passage automatique des loyers échus impayés en EN_RETARD
-- Sprint S03 / Phase 07 — US-31, EF-31, Expression du besoin Annexe A.3/A.4.
--
-- Règle (volontairement ÉTROITE) : une échéance encore `IMPAYE` dont la `date_exigibilite` est
-- DÉPASSÉE bascule en `EN_RETARD`. On ne touche JAMAIS `PARTIEL`/`RECU` (états décidés par un
-- acteur via pointage) ni ne fait de retour arrière : l'opération est donc sûre et idempotente.
--
-- Job MULTI-BAILLEUR (ADR-01), même patron que `generer_echeances_loyers()` (V6) : fonction
-- SECURITY DEFINER PROPRIÉTÉ du rôle `loyertracker_batch` (BYPASSRLS), appelée par le rôle
-- applicatif `loyertracker_api` (NOBYPASSRLS). Aucun second datasource ni rôle LOGIN dédié.
-- =====================================================================================

CREATE OR REPLACE FUNCTION marquer_loyers_en_retard()
    RETURNS integer
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
AS $$
    WITH maj AS (
        UPDATE paiement
        SET statut = 'EN_RETARD'
        WHERE statut = 'IMPAYE'
          AND date_exigibilite < current_date
        RETURNING 1)
    SELECT COALESCE(count(*), 0)::integer FROM maj;
$$;

COMMENT ON FUNCTION marquer_loyers_en_retard() IS
    'US-31/EF-31/A.3 : bascule en EN_RETARD les loyers IMPAYE dont l''exigibilité est dépassée (multi-bailleur). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch), idempotent. Retourne le nombre de loyers basculés.';

ALTER FUNCTION marquer_loyers_en_retard() OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION marquer_loyers_en_retard() TO loyertracker_api;
