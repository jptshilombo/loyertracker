-- =====================================================================================
-- LoyerTracker — Migration V21 : Sprint 10 (EP-12b) — Usage métier de la garantie
--
-- US-95 (retenue sur impayé, ADR-14 §5) relie un mouvement RETENUE_LOYER au paiement qu'il couvre
-- via une FK nullable : nullable car la grande majorité des paiements ne sont jamais couverts par
-- la garantie, et l'ordre de création est paiement (batch V6/V18) puis, éventuellement bien plus
-- tard, mouvement (décision explicite du gestionnaire, jamais automatique).
--
-- Pas de ON DELETE explicite : garantie_movement est un journal append-only (jamais de DELETE en
-- usage normal), aligné sur la convention du projet (aucune FK V1 ne spécifie de ON DELETE).
-- =====================================================================================

ALTER TABLE paiement
    ADD COLUMN garantie_movement_id UUID REFERENCES garantie_movement (id);

CREATE INDEX idx_paiement_garantie_movement_id ON paiement (garantie_movement_id);
