-- =====================================================================================
-- LoyerTracker — Migration V11 : socle « Quittances de loyer » (lot post-go-live)
-- Phase exploitation — fondation données pour la quittance (et l'avis d'échéance).
--
-- Deux besoins du document légal de quittance, non couverts par le modèle initial :
--   1) VENTILATION loyer / charges. Le bail ne portait qu'un loyer « charges comprises »
--      (`loyer_cc`). Une quittance ventile le LOYER HORS CHARGES et la PROVISION DE CHARGES.
--      On ajoute donc `loyer_hc` et `provision_charges`, et on impose la cohérence
--      `loyer_cc = loyer_hc + provision_charges` (le `loyer_cc` existant reste la source de vérité
--      des échéances/paiements, inchangé — aucune migration de la table `paiement`).
--   2) ADRESSE du bailleur. La quittance porte le nom + l'adresse du bailleur. Le nom/prénom
--      existent déjà (`bailleur.nom` / `bailleur.prenom`) ; il manque l'adresse postale.
--
-- RLS : aucune policy modifiée. Les colonnes ajoutées héritent des policies `bail_isolation` /
-- `bailleur_isolation` existantes (cloisonnement par `bailleur_id` / `id`).
-- =====================================================================================

-- --- 1. Adresse postale du bailleur (mentions de quittance) --------------------------
-- Nullable : les bailleurs déjà inscrits n'en ont pas ; l'UI invite à la renseigner et la
-- génération de quittance l'exige (contrôle applicatif, message explicite si absente).
ALTER TABLE bailleur ADD COLUMN adresse VARCHAR(500);

COMMENT ON COLUMN bailleur.adresse IS
    'Adresse postale du bailleur — mention obligatoire de la quittance de loyer (V11).';

-- --- 2. Ventilation loyer hors charges / provision de charges ------------------------
-- Étape additive et rétro-compatible : on crée les colonnes, on initialise les baux existants
-- (loyer_hc = loyer_cc, provision_charges = 0), puis on verrouille par NOT NULL + cohérence.
ALTER TABLE bail ADD COLUMN loyer_hc          NUMERIC(12, 2);
ALTER TABLE bail ADD COLUMN provision_charges NUMERIC(12, 2) NOT NULL DEFAULT 0
                 CHECK (provision_charges >= 0);

-- Rétro-compat : tout le loyer existant est réputé hors charges, provision nulle.
UPDATE bail SET loyer_hc = loyer_cc WHERE loyer_hc IS NULL;

ALTER TABLE bail ALTER COLUMN loyer_hc SET NOT NULL;
ALTER TABLE bail ADD CONSTRAINT chk_loyer_hc_positif CHECK (loyer_hc >= 0);

-- Cohérence métier : le « charges comprises » est exactement la somme des deux postes.
-- Vrai pour les lignes rétro-initialisées (loyer_cc = loyer_cc + 0) et imposé aux nouveaux baux.
ALTER TABLE bail ADD CONSTRAINT chk_loyer_cc_coherent
                 CHECK (loyer_cc = loyer_hc + provision_charges);

COMMENT ON COLUMN bail.loyer_hc IS
    'Loyer hors charges (V11). loyer_cc = loyer_hc + provision_charges.';
COMMENT ON COLUMN bail.provision_charges IS
    'Provision mensuelle pour charges (V11). loyer_cc = loyer_hc + provision_charges.';
