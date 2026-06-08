package com.loyertracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
                .load();
        MigrateResult result = flyway.migrate();
        // V1 (schéma US-03) + V2 (résolution tenant) + V3 (prédicats d'autorisation)
        // + V4 (helpers S02 biens/baux/affectations).
        assertThat(result.migrationsExecuted).isEqualTo(4);
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
            "paiement", "garantie", "honoraire", "alerte", "audit_log", "flyway_schema_history"
        };
        try (Connection c = connect()) {
            for (String table : tables) {
                assertThat(tableExists(c, table)).as("table %s", table).isTrue();
            }
        }
    }

    @Test
    void lesCinqIndexUniquesPartielsSontCrees() throws SQLException {
        String[] index = {
            "uq_bail_actif", "uq_affectation_active", "uq_paiement_periode",
            "uq_honoraire_periode", "uq_alerte_nonlue"
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
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bien (bailleur_id, adresse, type) VALUES (?, ?, 'APPARTEMENT')")) {
            ps.setObject(1, bailleurId);
            ps.setString(2, adresse);
            ps.executeUpdate();
        }
    }

    /** Compte les biens visibles sous le rôle restreint et le bailleur courant donné (NULL = aucun). */
    private int countBiensVu(Connection c, String role, UUID bailleurCourant) throws SQLException {
        try (Statement s = c.createStatement()) {
            if (bailleurCourant != null) {
                s.execute("SET app.current_bailleur_id = '" + bailleurCourant + "'");
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
