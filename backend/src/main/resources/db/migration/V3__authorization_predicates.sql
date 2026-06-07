-- =====================================================================================
-- LoyerTracker — Migration V3 : prédicats d'autorisation fine (US-13, ADR-01/ADR-02)
-- Sprint S01 / Phase 07. Fonctions booléennes évaluées au temps @PreAuthorize (avant la
-- transaction de service, donc SANS contexte tenant positionné) : elles doivent contourner la
-- RLS de façon étroite, exactement comme les résolveurs de V2 (ADR-09). SECURITY DEFINER,
-- LECTURE SEULE, propriété du rôle BYPASSRLS `loyertracker_batch`, search_path figé.
--   * bien_appartient_au_bailleur(bien, bailleur) : le bailleur possède-t-il ce bien ?
--   * gestionnaire_affecte_actif(bien, gestionnaire) : affectation ACTIVE (bien, gestionnaire) ?
-- =====================================================================================

CREATE OR REPLACE FUNCTION bien_appartient_au_bailleur(p_bien uuid, p_bailleur uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT EXISTS (SELECT 1 FROM bien WHERE id = p_bien AND bailleur_id = p_bailleur)
$$;

COMMENT ON FUNCTION bien_appartient_au_bailleur(uuid, uuid) IS
    'US-13/ADR-02 : prédicat de propriété bailleur. SECURITY DEFINER (owner BYPASSRLS), lecture seule.';

CREATE OR REPLACE FUNCTION gestionnaire_affecte_actif(p_bien uuid, p_gestionnaire uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM affectation
        WHERE bien_id = p_bien AND gestionnaire_id = p_gestionnaire AND statut = 'ACTIVE'
    )
$$;

COMMENT ON FUNCTION gestionnaire_affecte_actif(uuid, uuid) IS
    'US-13/ADR-02 : prédicat d''affectation ACTIVE (périmètre gestionnaire dynamique). SECURITY DEFINER.';

ALTER FUNCTION bien_appartient_au_bailleur(uuid, uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION gestionnaire_affecte_actif(uuid, uuid) OWNER TO loyertracker_batch;
