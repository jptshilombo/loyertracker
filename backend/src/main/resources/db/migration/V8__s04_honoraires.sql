-- =====================================================================================
-- LoyerTracker — Migration V8 : calcul des honoraires de gestion
-- Sprint S04 / Phase 07 — US-40, EF-50/51/52, Expression du besoin Annexe A.6.
--
-- Un honoraire est dû par AFFECTATION et par PÉRIODE (mois civil), aligné sur les loyers du bien
-- géré. Le montant dépend du barème porté par l'affectation :
--   * POURCENTAGE : round(montant_honoraires / 100 * loyer ENCAISSÉ, 2) — recalculé à chaque
--     encaissement TANT QUE l'honoraire n'est pas figé (EF-51) ;
--   * FORFAIT     : montant_honoraires (fixe).
-- L'honoraire n'est calculé que pour les périodes couvertes par la fenêtre de l'affectation
-- ([date_debut, date_fin]) et tant que l'affectation est ACTIVE.
--
-- GEL À PAYE (EF-52) : une fois l'honoraire validé (statut PAYE) par le bailleur, le montant n'est
-- plus recalculé — le `WHERE honoraire.statut <> 'PAYE'` du DO UPDATE le garantit en base.
--
-- IDEMPOTENCE : `ON CONFLICT (affectation_id, periode) DO UPDATE` s'appuie sur l'index unique métier
-- `uq_honoraire_periode` (V1, EF-51). Le `bailleur_id` est hérité de l'affectation : aucune fuite
-- inter-tenant possible.
--
-- Job MULTI-BAILLEUR (ADR-01), même patron que V6/V7 : fonction SECURITY DEFINER PROPRIÉTÉ du rôle
-- `loyertracker_batch` (BYPASSRLS), appelée par le rôle applicatif `loyertracker_api` (NOBYPASSRLS).
-- Le paramètre `p_bien_id` permet un recalcul CIBLÉ (synchrone au pointage d'un loyer) ; NULL =
-- recalcul COMPLET (batch). Le `bailleur_id` étant dérivé de la donnée, le ciblage ne crée aucune
-- fuite même si la fonction contourne la RLS.
-- =====================================================================================

CREATE OR REPLACE FUNCTION calculer_honoraires(p_bien_id uuid DEFAULT NULL)
    RETURNS integer
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
AS $$
    WITH calcul AS (
        SELECT
            a.bailleur_id,
            a.id AS affectation_id,
            p.periode,
            CASE a.type_honoraires
                WHEN 'POURCENTAGE' THEN round(a.montant_honoraires / 100 * p.montant_recu, 2)
                ELSE a.montant_honoraires
            END AS montant
        FROM affectation a
        JOIN paiement p
            ON p.bien_id = a.bien_id
           AND p.periode >= to_char(a.date_debut, 'YYYY-MM')
           AND (a.date_fin IS NULL OR p.periode <= to_char(a.date_fin, 'YYYY-MM'))
        WHERE a.statut = 'ACTIVE'
          AND (p_bien_id IS NULL OR a.bien_id = p_bien_id)
    ),
    upsert AS (
        INSERT INTO honoraire (bailleur_id, affectation_id, periode, montant, statut)
        SELECT bailleur_id, affectation_id, periode, montant, 'DU'
        FROM calcul
        ON CONFLICT (affectation_id, periode) DO UPDATE
            SET montant = EXCLUDED.montant
            WHERE honoraire.statut <> 'PAYE'
              AND honoraire.montant IS DISTINCT FROM EXCLUDED.montant
        RETURNING 1)
    SELECT COALESCE(count(*), 0)::integer FROM upsert;
$$;

COMMENT ON FUNCTION calculer_honoraires(uuid) IS
    'US-40/EF-50/51/52/A.6 : calcule (upsert idempotent) les honoraires de gestion par affectation ACTIVE et période, POURCENTAGE sur loyer encaissé ou FORFAIT, gel à PAYE. p_bien_id NULL = tous les biens (batch), sinon ciblé (hook pointage). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch). Retourne le nombre de lignes créées/mises à jour.';

ALTER FUNCTION calculer_honoraires(uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION calculer_honoraires(uuid) TO loyertracker_api;
