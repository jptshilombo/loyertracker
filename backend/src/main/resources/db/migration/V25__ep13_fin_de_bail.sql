-- =====================================================================================
-- LoyerTracker — Migration V25 : Fin de bail (EP-13, US-115→118, ADR-17)
-- Sprint unique EP-13 — additive uniquement, rollback applicatif seul.
--
-- a) bail.date_cloture_effective (K2) : date de clôture réelle, distincte de date_fin
--    (contractuelle, jamais réécrite). Nullable, renseignée uniquement à la clôture manuelle
--    (US-115), remise à NULL en cas de réouverture (US-116). Le CHECK sur bail.statut autorise
--    déjà 'CLOS' depuis V1 — aucune contrainte supplémentaire nécessaire.
-- b) generer_alertes() (US-118) : la CTE LOYER_EN_RETARD ne filtrait jusqu'ici que sur
--    paiement.statut, sans jointure vers bail — un bail CLOS pouvait donc continuer à générer des
--    alertes LOYER_EN_RETARD indéfiniment pour les paiements non purgés (RECU/PARTIEL/EN_RETARD/
--    IMPAYE, jamais supprimés par la purge US-117 qui ne touche que les A_VENIR). Alignée sur
--    FIN_BAIL/PREAVIS, qui filtrent déjà bail.statut = 'ACTIF' depuis V10. GARANTIE_NON_RESTITUEE
--    n'est pas touchée (filtre intentionnellement l'inverse : bail.statut = 'CLOS').
-- =====================================================================================

ALTER TABLE bail ADD COLUMN date_cloture_effective DATE;

COMMENT ON COLUMN bail.date_cloture_effective IS
    'Date de clôture effective (US-115, ADR-17 K2) : renseignée uniquement à la clôture manuelle '
    '(ACTIF -> CLOS), remise à NULL en cas de réouverture (US-116). Distincte de date_fin '
    '(contractuelle, jamais réécrite) : les deux peuvent légitimement diverger.';

CREATE OR REPLACE FUNCTION generer_alertes(p_preavis_jours integer DEFAULT 90)
    RETURNS integer
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public
AS $$
DECLARE
    total integer := 0;
    n     integer;
BEGIN
    -- LOYER_EN_RETARD (EF-60) : un loyer marqué EN_RETARD par V7, sur un bail encore ACTIF
    -- (V25/US-118 : un bail CLOS n'émet plus jamais cette alerte).
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT p.bailleur_id, p.bailleur_id, 'LOYER_EN_RETARD', p.bien_id, p.bail_id, p.periode,
               'Loyer ' || p.periode || ' en retard de paiement.'
        FROM paiement p
        JOIN bail b ON b.id = p.bail_id
        WHERE p.statut = 'EN_RETARD'
          AND b.statut = 'ACTIF'
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    -- FIN_BAIL (EF-61) : bail ACTIF dont le terme est atteint sous 60 jours. periode = mois du terme.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT b.bailleur_id, b.bailleur_id, 'FIN_BAIL', b.bien_id, b.id,
               to_char(b.date_fin, 'YYYY-MM'),
               'Fin de bail prévue le ' || to_char(b.date_fin, 'YYYY-MM-DD') || '.'
        FROM bail b
        WHERE b.statut = 'ACTIF'
          AND b.date_fin IS NOT NULL
          AND b.date_fin <= current_date + 60
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    -- PREAVIS (EF-62) : bail ACTIF dont le terme entre dans la bande de préavis ]J+60 ; J+preavis].
    -- Borne basse > J+60 : exclusion mutuelle avec FIN_BAIL. periode = mois du terme.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT b.bailleur_id, b.bailleur_id, 'PREAVIS', b.bien_id, b.id,
               to_char(b.date_fin, 'YYYY-MM'),
               'Échéance de préavis : fin de bail prévue le ' || to_char(b.date_fin, 'YYYY-MM-DD') || '.'
        FROM bail b
        WHERE b.statut = 'ACTIF'
          AND b.date_fin IS NOT NULL
          AND b.date_fin > current_date + 60
          AND b.date_fin <= current_date + p_preavis_jours
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    -- GARANTIE_NON_RESTITUEE (EF-63) : garantie DETENU > 30 jours après la fin d'un bail CLOS.
    WITH ins AS (
        INSERT INTO alerte (bailleur_id, destinataire_id, type, bien_id, bail_id, periode, message)
        SELECT g.bailleur_id, g.bailleur_id, 'GARANTIE_NON_RESTITUEE', b.bien_id, g.bail_id,
               to_char(b.date_fin, 'YYYY-MM'),
               'Garantie non restituée plus de 30 jours après la fin du bail.'
        FROM garantie g
        JOIN bail b ON b.id = g.bail_id
        WHERE g.statut = 'DETENU'
          AND b.statut = 'CLOS'
          AND b.date_fin IS NOT NULL
          AND b.date_fin < current_date - 30
        ON CONFLICT (type, bien_id, periode) WHERE statut = 'NON_LUE' DO NOTHING
        RETURNING 1)
    SELECT count(*) INTO n FROM ins;
    total := total + n;

    RETURN total;
END;
$$;

COMMENT ON FUNCTION generer_alertes(integer) IS
    'US-50/51/EF-60/61/62/63/65/A.7 : génère (idempotent) les alertes LOYER_EN_RETARD, FIN_BAIL, PREAVIS (bande ]J+60 ; J+p_preavis_jours]) et GARANTIE_NON_RESTITUEE pour tous les bailleurs. SECURITY DEFINER (owner BYPASSRLS loyertracker_batch). Retourne le nombre d''alertes créées. V25/EP-13/US-118 : LOYER_EN_RETARD désormais restreinte aux baux ACTIF.';

ALTER FUNCTION generer_alertes(integer) OWNER TO loyertracker_batch;
GRANT EXECUTE ON FUNCTION generer_alertes(integer) TO loyertracker_api;
