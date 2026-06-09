-- =====================================================================================
-- LoyerTracker — Migration V6 : génération des échéances de loyers à terme échu
-- Sprint S03 / Phase 07 — US-30, EF-33, Expression du besoin Annexe A.3.
--
-- Paiement à TERME ÉCHU (A.3) : une échéance porte sur un MOIS CONSOMMÉ (`periode`), depuis le
-- mois de DÉBUT du bail jusqu'au mois du terme (`date_fin`, ou le mois courant si bail en cours),
-- SANS prorata. Le montant attendu est le `loyer_cc`. La période `m` devient exigible le 1er du
-- mois `m+1` (`date_exigibilite`).
--
-- Job MULTI-BAILLEUR (ADR-01) : génère pour tous les tenants. On réutilise le patron établi des
-- résolveurs/prédicats (V2–V4) — fonction SECURITY DEFINER PROPRIÉTÉ du rôle `loyertracker_batch`
-- (BYPASSRLS) : appelée par le rôle applicatif `loyertracker_api` (NOBYPASSRLS), elle s'exécute
-- avec l'identité du batch et franchit la RLS de façon étroite et auditable. Aucun second
-- datasource ni rôle LOGIN dédié n'est donc nécessaire.
--
-- IDEMPOTENCE : `ON CONFLICT (bien_id, periode) DO NOTHING` s'appuie sur l'index unique métier
-- `uq_paiement_periode` (V1, EF-30/33). Un 2ᵉ passage n'insère aucun doublon. Le `bailleur_id` et
-- le `bien_id` de chaque paiement sont hérités du bail : aucune fuite inter-tenant possible.
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
            'IMPAYE'
        FROM bail b
        CROSS JOIN LATERAL generate_series(
            date_trunc('month', b.date_debut),
            date_trunc('month', COALESCE(b.date_fin, current_date)),
            INTERVAL '1 month') AS gs(m)
        WHERE b.statut = 'ACTIF'
        ON CONFLICT (bien_id, periode) DO NOTHING
        RETURNING 1)
    SELECT COALESCE(count(*), 0)::integer FROM inserees;
$$;

COMMENT ON FUNCTION generer_echeances_loyers() IS
    'US-30/EF-33/A.3 : génère les loyers attendus à terme échu pour tous les baux ACTIFS (multi-bailleur). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch), idempotent via uq_paiement_periode. Retourne le nombre d''échéances créées.';

ALTER FUNCTION generer_echeances_loyers() OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION generer_echeances_loyers() TO loyertracker_api;
