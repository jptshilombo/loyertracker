-- =====================================================================================
-- LoyerTracker — Migration V13 : affectations au niveau patrimoine (Sprint 2)
--
-- Objectif : permettre deux granularités exclusives d'affectation :
--   * affectation bien        : bien_id renseigné, patrimoine_id NULL ;
--   * affectation patrimoine  : patrimoine_id renseigné, bien_id NULL.
--
-- L'héritage d'accès patrimoine reste dynamique : aucune duplication gestionnaire x bien.
-- =====================================================================================

ALTER TABLE affectation
    ADD COLUMN patrimoine_id UUID REFERENCES patrimoine (id);

ALTER TABLE affectation
    ALTER COLUMN bien_id DROP NOT NULL;

ALTER TABLE affectation
    ADD CONSTRAINT affectation_un_seul_perimetre
    CHECK (
        (bien_id IS NOT NULL AND patrimoine_id IS NULL)
        OR (bien_id IS NULL AND patrimoine_id IS NOT NULL)
    );

-- Maintient l'index historique des affectations bien et ajoute le chemin de résolution patrimoine.
CREATE INDEX idx_affectation_patrimoine_gestionnaire_statut
    ON affectation (patrimoine_id, gestionnaire_id, statut)
    WHERE patrimoine_id IS NOT NULL;

CREATE INDEX idx_affectation_bien_gestionnaire_statut
    ON affectation (bien_id, gestionnaire_id, statut)
    WHERE bien_id IS NOT NULL;

-- Symétrique de uq_affectation_active pour le périmètre patrimoine : un seul gestionnaire
-- actif par patrimoine. Les affectations révoquées/expirées conservent l'historique.
CREATE UNIQUE INDEX uq_affectation_patrimoine_active
    ON affectation (patrimoine_id)
    WHERE statut = 'ACTIVE' AND patrimoine_id IS NOT NULL;

-- Étend le prédicat ReBAC bien pour inclure l'héritage patrimoine : un gestionnaire
-- affecté au portefeuille accède dynamiquement à tous les biens du patrimoine.
CREATE OR REPLACE FUNCTION gestionnaire_affecte_actif(p_bien uuid, p_gestionnaire uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM bien b
        JOIN affectation a ON a.statut = 'ACTIVE'
            AND a.gestionnaire_id = p_gestionnaire
            AND (
                a.bien_id = b.id
                OR (a.patrimoine_id IS NOT NULL AND a.patrimoine_id = b.patrimoine_id)
            )
        WHERE b.id = p_bien
    )
$$;

COMMENT ON FUNCTION gestionnaire_affecte_actif(uuid, uuid) IS
    'Sprint 2/ADR-02 : prédicat ACTIVE incluant affectation directe bien ou héritée via patrimoine.';

-- La signature retour change pour exposer patrimoine_id au frontend et dédoublonner
-- les biens visibles lorsqu'une affectation directe et une affectation patrimoine coexistent.
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
    JOIN affectation a ON a.statut = 'ACTIVE'
        AND (
            a.bien_id = b.id
            OR (a.patrimoine_id IS NOT NULL AND a.patrimoine_id = b.patrimoine_id)
        )
    JOIN gestionnaire g ON g.id = a.gestionnaire_id
    WHERE g.keycloak_id = p_keycloak_id
    ORDER BY b.adresse
$$;

COMMENT ON FUNCTION biens_affectes_gestionnaire(text) IS
    'Sprint 2/ADR-02 : liste des biens affectés actifs à un gestionnaire, directs ou hérités via patrimoine. SECURITY DEFINER, lecture seule.';

ALTER FUNCTION gestionnaire_affecte_actif(uuid, uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION biens_affectes_gestionnaire(text) OWNER TO loyertracker_batch;

COMMENT ON COLUMN affectation.patrimoine_id IS
    'Sprint 2 Patrimoine : périmètre affecté au niveau patrimoine. Exclusif avec bien_id via affectation_un_seul_perimetre.';
