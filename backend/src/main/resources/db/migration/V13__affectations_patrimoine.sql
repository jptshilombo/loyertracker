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

COMMENT ON COLUMN affectation.patrimoine_id IS
    'Sprint 2 Patrimoine : périmètre affecté au niveau patrimoine. Exclusif avec bien_id via affectation_un_seul_perimetre.';
