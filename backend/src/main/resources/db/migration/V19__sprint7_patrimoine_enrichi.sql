-- =====================================================================================
-- LoyerTracker — Migration V19 : Sprint 7 (EP-10, US-90) — Patrimoine enrichi
--
-- Ajoute sept champs optionnels au patrimoine (localisation détaillée + informations
-- administratives) et rend `adresse` obligatoire (ADR-12, D-PAT-002).
--
-- `adresse` était nullable depuis V16. Backfill générique par placeholder pour tout
-- patrimoine existant sans adresse (ne perd aucune donnée, à corriger ensuite via
-- PUT /api/patrimoines/{id} — cf. rapport de clôture Sprint 7 pour le cas réel identifié
-- en Production sur le patrimoine "Patrimoine principal").
-- =====================================================================================

ALTER TABLE patrimoine
    ADD COLUMN IF NOT EXISTS ville              VARCHAR(255),
    ADD COLUMN IF NOT EXISTS commune            VARCHAR(255),
    ADD COLUMN IF NOT EXISTS quartier           VARCHAR(255),
    ADD COLUMN IF NOT EXISTS province_etat      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pays               VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description        TEXT,
    ADD COLUMN IF NOT EXISTS reference_interne  VARCHAR(100);

-- Backfill générique : aucun patrimoine existant ne doit être bloqué par la contrainte
-- NOT NULL introduite ci-dessous. Valeur placeholder explicite, à corriger manuellement.
UPDATE patrimoine SET adresse = 'Adresse à renseigner' WHERE adresse IS NULL;

ALTER TABLE patrimoine ALTER COLUMN adresse SET NOT NULL;
