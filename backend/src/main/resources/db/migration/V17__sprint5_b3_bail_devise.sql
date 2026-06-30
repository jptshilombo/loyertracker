-- =====================================================================================
-- LoyerTracker — Migration V17 : Sprint 5 Lot B3 — devise du bail (US-59)
--
-- Ajout d'une colonne `devise` VARCHAR(3) NOT NULL DEFAULT 'EUR' sur la table `bail`.
-- Les baux existants héritent de 'EUR' (devise de référence). La contrainte CHECK limite
-- les valeurs aux trois devises supportées : EUR, USD, CDF.
-- =====================================================================================

ALTER TABLE bail
    ADD COLUMN IF NOT EXISTS devise VARCHAR(3) NOT NULL DEFAULT 'EUR';

ALTER TABLE bail
    ADD CONSTRAINT ck_bail_devise CHECK (devise IN ('EUR', 'USD', 'CDF'));
