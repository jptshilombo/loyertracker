-- =====================================================================================
-- LoyerTracker — Migration V16 : Sprint 5 Lot B1+B2
--
-- B1 : sync bien.statut = 'LOUE' pour les biens qui ont un bail ACTIF mais dont le
--      statut est encore 'LIBRE' (incohérence de données pré-existante, US-58).
--
-- B2 : ajout de la colonne `adresse` sur la table `patrimoine` (nullable — les
--      patrimoines existants n'ont pas encore d'adresse, US-57).
-- =====================================================================================

-- B2 : champ adresse sur patrimoine (nullable, migration incrémentale)
ALTER TABLE patrimoine ADD COLUMN IF NOT EXISTS adresse VARCHAR(255);

-- B1 : aligner le statut des biens ayant un bail ACTIF
UPDATE bien
SET statut = 'LOUE'
WHERE statut = 'LIBRE'
  AND id IN (SELECT bien_id FROM bail WHERE statut = 'ACTIF');
