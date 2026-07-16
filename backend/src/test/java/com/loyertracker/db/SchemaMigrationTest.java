package com.loyertracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valide la migration Flyway V1 contre un vrai PostgreSQL 16 (Testcontainers), hors contexte
 * Spring : application effective de la migration, présence des tables et des index uniques
 * partiels, activation de la RLS, idempotence du re-run, et enfin <b>preuve fonctionnelle</b>
 * du cloisonnement RLS (un rôle restreint ne voit que les lignes de son bailleur courant).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaMigrationTest {

    private final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway flyway;

    @BeforeAll
    void migrate() {
        postgres.start();
        flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                // V5 crée le rôle applicatif via un placeholder de mot de passe (cf. application.yml).
                .placeholders(Map.of("api_password", "loyertracker_api_test"))
                .load();
        MigrateResult result = flyway.migrate();
        // V1 (schéma US-03) + V2 (résolution tenant) + V3 (prédicats d'autorisation)
        // + V4 (helpers S02 biens/baux/affectations) + V5 (rôle applicatif RLS)
        // + V6 (génération échéances loyers S03) + V7 (passage EN_RETARD S03)
        // + V8 (calcul honoraires S04) + V9 (génération alertes S04).
        // + V10 (alerte PREAVIS S04).
        // + V11 (ventilation loyer HC/charges + adresse bailleur — socle quittances).
        // + V12 (Patrimoine + TypeBien administrable — US-80/81/82).
        // + V13 (affectations au périmètre patrimoine — Sprint 2 Patrimoine).
        // + V14 (honoraires sur affectations patrimoine — Sprint 2 Patrimoine).
        // + V15 (exceptions fines par bien, résolution à priorité — Sprint 3 Patrimoine, US-85).
        // + V16 (bien.statut LOUE rétroactif + patrimoine.adresse — Sprint 5 B1/B2).
        // + V17 (bail.devise EUR/USD/CDF — Sprint 5 B3).
        // + V18 (StatutPaiement A_VENIR + génération échéances futures — Sprint 5 B4, US-60).
        // + V19 (patrimoine enrichi : champs additionnels + adresse obligatoire — Sprint 7, US-90).
        // + V20 (garantie_movement ledger + bail.depot_garantie supprimée — Sprint 9, US-94).
        // + V21 (paiement.garantie_movement_id — Sprint 10, US-95).
        // + V22 (quittances certifiées : quittance + numérotation + journal — Sprint 11, US-99).
        // + V23 (cycle de vie Gestionnaire : statut global + fonctions cross-tenant — EP-15 Sprint A).
        // + V24 (entité Locataire + bail.locataire_id préparatoire — EP-15 Sprint B).
        // + V25 (clôture/réouverture de bail, purge échéancier, non-régression alertes — EP-13).
        assertThat(result.migrationsExecuted).isEqualTo(25);
        assertThat(result.success).isTrue();
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @Test
    void toutesLesTablesMetierExistent() throws SQLException {
        String[] tables = {
            "bailleur", "gestionnaire", "invitation", "bien", "bail", "affectation",
            "paiement", "garantie", "honoraire", "alerte", "audit_log", "patrimoine", "type_bien",
            "quittance", "quittance_numerotation", "quittance_verification_log",
            "locataire", "flyway_schema_history"
        };
        try (Connection c = connect()) {
            for (String table : tables) {
                assertThat(tableExists(c, table)).as("table %s", table).isTrue();
            }
        }
    }

    @Test
    void lesSixIndexUniquesPartielsSontCrees() throws SQLException {
        String[] index = {
            "uq_bail_actif", "uq_affectation_active", "uq_affectation_patrimoine_active",
            "uq_paiement_periode", "uq_honoraire_periode", "uq_alerte_nonlue"
        };
        try (Connection c = connect()) {
            for (String idx : index) {
                assertThat(indexExists(c, idx)).as("index %s", idx).isTrue();
            }
        }
    }

    @Test
    void rlsActiveEtForceeSurBien() throws SQLException {
        try (Connection c = connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT relrowsecurity, relforcerowsecurity "
                             + "FROM pg_class WHERE relname = 'bien'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("relrowsecurity")).as("ENABLE RLS").isTrue();
            assertThat(rs.getBoolean("relforcerowsecurity")).as("FORCE RLS").isTrue();
        }
    }

    @Test
    void rlsActiveEtForceeSurPatrimoine() throws SQLException {
        try (Connection c = connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT relrowsecurity, relforcerowsecurity "
                             + "FROM pg_class WHERE relname = 'patrimoine'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("relrowsecurity")).as("ENABLE RLS").isTrue();
            assertThat(rs.getBoolean("relforcerowsecurity")).as("FORCE RLS").isTrue();
        }
    }

    @Test
    void roleBatchExisteAvecBypassRls() throws SQLException {
        try (Connection c = connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT rolbypassrls FROM pg_roles WHERE rolname = 'loyertracker_batch'")) {
            assertThat(rs.next()).as("rôle loyertracker_batch présent").isTrue();
            assertThat(rs.getBoolean("rolbypassrls")).isTrue();
        }
    }

    @Test
    void resolveBailleurIdContourneLaRlsViaSecurityDefiner() throws SQLException {
        // ADR-09 : la résolution keycloak_id → bailleur_id doit aboutir SANS contexte tenant et
        // sans droit de lecture direct sur `bailleur` — c'est tout l'intérêt du SECURITY DEFINER.
        UUID bailleurId = UUID.randomUUID();
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            seedBailleurEtBien(c, bailleurId, "definer@test.local", "3 rue C");

            try (Statement s = c.createStatement()) {
                s.execute("DROP ROLE IF EXISTS app_user_definer");
                s.execute("CREATE ROLE app_user_definer NOLOGIN");
                s.execute("GRANT USAGE ON SCHEMA public TO app_user_definer");
                // Volontairement AUCUN GRANT SELECT sur bailleur : seule la fonction y donne accès.
            }

            try (Statement s = c.createStatement()) {
                s.execute("RESET app.current_bailleur_id"); // fail-closed pour un SELECT direct
                s.execute("SET ROLE app_user_definer");
                try (ResultSet rs = s.executeQuery(
                        "SELECT resolve_bailleur_id('kc-" + bailleurId + "')")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject(1, UUID.class)).isEqualTo(bailleurId);
                }
                s.execute("RESET ROLE");
            }
            c.rollback();
        }
    }

    @Test
    void reRunEstIdempotent() {
        // 2ᵉ migrate() : rien à appliquer (« Schema is up to date »).
        MigrateResult again = flyway.migrate();
        assertThat(again.migrationsExecuted).isZero();
    }

    @Test
    void rlsCloisonneEffectivementParBailleur() throws SQLException {
        UUID bailleurA = UUID.randomUUID();
        UUID bailleurB = UUID.randomUUID();

        try (Connection c = connect()) {
            c.setAutoCommit(false);
            // Données de deux bailleurs distincts (insérées en superutilisateur, RLS contournée).
            seedBailleurEtBien(c, bailleurA, "a@test.local", "1 rue A");
            seedBailleurEtBien(c, bailleurB, "b@test.local", "2 rue B");

            // Rôle applicatif restreint, non-propriétaire, sans BYPASSRLS : la RLS s'applique.
            try (Statement s = c.createStatement()) {
                s.execute("DROP ROLE IF EXISTS app_user_test");
                s.execute("CREATE ROLE app_user_test NOLOGIN");
                s.execute("GRANT USAGE ON SCHEMA public TO app_user_test");
                s.execute("GRANT SELECT ON bien, bailleur TO app_user_test");
            }

            // Sous l'identité du bailleur A : seul son bien est visible.
            assertThat(countBiensVu(c, "app_user_test", bailleurA)).isEqualTo(1);
            // Sous l'identité du bailleur B : un seul bien aussi, et pas celui de A.
            assertThat(countBiensVu(c, "app_user_test", bailleurB)).isEqualTo(1);
            // Sans GUC positionné : fail-closed, aucune ligne.
            assertThat(countBiensVu(c, "app_user_test", null)).isZero();

            c.rollback();
        }
    }

    @Test
    void roleApiExisteSansSuperuserNiBypassRls() throws SQLException {
        try (Connection c = connect();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT rolcanlogin, rolsuper, rolbypassrls "
                                + "FROM pg_roles WHERE rolname = 'loyertracker_api'")) {
            assertThat(rs.next()).as("rôle loyertracker_api créé par V5").isTrue();
            assertThat(rs.getBoolean("rolcanlogin")).as("LOGIN").isTrue();
            assertThat(rs.getBoolean("rolsuper")).as("NOSUPERUSER").isFalse();
            assertThat(rs.getBoolean("rolbypassrls")).as("NOBYPASSRLS").isFalse();
        }
    }

    /**
     * Preuve que la RLS, sous le rôle applicatif réel (loyertracker_api, sans BYPASSRLS), aurait
     * bloqué la fuite cross-bailleur de {@code revoquer} : la révocation (UPDATE) d'une affectation
     * d'un autre tenant n'affecte aucune ligne et celle-ci reste ACTIVE, tandis que le tenant
     * propriétaire révoque bien la sienne (contrôle positif).
     */
    @Test
    void rlsBloqueRevocationAffectationCrossTenantSousRoleApi() throws SQLException {
        UUID bailleurA = UUID.randomUUID();
        UUID bailleurB = UUID.randomUUID();

        try (Connection c = connect()) {
            c.setAutoCommit(false);
            // Seed des deux tenants en superutilisateur (RLS contournée).
            seedBailleurEtBien(c, bailleurA, "a-aff@test.local", "1 rue A");
            seedBailleurEtBien(c, bailleurB, "b-aff@test.local", "2 rue B");
            UUID gestionnaire = seedGestionnaire(c, "kc-g-aff", "g-aff@test.local");
            UUID affectationA =
                    seedAffectationActive(c, bailleurA, bienIdDe(c, bailleurA), gestionnaire);
            UUID affectationB =
                    seedAffectationActive(c, bailleurB, bienIdDe(c, bailleurB), gestionnaire);

            // Contexte tenant A via set_config paramétré (pas de SQL concaténé).
            positionnerTenant(c, bailleurA);
            try (Statement role = c.createStatement()) {
                role.execute("SET ROLE loyertracker_api");

                try (Statement s = c.createStatement();
                        ResultSet rs = s.executeQuery("SELECT count(*) FROM affectation")) {
                    rs.next();
                    assertThat(rs.getInt(1)).as("affectations visibles sous tenant A").isEqualTo(1);
                }
                // Révocation cross-tenant (affectation de B) : invisible → 0 ligne touchée.
                assertThat(revoquer(c, affectationB))
                        .as("révocation cross-tenant bloquée par la RLS").isZero();
                // Contrôle positif : le tenant A révoque bien sa propre affectation.
                assertThat(revoquer(c, affectationA))
                        .as("révocation de sa propre affectation autorisée").isEqualTo(1);

                role.execute("RESET ROLE");
            }

            // L'affectation de B est restée ACTIVE (vérifié en superutilisateur).
            try (PreparedStatement ps =
                    c.prepareStatement("SELECT statut FROM affectation WHERE id = ?")) {
                ps.setObject(1, affectationB);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getString(1)).isEqualTo("ACTIVE");
                }
            }

            c.rollback();
        }
    }

    // --- Helpers ---------------------------------------------------------------------

    private Connection connect() throws SQLException {
        return postgres.createConnection("");
    }

    private boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean indexExists(Connection c, String index) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?")) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void seedBailleurEtBien(Connection c, UUID bailleurId, String email, String adresse)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?, ?, ?, 'N', 'P')")) {
            ps.setObject(1, bailleurId);
            ps.setString(2, "kc-" + bailleurId);
            ps.setString(3, email);
            ps.executeUpdate();
        }
        UUID patrimoineId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO patrimoine (bailleur_id, nom, adresse) VALUES (?, 'Patrimoine test', '1 rue Test') RETURNING id")) {
            ps.setObject(1, bailleurId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                patrimoineId = rs.getObject(1, UUID.class);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bien (bailleur_id, adresse, type, patrimoine_id) "
                        + "VALUES (?, ?, 'APPARTEMENT', ?)")) {
            ps.setObject(1, bailleurId);
            ps.setString(2, adresse);
            ps.setObject(3, patrimoineId);
            ps.executeUpdate();
        }
    }

    private UUID seedGestionnaire(Connection c, String keycloakId, String email) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,'N','P')")) {
            ps.setObject(1, id);
            ps.setString(2, keycloakId);
            ps.setString(3, email);
            ps.executeUpdate();
        }
        return id;
    }

    private UUID bienIdDe(Connection c, UUID bailleurId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM bien WHERE bailleur_id = ?")) {
            ps.setObject(1, bailleurId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private UUID seedAffectationActive(Connection c, UUID bailleurId, UUID bienId, UUID gestionnaireId)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO affectation (id, bailleur_id, bien_id, gestionnaire_id, type_honoraires, "
                        + "montant_honoraires, date_debut) "
                        + "VALUES (?,?,?,?, 'POURCENTAGE', 10.00, '2026-06-01')")) {
            ps.setObject(1, id);
            ps.setObject(2, bailleurId);
            ps.setObject(3, bienId);
            ps.setObject(4, gestionnaireId);
            ps.executeUpdate();
        }
        return id;
    }

    /** Positionne le contexte tenant (GUC de session) via set_config paramétré. */
    private void positionnerTenant(Connection c, UUID bailleurId) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("SELECT set_config('app.current_bailleur_id', ?, false)")) {
            ps.setString(1, bailleurId.toString());
            ps.executeQuery();
        }
    }

    /** Révoque une affectation par id (UPDATE paramétré) ; renvoie le nombre de lignes touchées. */
    private int revoquer(Connection c, UUID affectationId) throws SQLException {
        try (PreparedStatement ps =
                c.prepareStatement("UPDATE affectation SET statut = 'REVOQUEE' WHERE id = ?")) {
            ps.setObject(1, affectationId);
            return ps.executeUpdate();
        }
    }

    /** Compte les biens visibles sous le rôle restreint et le bailleur courant donné (NULL = aucun). */
    private int countBiensVu(Connection c, String role, UUID bailleurCourant) throws SQLException {
        try (Statement s = c.createStatement()) {
            if (bailleurCourant != null) {
                positionnerTenant(c, bailleurCourant);
            } else {
                s.execute("RESET app.current_bailleur_id");
            }
            s.execute("SET ROLE " + role);
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM bien")) {
                rs.next();
                int count = rs.getInt(1);
                s.execute("RESET ROLE");
                return count;
            }
        }
    }
}
