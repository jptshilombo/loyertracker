-- =====================================================================================
-- LoyerTracker — Migration V5 : rôle applicatif à privilèges minimaux (réactivation RLS)
--
-- Constat (investigation 2026-06-08) : l'API se connectait en SUPERUTILISATEUR
-- (`POSTGRES_USER`), qui CONTOURNE la RLS même `FORCE` (ADR-01). La 2ᵉ couche de défense
-- en profondeur n'était donc pas opérante : seule l'autorisation applicative (prédicats
-- `bailleur_id` + ReBAC `@authz.peutAccederBien`) cloisonnait réellement.
--
-- Correctif : créer un rôle `loyertracker_api` LOGIN NOSUPERUSER NOBYPASSRLS, NON-propriétaire,
-- avec uniquement les privilèges DML + EXECUTE nécessaires. L'application s'y connecte au
-- runtime ; Flyway continue de migrer via un rôle administrateur distinct (cf. application.yml,
-- `spring.flyway.user`). Sous ce rôle, la RLS `FORCE` et le GUC `app.current_bailleur_id`
-- (positionné par TenantContext) redeviennent effectifs.
--
-- Secret : le mot de passe est injecté par placeholder Flyway `${api_password}` alimenté par
-- variable d'environnement (jamais versionné, cf. politique secrets du README / .env).
-- Le rôle `loyertracker_batch` (BYPASSRLS, V1) reste réservé aux jobs multi-bailleur.
-- =====================================================================================

-- --- 1. Rôle applicatif (idempotent) ------------------------------------------------
DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'loyertracker_api') THEN
        CREATE ROLE loyertracker_api LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE
            PASSWORD '${api_password}';
    ELSE
        ALTER ROLE loyertracker_api WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE
            PASSWORD '${api_password}';
    END IF;
END
$do$;

-- --- 2. Privilèges minimaux ---------------------------------------------------------
-- DML uniquement (jamais DDL ni TRUNCATE) : la RLS reste la barrière de cloisonnement.
GRANT USAGE ON SCHEMA public TO loyertracker_api;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO loyertracker_api;

-- EXECUTE sur les fonctions SECURITY DEFINER (résolution tenant / prédicats ReBAC, V2–V4) :
-- elles s'exécutent avec l'identité de leur propriétaire `loyertracker_batch` (BYPASSRLS) et
-- restent le seul chemin de contournement étroit et auditable (ADR-09).
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO loyertracker_api;

-- --- 3. Privilèges par défaut pour les tables/fonctions futures (S03+) ---------------
-- S'applique aux objets créés par le rôle administrateur qui exécute les migrations.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO loyertracker_api;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO loyertracker_api;
