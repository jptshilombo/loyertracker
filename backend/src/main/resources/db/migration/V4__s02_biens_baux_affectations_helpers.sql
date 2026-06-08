-- =====================================================================================
-- LoyerTracker — Migration V4 : helpers S02 biens/baux/affectations
-- Fournit les chemins privilégiés minimaux nécessaires sous RLS FORCE.
-- =====================================================================================

-- US-20 : archivage métier d'un bien sans suppression physique.
ALTER TABLE bien DROP CONSTRAINT bien_statut_check;
ALTER TABLE bien ADD CONSTRAINT bien_statut_check
    CHECK (statut IN ('LIBRE', 'LOUE', 'EN_TRAVAUX', 'ARCHIVE'));

CREATE OR REPLACE FUNCTION resolve_bien_bailleur(p_bien uuid)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT bailleur_id FROM bien WHERE id = p_bien
$$;

COMMENT ON FUNCTION resolve_bien_bailleur(uuid) IS
    'S02/ADR-09 : résolution du tenant propriétaire d''un bien sous RLS FORCE. SECURITY DEFINER, lecture seule.';

CREATE OR REPLACE FUNCTION biens_affectes_gestionnaire(p_keycloak_id text)
    RETURNS TABLE(id uuid, adresse varchar, type varchar, statut varchar)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT b.id, b.adresse, b.type, b.statut
    FROM bien b
    JOIN affectation a ON a.bien_id = b.id AND a.statut = 'ACTIVE'
    JOIN gestionnaire g ON g.id = a.gestionnaire_id
    WHERE g.keycloak_id = p_keycloak_id
    ORDER BY b.adresse
$$;

COMMENT ON FUNCTION biens_affectes_gestionnaire(text) IS
    'S02/ADR-02 : liste des biens affectés actifs à un gestionnaire. SECURITY DEFINER, lecture seule.';

ALTER FUNCTION resolve_bien_bailleur(uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION biens_affectes_gestionnaire(text) OWNER TO loyertracker_batch;
