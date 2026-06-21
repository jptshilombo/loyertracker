-- =====================================================================================
-- LoyerTracker — Migration V12 : Patrimoine & typologie administrable (US-80/81/82)
-- Sprint 1 (EP-09, D-PAT-001/ADR-11). Introduit le regroupement de biens par patrimoine et
-- remplace la saisie libre de Bien.type par un référentiel administrable (BF-91 : la liste doit
-- pouvoir évoluer sans déploiement applicatif).
--
-- 1) type_bien : référentiel GLOBAL (pas de bailleur_id, pas de RLS — partagé entre bailleurs,
--    cf. addendum CDC §4.2). Seedé avec les 7 codes déjà validés (RM-93/BF-91).
-- 2) patrimoine : table métier sous RLS FORCE dès sa création (RS-02 — aucune table métier
--    n'échappe à la RLS, même patron que `bien`/`affectation` en V1).
-- 3) bien.patrimoine_id : rattachement obligatoire (US-82/EF-92). Étape additive et
--    rétro-compatible (même patron que V11) : colonne nullable -> patrimoine par défaut par
--    bailleur -> rattachement -> NOT NULL.
-- 4) Prédicat ReBAC `patrimoine_appartient_au_bailleur`, même patron que V3
--    (`bien_appartient_au_bailleur`) — défense en profondeur pour `AuthorizationService`.
-- =====================================================================================

-- --- 1. Référentiel TypeBien (global, sans RLS) --------------------------------------
CREATE TABLE type_bien (
    code    VARCHAR(50)  PRIMARY KEY,
    libelle VARCHAR(100) NOT NULL,
    actif   BOOLEAN      NOT NULL DEFAULT true
);

INSERT INTO type_bien (code, libelle) VALUES
    ('APPARTEMENT', 'Appartement'),
    ('BOUTIQUE',    'Boutique'),
    ('BUREAU',      'Bureau'),
    ('VILLA',       'Villa'),
    ('TERRAIN',     'Terrain'),
    ('ENTREPOT',    'Entrepôt'),
    ('AUTRE',       'Autre');

COMMENT ON TABLE type_bien IS
    'Référentiel administrable de la typologie des biens (US-81/RM-93/BF-91) — partagé entre bailleurs, sans RLS.';

-- --- 2. Table Patrimoine (RLS FORCE dès la création — RS-02, ADR-01) -----------------
CREATE TABLE patrimoine (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bailleur_id   UUID NOT NULL REFERENCES bailleur (id),
    nom           VARCHAR(255) NOT NULL,
    statut        VARCHAR(20)  NOT NULL DEFAULT 'ACTIF'
                  CHECK (statut IN ('ACTIF', 'ARCHIVE')),
    date_creation TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_patrimoine_bailleur ON patrimoine (bailleur_id);

ALTER TABLE patrimoine ENABLE ROW LEVEL SECURITY;
ALTER TABLE patrimoine FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON patrimoine
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

-- --- 3. Rattachement obligatoire Bien -> Patrimoine (US-82/EF-92) --------------------
ALTER TABLE bien ADD COLUMN patrimoine_id UUID REFERENCES patrimoine (id);

-- Patrimoine par défaut pour chaque bailleur possédant déjà au moins un bien (US-80/82) :
-- garantit 0 bien orphelin après migration, sans en créer pour les bailleurs sans bien.
INSERT INTO patrimoine (bailleur_id, nom)
SELECT DISTINCT bailleur_id, 'Patrimoine principal'
FROM bien
WHERE patrimoine_id IS NULL;

-- Rattachement rétroactif : chaque bien existant rejoint le patrimoine par défaut de son bailleur.
UPDATE bien
SET patrimoine_id = patrimoine.id
FROM patrimoine
WHERE bien.bailleur_id = patrimoine.bailleur_id
  AND bien.patrimoine_id IS NULL
  AND patrimoine.nom = 'Patrimoine principal';

ALTER TABLE bien ALTER COLUMN patrimoine_id SET NOT NULL;
CREATE INDEX idx_bien_patrimoine ON bien (patrimoine_id);

COMMENT ON COLUMN bien.patrimoine_id IS
    'Rattachement obligatoire au patrimoine (V12, US-82/EF-92). Patrimoine par défaut créé pour les biens pré-existants.';

-- --- 4. Prédicat ReBAC de propriété (même patron que V3) -----------------------------
CREATE OR REPLACE FUNCTION patrimoine_appartient_au_bailleur(p_patrimoine uuid, p_bailleur uuid)
    RETURNS boolean
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT EXISTS (SELECT 1 FROM patrimoine WHERE id = p_patrimoine AND bailleur_id = p_bailleur)
$$;

COMMENT ON FUNCTION patrimoine_appartient_au_bailleur(uuid, uuid) IS
    'US-80/ADR-02 : prédicat de propriété bailleur sur un patrimoine. SECURITY DEFINER (owner BYPASSRLS), lecture seule.';

ALTER FUNCTION patrimoine_appartient_au_bailleur(uuid, uuid) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION patrimoine_appartient_au_bailleur(uuid, uuid) TO loyertracker_api;

-- --- 5. Privilèges sur les 2 nouvelles tables -----------------------------------------
-- `loyertracker_batch` (BYPASSRLS, V1) doit lire `patrimoine` pour le prédicat ci-dessus (même
-- patron que V1 §4 : « le rôle batch peut lire/écrire toutes les tables »). `loyertracker_api`
-- est normalement couvert par `ALTER DEFAULT PRIVILEGES` (V5), mais on le déclare explicitement
-- ici, comme V6-V10 le font déjà pour leurs fonctions — aucune table n'avait été créée depuis V5.
GRANT SELECT, INSERT, UPDATE, DELETE ON patrimoine, type_bien TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON patrimoine, type_bien TO loyertracker_api;
