-- =====================================================================================
-- LoyerTracker — Migration V14 : honoraires sur affectations patrimoine (Sprint S02)
--
-- La V13 a introduit les affectations au niveau patrimoine (bien_id NULL, patrimoine_id
-- renseigné). La fonction calculer_honoraires(V8) ne calculait que les honoraires des
-- affectations directes bien (JOIN paiement.bien_id = affectation.bien_id).
--
-- Cette migration étend la fonction pour :
--   1. résoudre les biens effectifs d'une affectation patrimoine via la table bien ;
--   2. agréger les paiements par période pour une affectation patrimoine ;
--   3. conserver le comportement existant pour les affectations bien.
-- =====================================================================================

CREATE OR REPLACE FUNCTION calculer_honoraires(p_bien_id uuid DEFAULT NULL)
    RETURNS integer
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
AS $$
    WITH eligible AS (
        -- Tous les couples (affectation, bien_effectif) couverts par l'affectation
        SELECT
            a.id AS affectation_id,
            a.bailleur_id,
            a.type_honoraires,
            a.montant_honoraires,
            a.date_debut,
            a.date_fin,
            COALESCE(a.bien_id, b.id) AS bien_effectif_id
        FROM affectation a
        LEFT JOIN bien b ON a.patrimoine_id IS NOT NULL AND a.patrimoine_id = b.patrimoine_id
        WHERE a.statut = 'ACTIVE'
          AND (p_bien_id IS NULL OR COALESCE(a.bien_id, b.id) = p_bien_id)
    ),
    calcul AS (
        SELECT
            e.bailleur_id,
            e.affectation_id,
            p.periode,
            CASE e.type_honoraires
                WHEN 'POURCENTAGE' THEN round(e.montant_honoraires / 100 * SUM(p.montant_recu), 2)
                ELSE e.montant_honoraires
            END AS montant
        FROM eligible e
        JOIN paiement p
            ON p.bien_id = e.bien_effectif_id
           AND p.periode >= to_char(e.date_debut, 'YYYY-MM')
           AND (e.date_fin IS NULL OR p.periode <= to_char(e.date_fin, 'YYYY-MM'))
        GROUP BY e.bailleur_id, e.affectation_id, p.periode, e.type_honoraires, e.montant_honoraires
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
    'US-40/EF-50/51/52/A.6 + S02 : calcule (upsert idempotent) les honoraires de gestion par affectation ACTIVE et periode, POURCENTAGE sur loyer encaisse ou FORFAIT, gel a PAYE. Supporte affectations bien et patrimoine. p_bien_id NULL = tous les biens (batch), sinon cible (hook pointage). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch).';

ALTER FUNCTION calculer_honoraires(uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION calculer_honoraires(uuid) TO loyertracker_api;
