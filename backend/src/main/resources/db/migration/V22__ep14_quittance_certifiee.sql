-- =====================================================================================
-- LoyerTracker — Migration V22 : Sprint 11 (EP-14a) — Quittances certifiées (ADR-15/D-QC-001)
--
-- La certification exige un exemplaire de référence persistant (ADR-15 D1 : supplante
-- l'arbitrage C pour les quittances uniquement — les avis d'échéance restent à la volée).
--
-- `contenu` porte le payload canonique EXACT (JSON déterministe, ADR-15 D2) dont le SHA-256
-- est `content_hash` : la page publique affiche le contenu certifié à l'émission, jamais les
-- données vivantes (une anonymisation RGPD ultérieure du locataire ne réécrit pas le document
-- certifié — obligation de conservation comptable du bailleur, ADR-15 §RGPD). `pdf_hash` est
-- le SHA-256 des octets du PDF stocké, re-vérifié avant chaque téléchargement public.
--
-- Numérotation par bailleur+année (kickoff K1) : compteur dédié, strictement croissant, un
-- numéro consommé n'est JAMAIS réutilisé (même après annulation). La régénération conserve le
-- numéro et incrémente la version (une ligne par version, chaînage `remplacee_par`).
--
-- Migration additive : rollback applicatif seul viable (aucun objet existant modifié).
-- =====================================================================================

-- --- 1. Compteur de numérotation par bailleur + année (K1) ---------------------------
CREATE TABLE quittance_numerotation (
    bailleur_id UUID NOT NULL REFERENCES bailleur (id),
    annee       INT  NOT NULL,
    prochain    INT  NOT NULL DEFAULT 1 CHECK (prochain >= 1),
    PRIMARY KEY (bailleur_id, annee)
);

ALTER TABLE quittance_numerotation ENABLE ROW LEVEL SECURITY;
ALTER TABLE quittance_numerotation FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON quittance_numerotation
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON quittance_numerotation TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE ON quittance_numerotation TO loyertracker_api;

-- --- 2. Quittances certifiées (RLS FORCE, même patron que V20) ------------------------
CREATE TABLE quittance (
    id                 UUID PRIMARY KEY,
    bailleur_id        UUID NOT NULL REFERENCES bailleur (id),
    paiement_id        UUID NOT NULL REFERENCES paiement (id),
    numero             VARCHAR(20) NOT NULL,
    version            INT NOT NULL DEFAULT 1 CHECK (version >= 1),
    statut             VARCHAR(20) NOT NULL
                       CHECK (statut IN ('EMISE', 'ANNULEE', 'REMPLACEE')),
    remplacee_par      UUID REFERENCES quittance (id),
    contenu            TEXT NOT NULL,
    content_hash       VARCHAR(64) NOT NULL,
    pdf_hash           VARCHAR(64) NOT NULL,
    pdf                BYTEA NOT NULL,
    empreinte_metier   VARCHAR(64) NOT NULL,
    token_kid          SMALLINT NOT NULL DEFAULT 1,
    emise_le           TIMESTAMPTZ NOT NULL DEFAULT now(),
    nb_telechargements INT NOT NULL DEFAULT 0 CHECK (nb_telechargements >= 0),
    nb_verifications   INT NOT NULL DEFAULT 0 CHECK (nb_verifications >= 0),
    UNIQUE (bailleur_id, numero, version)
);

CREATE INDEX idx_quittance_bailleur ON quittance (bailleur_id);
CREATE INDEX idx_quittance_paiement ON quittance (paiement_id);
-- Au plus une quittance active par loyer (la régénération remplace, jamais ne duplique).
CREATE UNIQUE INDEX uq_quittance_paiement_emise ON quittance (paiement_id)
    WHERE statut = 'EMISE';

ALTER TABLE quittance ENABLE ROW LEVEL SECURITY;
ALTER TABLE quittance FORCE  ROW LEVEL SECURITY;
CREATE POLICY bailleur_isolation ON quittance
    USING (bailleur_id = NULLIF(current_setting('app.current_bailleur_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON quittance TO loyertracker_batch;
GRANT SELECT, INSERT, UPDATE, DELETE ON quittance TO loyertracker_api;

-- --- 3. Journal des vérifications publiques (RGPD-minimal : ni IP ni user-agent) ------
-- Aucun GRANT direct à loyertracker_api : la table n'est accessible qu'au travers des
-- fonctions SECURITY DEFINER ci-dessous (défense en profondeur — la vérification publique
-- s'exécute sans contexte tenant).
CREATE TABLE quittance_verification_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quittance_id   UUID REFERENCES quittance (id),
    horodatage     TIMESTAMPTZ NOT NULL DEFAULT now(),
    type_evenement VARCHAR(20) NOT NULL
                   CHECK (type_evenement IN ('VERIFICATION', 'TELECHARGEMENT')),
    resultat       VARCHAR(10) NOT NULL CHECK (resultat IN ('VALIDE', 'INVALIDE'))
);

CREATE INDEX idx_quittance_verification_log_quittance
    ON quittance_verification_log (quittance_id, horodatage);

GRANT SELECT, INSERT ON quittance_verification_log TO loyertracker_batch;

-- --- 4. Lecture publique (Sprint 12) — même patron SECURITY DEFINER que V10/V12/V13 ---
-- Le HMAC du token est vérifié côté Java AVANT tout appel ; la fonction ne renvoie que le
-- contrat public (ADR-15 D5/K2) et ne voit jamais le token.
CREATE OR REPLACE FUNCTION lire_quittance_publique(p_id uuid)
    RETURNS TABLE (
        id            uuid,
        numero        varchar,
        version       int,
        statut        varchar,
        contenu       text,
        content_hash  varchar(64),
        pdf_hash      varchar(64),
        emise_le      timestamptz,
        token_kid     smallint,
        remplacante_numero  varchar,
        remplacante_version int)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT q.id, q.numero, q.version, q.statut, q.contenu, q.content_hash, q.pdf_hash,
           q.emise_le, q.token_kid, r.numero, r.version
    FROM quittance q
    LEFT JOIN quittance r ON r.id = q.remplacee_par
    WHERE q.id = p_id;
$$;

COMMENT ON FUNCTION lire_quittance_publique(uuid) IS
    'US-102/ADR-15 D5 : lecture publique d''une quittance certifiée pour la page de vérification. SECURITY DEFINER (owner BYPASSRLS loyertracker_batch), lecture seule, contrat public strict — le token HMAC est vérifié côté application avant l''appel.';

CREATE OR REPLACE FUNCTION lire_pdf_quittance_publique(p_id uuid)
    RETURNS TABLE (pdf bytea, pdf_hash varchar(64), numero varchar, version int)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT q.pdf, q.pdf_hash, q.numero, q.version FROM quittance q WHERE q.id = p_id;
$$;

COMMENT ON FUNCTION lire_pdf_quittance_publique(uuid) IS
    'US-102/ADR-15 D5 : exemplaire officiel pour le téléchargement public. Le PDF est re-haché côté application et comparé à pdf_hash avant d''être servi.';

CREATE OR REPLACE FUNCTION journaliser_evenement_quittance(
        p_quittance uuid, p_type varchar, p_resultat varchar)
    RETURNS void
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = public
AS $$
    INSERT INTO quittance_verification_log (quittance_id, type_evenement, resultat)
    VALUES (p_quittance, p_type, p_resultat);

    UPDATE quittance
    SET nb_verifications   = nb_verifications   + CASE WHEN p_type = 'VERIFICATION'   AND p_resultat = 'VALIDE' THEN 1 ELSE 0 END,
        nb_telechargements = nb_telechargements + CASE WHEN p_type = 'TELECHARGEMENT' AND p_resultat = 'VALIDE' THEN 1 ELSE 0 END
    WHERE id = p_quittance AND p_resultat = 'VALIDE';
$$;

COMMENT ON FUNCTION journaliser_evenement_quittance(uuid, varchar, varchar) IS
    'US-104/ADR-15 D7 : journalise un événement de vérification/téléchargement public (RGPD-minimal, ni IP ni user-agent) et incrémente les compteurs de la quittance quand l''événement est valide.';

ALTER FUNCTION lire_quittance_publique(uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION lire_pdf_quittance_publique(uuid) OWNER TO loyertracker_batch;
ALTER FUNCTION journaliser_evenement_quittance(uuid, varchar, varchar) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION lire_quittance_publique(uuid) TO loyertracker_api;
GRANT EXECUTE ON FUNCTION lire_pdf_quittance_publique(uuid) TO loyertracker_api;
GRANT EXECUTE ON FUNCTION journaliser_evenement_quittance(uuid, varchar, varchar) TO loyertracker_api;
