-- =====================================================================================
-- LoyerTracker — Migration V20 : Sprint 9 (EP-12a) — Garantie : ledger de mouvements
--
-- Introduit `garantie_movement` (journal append-only, ADR-14/D-GAR-001) et la colonne de cache
-- `garantie.solde_actuel`, recalculée de façon transactionnelle et synchrone à chaque mouvement
-- (jamais en asynchrone) : `garantie.statut` reste inchangé et continue d'être lu directement par
-- le batch d'alertes GARANTIE_NON_RESTITUEE (SECURITY DEFINER, V9/V10) — aucune rupture.
--
-- Backfill rétroactif (aucune perte d'historique), pour chaque garantie existante :
--   - DEPOT_INITIAL (credit = montant)
--   - si montant_retenu > 0 : AJUSTEMENT (debit = montant_retenu) reconstituant la retenue
--     historique (type précis inconnu pour les données pré-existantes ; motif_retenue conservé)
--   - si statut = RESTITUE_TOTAL : RESTITUTION (debit = solde restant) ramenant le solde à 0
-- Solde recalculé : DETENU/RESTITUE_PARTIEL -> montant - montant_retenu ; RESTITUE_TOTAL -> 0.
--
-- bail.depot_garantie (ADR-14 §8, arbitrage PO kickoff Sprint 9 du 2026-07-02) : devient une
-- valeur dérivée du ledger, plus jamais saisie ni stockée sur `bail` -> colonne supprimée.
-- =====================================================================================

-- --- 1. Table garantie_movement (RLS FORCE dès la création, même patron que V12) -----
CREATE TABLE garantie_movement (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id         UUID NOT NULL REFERENCES bailleur (id),
    garantie_id         UUID NOT NULL REFERENCES garantie (id),
    date_mouvement      DATE NOT NULL,
    type                VARCHAR(30) NOT NULL
                        CHECK (type IN ('DEPOT_INITIAL', 'COMPLEMENT', 'RETENUE_LOYER',
                                        'RETENUE_CHARGES', 'RETENUE_REPARATION', 'RESTITUTION',
                                        'AJUSTEMENT', 'ANNULATION')),
    debit               NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit              NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
    solde_apres         NUMERIC(12, 2) NOT NULL CHECK (solde_apres >= 0),
    motif               TEXT,
    utilisateur         VARCHAR(255) NOT NULL,
    commentaire         TEXT,
    reference_document  VARCHAR(255),
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_garantie_movement_bailleur ON garantie_movement (bailleur_id);
CREATE INDEX idx_garantie_movement_garantie ON garantie_movement (garantie_id, date_mouvement);

ALTER TABLE garantie_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE garantie_movement FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON garantie_movement
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON garantie_movement TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON garantie_movement TO loyertracker_api;

-- --- 2. Cache transactionnel du solde (ADR-14 §3) ------------------------------------
ALTER TABLE garantie ADD COLUMN solde_actuel NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (solde_actuel >= 0);

-- --- 3. Backfill rétroactif -----------------------------------------------------------
-- DEPOT_INITIAL pour chaque garantie existante.
INSERT INTO garantie_movement (bailleur_id, garantie_id, date_mouvement, type, debit, credit,
    solde_apres, motif, utilisateur, commentaire)
SELECT bailleur_id, id, date_depot, 'DEPOT_INITIAL', 0, montant, montant,
    'Dépôt initial de la garantie', 'system',
    'Backfill rétroactif (migration V20) reconstituant le dépôt initial'
FROM garantie;

-- Retenue historique (motif_retenue conservé), pour les garanties avec montant_retenu > 0.
INSERT INTO garantie_movement (bailleur_id, garantie_id, date_mouvement, type, debit, credit,
    solde_apres, motif, utilisateur, commentaire)
SELECT bailleur_id, id, date_depot, 'AJUSTEMENT', montant_retenu, 0, montant - montant_retenu,
    motif_retenue, 'system',
    'Backfill rétroactif (migration V20) reconstituant une retenue historique'
FROM garantie
WHERE montant_retenu > 0;

-- Dossiers définitivement clos (RESTITUE_TOTAL) -> solde ramené à 0.
INSERT INTO garantie_movement (bailleur_id, garantie_id, date_mouvement, type, debit, credit,
    solde_apres, motif, utilisateur, commentaire)
SELECT bailleur_id, id, date_depot, 'RESTITUTION', montant - montant_retenu, 0, 0,
    'Restitution totale (historique)', 'system',
    'Backfill rétroactif (migration V20) clôturant un dossier déjà restitué intégralement'
FROM garantie
WHERE statut = 'RESTITUE_TOTAL' AND (montant - montant_retenu) > 0;

-- Synchronise le cache garantie.solde_actuel avec l'état actuel (invariant du Plan d'Exécution
-- Sprint 9 : solde == montant - montant_retenu, sauf RESTITUE_TOTAL où le dossier est clos à 0).
UPDATE garantie
SET solde_actuel = CASE WHEN statut = 'RESTITUE_TOTAL' THEN 0 ELSE montant - montant_retenu END;

-- --- 4. bail.depot_garantie devient une valeur dérivée du ledger (ADR-14 §8) ---------
ALTER TABLE bail DROP COLUMN depot_garantie;
