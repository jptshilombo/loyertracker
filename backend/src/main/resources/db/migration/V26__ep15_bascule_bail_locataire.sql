-- =====================================================================================
-- LoyerTracker — Migration V26 : Sprint C (EP-15) — Bascule Bail -> Locataire (non additive)
--
-- Cutover annoncé par V24 (Sprint B) : chaque Bail référence désormais un Locataire structuré
-- au lieu du texte libre historique bail.locataire_nom/locataire_email (ADR-16 D2/D3).
--
-- Backfill (RSV-EP15-02, tranché par le PO) : un Locataire par Bail existant, `nom` reçoit la
-- valeur intégrale de bail.locataire_nom (aucun découpage automatique nom/prénom -- `prenom`
-- reste NULL, correction manuelle possible sans impact fonctionnel). `email` reprend
-- bail.locataire_email tel quel (déjà nullable).
--
-- Migration NON additive (RSV-EP15-03, même profil que V20) : bail.locataire_id passe
-- NOT NULL puis bail.locataire_nom/locataire_email sont supprimées. Rollback applicatif non
-- viable -- seule une restauration de backup permet un retour arrière. Le Préflight de la
-- release qui embarquera cette migration doit vérifier un backup disponible avant ET après.
--
-- Renumérotation (cf. ADR-16 D3) : le Plan d'Exécution EP-15 désignait cette bascule « V25 » ;
-- V25 a entretemps été consommée par une migration EP-13 sans rapport (fin de bail, livrée en
-- 1.11.0). Cette migration porte donc le numéro V26 -- aucune décision de fond modifiée.
-- =====================================================================================

-- Backfill + rattachement en une seule passe : la CTE `paires` fige un id de Locataire par Bail.
-- Référencée deux fois (dans `ins` et dans l'UPDATE final), Postgres la matérialise une seule
-- fois : gen_random_uuid() n'est donc PAS réévalué entre les deux usages, garantissant un
-- rattachement 1-pour-1 même si plusieurs baux partagent le même locataire_nom.
WITH paires AS (
    SELECT id AS bail_id, gen_random_uuid() AS locataire_id, bailleur_id,
           locataire_nom, locataire_email
    FROM bail
),
ins AS (
    INSERT INTO locataire (id, bailleur_id, nom, email, statut)
    SELECT locataire_id, bailleur_id, locataire_nom, locataire_email, 'ACTIVE'
    FROM paires
    RETURNING id
)
UPDATE bail
SET locataire_id = paires.locataire_id
FROM paires
WHERE bail.id = paires.bail_id;

-- Cutover : la FK devient obligatoire pour tout bail (existant et futur).
ALTER TABLE bail ALTER COLUMN locataire_id SET NOT NULL;

-- Suppression des colonnes texte libre historiques (non additif, RSV-EP15-03).
ALTER TABLE bail DROP COLUMN locataire_nom;
ALTER TABLE bail DROP COLUMN locataire_email;

COMMENT ON COLUMN bail.locataire_id IS
    'ADR-16 D2/D3 : FK obligatoire vers locataire depuis V26 (bascule Sprint C EP-15). '
    'locataire_nom/locataire_email (texte libre) supprimées par cette même migration.';
