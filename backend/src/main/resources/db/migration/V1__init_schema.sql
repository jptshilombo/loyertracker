-- =====================================================================================
-- LoyerTracker — Migration V1 : schéma initial complet (US-03)
-- Étape 06 (Phase 07 DevSecOps). Crée toutes les tables, contraintes, index (dont uniques
-- partiels — ADR-07), politiques Row-Level Security (ADR-01) et le rôle technique batch.
--
-- Conventions :
--   * Clés primaires UUID via gen_random_uuid() (cœur PostgreSQL ≥ 13, aucune extension).
--   * Domaines de statut portés par des contraintes CHECK (lisibles, sans type ENUM natif).
--   * Horodatages en timestamptz (UTC), stockés par l'application.
-- =====================================================================================

-- =====================================================================================
-- 1. TABLES
-- =====================================================================================

-- --- Acteurs ------------------------------------------------------------------------

CREATE TABLE bailleur (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id   VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(320) NOT NULL UNIQUE,
    nom           VARCHAR(255) NOT NULL,
    prenom        VARCHAR(255) NOT NULL,
    date_creation TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE gestionnaire (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_id VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(320) NOT NULL UNIQUE,
    nom         VARCHAR(255) NOT NULL,
    prenom      VARCHAR(255) NOT NULL
);

CREATE TABLE invitation (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id     UUID NOT NULL REFERENCES bailleur (id),
    email           VARCHAR(320) NOT NULL,
    token           VARCHAR(255) NOT NULL UNIQUE,
    statut          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (statut IN ('PENDING', 'ACCEPTED', 'EXPIRED')),
    date_expiration TIMESTAMPTZ  NOT NULL,
    date_creation   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- --- Patrimoine & baux --------------------------------------------------------------

CREATE TABLE bien (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id UUID NOT NULL REFERENCES bailleur (id),
    adresse     VARCHAR(500) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    statut      VARCHAR(20)  NOT NULL DEFAULT 'LIBRE'
                CHECK (statut IN ('LIBRE', 'LOUE', 'EN_TRAVAUX'))
);

CREATE TABLE bail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id     UUID NOT NULL REFERENCES bailleur (id),
    bien_id         UUID NOT NULL REFERENCES bien (id),
    locataire_nom   VARCHAR(255)  NOT NULL,
    locataire_email VARCHAR(320),
    loyer_cc        NUMERIC(12, 2) NOT NULL CHECK (loyer_cc >= 0),
    depot_garantie  NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (depot_garantie >= 0),
    date_debut      DATE NOT NULL,
    date_fin        DATE,
    statut          VARCHAR(20) NOT NULL DEFAULT 'ACTIF'
                    CHECK (statut IN ('ACTIF', 'CLOS'))
);

CREATE TABLE affectation (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id        UUID NOT NULL REFERENCES bailleur (id),
    bien_id            UUID NOT NULL REFERENCES bien (id),
    gestionnaire_id    UUID NOT NULL REFERENCES gestionnaire (id),
    type_honoraires    VARCHAR(20) NOT NULL
                       CHECK (type_honoraires IN ('POURCENTAGE', 'FORFAIT')),
    montant_honoraires NUMERIC(12, 2) NOT NULL CHECK (montant_honoraires >= 0),
    date_debut         DATE NOT NULL,
    date_fin           DATE,
    statut             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                       CHECK (statut IN ('ACTIVE', 'REVOQUEE', 'EXPIREE')),
    date_revocation    TIMESTAMPTZ
);

-- --- Flux financiers ----------------------------------------------------------------

CREATE TABLE paiement (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id      UUID NOT NULL REFERENCES bailleur (id),
    bail_id          UUID NOT NULL REFERENCES bail (id),
    bien_id          UUID NOT NULL REFERENCES bien (id),
    periode          CHAR(7) NOT NULL CHECK (periode ~ '^[0-9]{4}-[0-9]{2}$'),  -- YYYY-MM
    montant_attendu  NUMERIC(12, 2) NOT NULL CHECK (montant_attendu >= 0),
    montant_recu     NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (montant_recu >= 0),
    date_exigibilite DATE NOT NULL,
    statut           VARCHAR(20) NOT NULL DEFAULT 'IMPAYE'
                     CHECK (statut IN ('RECU', 'PARTIEL', 'EN_RETARD', 'IMPAYE'))
);

CREATE TABLE garantie (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id    UUID NOT NULL REFERENCES bailleur (id),
    bail_id        UUID NOT NULL REFERENCES bail (id),
    montant        NUMERIC(12, 2) NOT NULL CHECK (montant >= 0),
    type_garantie  VARCHAR(50) NOT NULL,
    date_depot     DATE NOT NULL,
    statut         VARCHAR(20) NOT NULL DEFAULT 'DETENU'
                   CHECK (statut IN ('DETENU', 'RESTITUE_PARTIEL', 'RESTITUE_TOTAL')),
    montant_retenu NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (montant_retenu >= 0),
    motif_retenue  TEXT
);

CREATE TABLE honoraire (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id    UUID NOT NULL REFERENCES bailleur (id),
    affectation_id UUID NOT NULL REFERENCES affectation (id),
    periode        CHAR(7) NOT NULL CHECK (periode ~ '^[0-9]{4}-[0-9]{2}$'),  -- YYYY-MM
    montant        NUMERIC(12, 2) NOT NULL CHECK (montant >= 0),
    statut         VARCHAR(20) NOT NULL DEFAULT 'DU'
                   CHECK (statut IN ('DU', 'EN_ATTENTE', 'PAYE'))
);

-- --- Pilotage -----------------------------------------------------------------------

CREATE TABLE alerte (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id    UUID NOT NULL REFERENCES bailleur (id),
    destinataire_id UUID NOT NULL,  -- bailleur ou gestionnaire (polymorphe, pas de FK)
    type           VARCHAR(30) NOT NULL
                   CHECK (type IN ('LOYER_EN_RETARD', 'FIN_BAIL', 'PREAVIS', 'GARANTIE_NON_RESTITUEE')),
    bien_id        UUID REFERENCES bien (id),
    bail_id        UUID REFERENCES bail (id),
    periode        CHAR(7) CHECK (periode IS NULL OR periode ~ '^[0-9]{4}-[0-9]{2}$'),
    message        TEXT NOT NULL,
    statut         VARCHAR(20) NOT NULL DEFAULT 'NON_LUE'
                   CHECK (statut IN ('NON_LUE', 'LUE')),
    date_creation  TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_lecture   TIMESTAMPTZ
);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id UUID REFERENCES bailleur (id),
    acteur_id   UUID NOT NULL,            -- bailleur ou gestionnaire (polymorphe, pas de FK)
    acteur_role VARCHAR(20) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   UUID,
    horodatage  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- 'timestamp' est un mot réservé SQL
    details     JSONB
);

-- =====================================================================================
-- 2. INDEX UNIQUES PARTIELS (ADR-07 — invariants métier garantis en base)
-- =====================================================================================

-- EF-12 : un seul bail ACTIF par bien
CREATE UNIQUE INDEX uq_bail_actif ON bail (bien_id) WHERE statut = 'ACTIF';

-- EF-21 : un seul gestionnaire ACTIF (affectation) par bien
CREATE UNIQUE INDEX uq_affectation_active ON affectation (bien_id) WHERE statut = 'ACTIVE';

-- EF-30/33 : un loyer attendu par bien/période (total, pas partiel)
CREATE UNIQUE INDEX uq_paiement_periode ON paiement (bien_id, periode);

-- EF-51 : un honoraire par affectation/période
CREATE UNIQUE INDEX uq_honoraire_periode ON honoraire (affectation_id, periode);

-- EF-65 : anti-doublon des alertes NON_LUE
CREATE UNIQUE INDEX uq_alerte_nonlue ON alerte (type, bien_id, periode) WHERE statut = 'NON_LUE';

-- =====================================================================================
-- 3. INDEX DE PERFORMANCE (ENF-06)
-- =====================================================================================

CREATE INDEX idx_bien_bailleur                 ON bien (bailleur_id);
CREATE INDEX idx_bail_bailleur                 ON bail (bailleur_id);
CREATE INDEX idx_bail_bien                     ON bail (bien_id);
CREATE INDEX idx_paiement_bailleur             ON paiement (bailleur_id);
CREATE INDEX idx_paiement_bail                 ON paiement (bail_id);
CREATE INDEX idx_affectation_gestionnaire_statut ON affectation (gestionnaire_id, statut);
CREATE INDEX idx_alerte_destinataire_statut    ON alerte (destinataire_id, statut);
CREATE INDEX idx_audit_bailleur                ON audit_log (bailleur_id);

-- =====================================================================================
-- 4. ROLE TECHNIQUE BATCH (ADR-01 — contourne la RLS pour les jobs multi-bailleur)
--    NOLOGIN / sans mot de passe ici : aucun secret n'est versionné. L'activation
--    (LOGIN + mot de passe) sera faite au pas « batch » via une migration ultérieure.
-- =====================================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loyertracker_batch') THEN
        CREATE ROLE loyertracker_batch BYPASSRLS NOLOGIN;
    END IF;
END
$$;

-- =====================================================================================
-- 5. ROW-LEVEL SECURITY (ADR-01 — cloisonnement par bailleur, défense en profondeur)
--    FORCE est indispensable : l'API se connecte en tant que PROPRIÉTAIRE des tables, or un
--    propriétaire contourne la RLS par défaut.
--    NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid → NULL quand le GUC est
--    absent OU vide (cas d'une connexion poolée « réinitialisée » : un GUC placeholder rendu
--    renvoie '' et non NULL). Comparaison à NULL ⇒ aucune ligne visible (fail-closed, sans erreur).
-- =====================================================================================

-- Table bailleur : isolation sur l'identité (id) plutôt que bailleur_id.
ALTER TABLE bailleur ENABLE ROW LEVEL SECURITY;
ALTER TABLE bailleur FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON bailleur
    USING (id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

-- Tables métier portant bailleur_id : isolation sur bailleur_id.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'invitation', 'bien', 'bail', 'affectation',
        'paiement', 'garantie', 'honoraire', 'alerte', 'audit_log'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', t);
        EXECUTE format('ALTER TABLE %I FORCE  ROW LEVEL SECURITY;', t);
        EXECUTE format(
            'CREATE POLICY bailleur_isolation ON %I '
            'USING (bailleur_id = NULLIF(current_setting(''app.current_bailleur_id'', true), '''')::uuid);', t);
    END LOOP;
END
$$;

-- Le rôle batch peut lire/écrire toutes les tables (la RLS est de toute façon contournée).
GRANT USAGE ON SCHEMA public TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO loyertracker_batch;
