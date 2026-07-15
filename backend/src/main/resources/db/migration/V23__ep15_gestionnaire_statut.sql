-- =====================================================================================
-- LoyerTracker — Migration V23 : Sprint A (EP-15) — Cycle de vie du Gestionnaire
--
-- Le Gestionnaire reste une entité globale multi-bailleur, SANS bailleur_id ni RLS
-- (ADR-01/EF-05, inchangé). Cette migration lui ajoute un statut de compte GLOBAL
-- (ACTIVE/SUSPENDU/ARCHIVE, décision PO 2026-07-08, ADR-16 D1) : suspendre/archiver
-- affecte tous les bailleurs qui emploient ce gestionnaire, risque assumé (RSV-EP15-01).
--
-- Migration additive : aucune colonne existante modifiée, rollback applicatif seul viable
-- (ADR-16 D3).
-- =====================================================================================

-- --- 1. Colonnes de profil et de cycle de vie -----------------------------------------
ALTER TABLE gestionnaire
    ADD COLUMN statut         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                              CHECK (statut IN ('ACTIVE', 'SUSPENDU', 'ARCHIVE')),
    ADD COLUMN telephone      VARCHAR(50),
    ADD COLUMN photo          BYTEA,
    ADD COLUMN observations   TEXT,
    ADD COLUMN date_creation  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN date_suspension TIMESTAMPTZ,
    ADD COLUMN date_archivage  TIMESTAMPTZ;

COMMENT ON COLUMN gestionnaire.statut IS
    'ADR-16 D1 : statut GLOBAL du compte, partagé par tous les bailleurs qui emploient ce gestionnaire (pas par relation bailleur-gestionnaire). SUSPENDU sans pré-condition ; ARCHIVE exige l''absence d''affectation ACTIVE tous bailleurs confondus (fonction gestionnaire_a_affectation_active).';
COMMENT ON COLUMN gestionnaire.date_creation IS
    'Valeur par défaut appliquée rétroactivement aux comptes existants au déploiement de cette migration (aucune date de création historique disponible avant V23).';

-- --- 2. Vérification cross-tenant pour l'archivage (ADR-16 D4) ------------------------
-- `affectation` est sous RLS bailleur_isolation : une session applicative scopée à un
-- bailleur ne voit que ses propres lignes. Le pré-check d'archivage (statut global) doit
-- couvrir TOUS les bailleurs : fonction SECURITY DEFINER à surface minimale (booléen
-- uniquement, aucune donnée d'affectation exposée), même patron que gestionnaire_affecte_actif
-- (V13) et les fonctions publiques de quittance (V22).
CREATE OR REPLACE FUNCTION gestionnaire_a_affectation_active(p_gestionnaire uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM affectation
        WHERE gestionnaire_id = p_gestionnaire AND statut = 'ACTIVE'
    )
$$;

COMMENT ON FUNCTION gestionnaire_a_affectation_active(uuid) IS
    'ADR-16 D4/EF-98 : vrai si une affectation ACTIVE existe pour ce gestionnaire, tous bailleurs confondus (traverse la RLS bailleur_isolation d''affectation). Utilisé exclusivement comme garde d''archivage ; ne renvoie qu''un booléen.';

-- --- 3. Vérification de relation bailleur↔gestionnaire (ReBAC, EF-97) -----------------
-- Évaluée dans @PreAuthorize, AVANT positionnement du contexte tenant (comme les autres
-- prédicats ReBAC, ADR-02) : prend le bailleur_id en paramètre explicite plutôt que de
-- s'appuyer sur le GUC RLS.
CREATE OR REPLACE FUNCTION gestionnaire_a_relation(p_gestionnaire uuid, p_bailleur uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM affectation
        WHERE gestionnaire_id = p_gestionnaire AND bailleur_id = p_bailleur
    )
$$;

COMMENT ON FUNCTION gestionnaire_a_relation(uuid, uuid) IS
    'ADR-16/EF-97 : vrai si ce bailleur a (ou a eu) au moins une affectation avec ce gestionnaire, active ou passée. Fonde le droit d''administrer son profil/statut (RM-107 : jamais un autre Gestionnaire).';

ALTER FUNCTION gestionnaire_a_affectation_active(uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION gestionnaire_a_relation(uuid, uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION gestionnaire_a_affectation_active(uuid) TO loyertracker_api;
GRANT EXECUTE ON FUNCTION gestionnaire_a_relation(uuid, uuid) TO loyertracker_api;
