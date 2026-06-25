-- =====================================================================================
-- LoyerTracker — Migration V15 : exceptions fines par bien (Sprint 3 Patrimoine, US-85)
--
-- La V13 a introduit l'affectation patrimoine (héritage dynamique vers tous les biens du
-- portefeuille). Cette migration ajoute la résolution à PRIORITÉ entre les deux niveaux :
--   * une affectation BIEN active pour (gestionnaire, bien) court-circuite toute résolution
--     patrimoine : son type_exception (INCLUSION|EXCLUSION) fait foi ;
--   * sinon, une affectation PATRIMOINE active héritée donne accès (comportement V13) ;
--   * sinon, refus (comportement actuel inchangé).
--
-- RS-04 (validé PO 2026-06-21) : une EXCLUSION sans affectation patrimoine active correspondante
-- est un état incohérent — rejetée en validation applicative (AffectationService), pas en base.
-- =====================================================================================

ALTER TABLE affectation
    ADD COLUMN type_exception varchar(10);

ALTER TABLE affectation
    ADD CONSTRAINT affectation_type_exception_valide
    CHECK (type_exception IN ('INCLUSION', 'EXCLUSION'));

ALTER TABLE affectation
    ADD CONSTRAINT affectation_exception_requiert_bien
    CHECK (type_exception IS NULL OR bien_id IS NOT NULL);

COMMENT ON COLUMN affectation.type_exception IS
    'Sprint 3 Patrimoine (US-85) : portée d''une affectation BIEN face à un héritage patrimoine. '
    'INCLUSION (défaut applicatif) ou EXCLUSION (RS-04 : exige une affectation patrimoine ACTIVE '
    'du même gestionnaire). NULL pour toute affectation patrimoine (CHECK ci-dessus).';

-- Non-régression US-23/24 : toute affectation bien déjà existante (active ou historique) se
-- comportait comme une inclusion implicite — backfill explicite, sans changement de comportement.
UPDATE affectation
   SET type_exception = 'INCLUSION'
 WHERE bien_id IS NOT NULL;

-- Étend le prédicat ReBAC bien : résolution à PRIORITÉ (bien court-circuite patrimoine).
CREATE OR REPLACE FUNCTION gestionnaire_affecte_actif(p_bien uuid, p_gestionnaire uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT COALESCE(
        -- Niveau 1 (prioritaire) : affectation bien ACTIVE pour ce gestionnaire sur ce bien.
        (SELECT COALESCE(a.type_exception, 'INCLUSION') = 'INCLUSION'
         FROM affectation a
         WHERE a.statut = 'ACTIVE'
           AND a.gestionnaire_id = p_gestionnaire
           AND a.bien_id = p_bien),
        -- Niveau 2 (repli) : affectation patrimoine ACTIVE héritée — uniquement si le niveau 1
        -- est totalement absent (NULL ci-dessus), jamais s'il existe (même en EXCLUSION).
        EXISTS (
            SELECT 1
            FROM bien b
            JOIN affectation a ON a.statut = 'ACTIVE'
                AND a.gestionnaire_id = p_gestionnaire
                AND a.patrimoine_id = b.patrimoine_id
            WHERE b.id = p_bien
        )
    )
$$;

COMMENT ON FUNCTION gestionnaire_affecte_actif(uuid, uuid) IS
    'Sprint 3/ADR-02/RM-98 : résolution à priorité — affectation bien ACTIVE (INCLUSION/EXCLUSION) '
    'court-circuite l''héritage patrimoine ; sinon repli sur l''affectation patrimoine héritée.';

-- Symétrique pour la liste des biens visibles par un gestionnaire (mêmes règles de priorité).
DROP FUNCTION biens_affectes_gestionnaire(text);

CREATE FUNCTION biens_affectes_gestionnaire(p_keycloak_id text)
    RETURNS TABLE(id uuid, adresse varchar, type varchar, statut varchar, patrimoine_id uuid)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT DISTINCT b.id, b.adresse, b.type, b.statut, b.patrimoine_id
    FROM bien b
    JOIN gestionnaire g ON g.keycloak_id = p_keycloak_id
    WHERE
        -- Inclusion directe sur le bien : prioritaire, indépendante d'un patrimoine.
        EXISTS (
            SELECT 1 FROM affectation a
            WHERE a.statut = 'ACTIVE' AND a.gestionnaire_id = g.id AND a.bien_id = b.id
              AND COALESCE(a.type_exception, 'INCLUSION') = 'INCLUSION'
        )
        OR (
            -- Repli patrimoine : uniquement si aucune affectation bien ACTIVE n'existe du tout
            -- pour ce couple (gestionnaire, bien) — une EXCLUSION bloque donc ce repli.
            NOT EXISTS (
                SELECT 1 FROM affectation a
                WHERE a.statut = 'ACTIVE' AND a.gestionnaire_id = g.id AND a.bien_id = b.id
            )
            AND b.patrimoine_id IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM affectation a
                WHERE a.statut = 'ACTIVE' AND a.gestionnaire_id = g.id
                  AND a.patrimoine_id = b.patrimoine_id
            )
        )
    ORDER BY b.adresse
$$;

COMMENT ON FUNCTION biens_affectes_gestionnaire(text) IS
    'Sprint 3/ADR-02/RM-98 : biens affectés actifs à un gestionnaire avec résolution à priorité '
    '(inclusion bien directe, ou héritage patrimoine non bloqué par une exclusion bien).';

ALTER FUNCTION gestionnaire_affecte_actif(uuid, uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION biens_affectes_gestionnaire(text) OWNER TO loyertracker_batch;

-- Correctif ciblé S04 (calculer_honoraires, V8/V14) : une affectation bien EXCLUSION est un
-- carve-out d'accès, pas un mandat de gestion — elle ne doit jamais générer d'honoraire. De même,
-- un bien couvert par une EXCLUSION indépendante reste hors du calcul de l'affectation patrimoine
-- du même gestionnaire (cohérence avec la résolution à priorité ci-dessus). Aucun autre changement
-- de comportement par rapport à V14.
CREATE OR REPLACE FUNCTION calculer_honoraires(p_bien_id uuid DEFAULT NULL)
    RETURNS integer
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
AS $$
    WITH eligible AS (
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
            AND NOT EXISTS (
                SELECT 1 FROM affectation excl
                WHERE excl.statut = 'ACTIVE'
                  AND excl.gestionnaire_id = a.gestionnaire_id
                  AND excl.bien_id = b.id
                  AND excl.type_exception = 'EXCLUSION'
            )
        WHERE a.statut = 'ACTIVE'
          AND COALESCE(a.type_exception, 'INCLUSION') <> 'EXCLUSION'
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
    'US-40/EF-50/51/52/A.6 + S02/Sprint 3 : calcule (upsert idempotent) les honoraires de gestion '
    'par affectation ACTIVE et periode, POURCENTAGE sur loyer encaisse ou FORFAIT, gel a PAYE. '
    'Supporte affectations bien et patrimoine ; exclut les affectations/biens couverts par une '
    'EXCLUSION (US-85, carve-out d''acces, jamais facturable). p_bien_id NULL = tous les biens '
    '(batch), sinon cible (hook pointage). SECURITY DEFINER (owner BYPASSRLS loyertracker_batch).';

ALTER FUNCTION calculer_honoraires(uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION calculer_honoraires(uuid) TO loyertracker_api;
