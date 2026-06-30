-- =====================================================================================
-- LoyerTracker — Migration V18 : Sprint 5 Lot B4 — StatutPaiement A_VENIR (US-60)
--
-- 1. Réécriture de `generer_echeances_loyers()` : les échéances dont la date
--    d'exigibilité est dans le futur sont créées avec le statut 'A_VENIR' au lieu de
--    'IMPAYE'. Cela rend le statut technique cohérent avec la réalité métier (le loyer
--    n'est pas encore exigible).
--
-- 2. Rétroactif : les paiements IMPAYE existants dont la date d'exigibilité est dans
--    le futur passent à 'A_VENIR' (cohérence des données pré-existantes).
--
-- La fonction reste SECURITY DEFINER OWNER loyertracker_batch (ADR-01, V6).
-- =====================================================================================

CREATE OR REPLACE FUNCTION generer_echeances_loyers()
    RETURNS integer
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
AS $$
    WITH inserees AS (
        INSERT INTO paiement (
            bailleur_id, bail_id, bien_id, periode,
            montant_attendu, montant_recu, date_exigibilite, statut)
        SELECT
            b.bailleur_id,
            b.id,
            b.bien_id,
            to_char(gs.m, 'YYYY-MM'),
            b.loyer_cc,
            0,
            (gs.m + INTERVAL '1 month')::date,
            CASE
                WHEN (gs.m + INTERVAL '1 month')::date > CURRENT_DATE THEN 'A_VENIR'
                ELSE 'IMPAYE'
            END
        FROM bail b
        CROSS JOIN LATERAL generate_series(
            date_trunc('month', b.date_debut),
            date_trunc('month', COALESCE(b.date_fin, CURRENT_DATE)),
            INTERVAL '1 month') AS gs(m)
        WHERE b.statut = 'ACTIF'
        ON CONFLICT (bien_id, periode) DO NOTHING
        RETURNING 1)
    SELECT COALESCE(count(*), 0)::integer FROM inserees;
$$;

COMMENT ON FUNCTION generer_echeances_loyers() IS
    'US-30/EF-33/A.3/US-60 : génère les loyers attendus à terme échu pour tous les baux ACTIFS (multi-bailleur). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch), idempotent via uq_paiement_periode. Statut A_VENIR pour les échéances dont la date d''exigibilité est dans le futur. Retourne le nombre d''échéances créées.';

ALTER FUNCTION generer_echeances_loyers() OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION generer_echeances_loyers() TO loyertracker_api;

-- B4 rétroactif : IMPAYE futur → A_VENIR
UPDATE paiement
SET statut = 'A_VENIR'
WHERE statut = 'IMPAYE'
  AND date_exigibilite > CURRENT_DATE;
