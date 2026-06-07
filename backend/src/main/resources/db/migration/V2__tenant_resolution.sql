-- =====================================================================================
-- LoyerTracker — Migration V2 : résolution du contexte tenant (ADR-09)
-- Sprint S01 / Phase 07. Fournit le chemin de lecture privilégié, étroit et auditable,
-- nécessaire pour positionner `app.current_bailleur_id` sous RLS FORCE (ADR-01) :
--   * resolve_bailleur_id(keycloak_id) : mappe l'identité Keycloak (JWT sub) vers bailleur.id
--     (US-11/US-13 — la lecture par keycloak_id est sinon masquée par la RLS, fail-closed).
--   * resolve_invitation_bailleur(token) : renvoie le bailleur_id de l'invitation portant ce
--     token (US-12 — acceptation NON authentifiée, aucun contexte tenant connu d'avance).
--
-- Sécurité : fonctions SECURITY DEFINER en LECTURE SEULE, propriété du rôle BYPASSRLS
-- `loyertracker_batch` (créé en V1). Elles ne révèlent qu'un identifiant que l'appelant
-- possède déjà (son propre sub, ou un token de capacité). search_path figé = anti-injection.
-- =====================================================================================

-- --- 1. Mapping identité Keycloak → bailleur ----------------------------------------
CREATE OR REPLACE FUNCTION resolve_bailleur_id(p_keycloak_id text)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT id FROM bailleur WHERE keycloak_id = p_keycloak_id
$$;

COMMENT ON FUNCTION resolve_bailleur_id(text) IS
    'ADR-09 : résolution tenant sous RLS. SECURITY DEFINER (owner BYPASSRLS). Lecture seule.';

-- --- 2. Résolution du tenant porteur d'une invitation (acceptation US-12) ------------
CREATE OR REPLACE FUNCTION resolve_invitation_bailleur(p_token text)
    RETURNS uuid
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT bailleur_id FROM invitation WHERE token = p_token
$$;

COMMENT ON FUNCTION resolve_invitation_bailleur(text) IS
    'ADR-09 : tenant d''une invitation par token (acceptation non authentifiée). Lecture seule.';

-- --- 3. Propriété BYPASSRLS ----------------------------------------------------------
-- Le propriétaire d'une fonction SECURITY DEFINER détermine l'identité d'exécution. Les tables
-- étant en RLS FORCE (le propriétaire lui-même y est soumis), seule la propriété par un rôle
-- BYPASSRLS permet le contournement. Réassignation possible si le rôle courant est membre de
-- loyertracker_batch ou superutilisateur (réserve R2 : provisionner cette appartenance en prod).
ALTER FUNCTION resolve_bailleur_id(text) OWNER TO loyertracker_batch;
ALTER FUNCTION resolve_invitation_bailleur(text) OWNER TO loyertracker_batch;
