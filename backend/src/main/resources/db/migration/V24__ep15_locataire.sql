-- =====================================================================================
-- LoyerTracker — Migration V24 : Sprint B (EP-15) — Entité Locataire (additive)
--
-- Le Locataire devient une entité de domaine indépendante du Bail (jusqu'ici, texte libre
-- bail.locataire_nom/locataire_email, ADR-16 D2). Contrairement au Gestionnaire, un Locataire
-- est intrinsèquement lié à un seul bailleur : bailleur_id NOT NULL + RLS (ADR-01, ADR-16 D2).
-- Le Locataire NE devient PAS un compte utilisateur (aucune identité Keycloak, aucun rôle RBAC).
--
-- bail.locataire_id est ajoutée nullable, sans aucun usage applicatif dans ce sprint : le
-- rattachement Bail -> Locataire (avec backfill des colonnes historiques et suppression de
-- bail.locataire_nom/locataire_email) est une bascule NON additive, prévue en V25 (Sprint C).
--
-- Migration additive : rollback applicatif seul viable (aucun objet existant modifié).
-- =====================================================================================

CREATE TABLE locataire (
    id                    UUID PRIMARY KEY,
    bailleur_id           UUID NOT NULL REFERENCES bailleur (id),
    nom                   VARCHAR(255) NOT NULL,
    prenom                VARCHAR(255),
    telephone             VARCHAR(50),
    email                 VARCHAR(320),
    profession            VARCHAR(255),
    date_naissance        DATE,
    type_piece_identite   VARCHAR(50),
    numero_piece_identite VARCHAR(100),
    photo                 BYTEA,
    contact_urgence       VARCHAR(255),
    observations          TEXT,
    statut                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                          CHECK (statut IN ('ACTIVE', 'ARCHIVE')),
    date_creation         TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_archivage        TIMESTAMPTZ
);

CREATE INDEX idx_locataire_bailleur ON locataire (bailleur_id);
CREATE INDEX idx_locataire_bailleur_statut ON locataire (bailleur_id, statut);

ALTER TABLE locataire ENABLE ROW LEVEL SECURITY;
ALTER TABLE locataire FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON locataire
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

COMMENT ON TABLE locataire IS
    'ADR-16 D2/EF-100 : entité de domaine indépendante du Bail. Reste un sujet de données (RGPD, ADR-03) — jamais un compte utilisateur : aucune identité Keycloak, aucun rôle RBAC.';

-- --- Préparation Sprint C (V25) : FK nullable, aucun usage applicatif dans ce sprint --
ALTER TABLE bail ADD COLUMN locataire_id UUID REFERENCES locataire (id);
CREATE INDEX idx_bail_locataire ON bail (locataire_id);

COMMENT ON COLUMN bail.locataire_id IS
    'ADR-16 D3 : préparation de la bascule V25 (Sprint C) — nullable et non utilisée applicativement tant que le backfill/cutover (suppression de locataire_nom/locataire_email) n''a pas eu lieu.';
