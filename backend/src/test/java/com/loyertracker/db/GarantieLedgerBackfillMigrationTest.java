package com.loyertracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Vérifie le backfill rétroactif de la migration V20 (Sprint 9, ADR-14 §4) : trois garanties
 * pré-existantes (une par état — {@code DETENU}, {@code RESTITUE_PARTIEL}, {@code RESTITUE_TOTAL})
 * sont insérées <b>avant</b> V20 (migration arrêtée à V19), puis la migration est menée à son terme.
 * Critère GO du Plan d'Exécution : "100 % des garanties existantes ont un historique de mouvements
 * cohérent (solde recalculé == montant - montant_retenu actuel)".
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GarantieLedgerBackfillMigrationTest {

    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private UUID bailleurId;
    private UUID garantieDetenue;
    private UUID garantiePartielle;
    private UUID garantieTotale;

    @BeforeAll
    void migrerJusquaV19PuisInsererEtTerminerLaMigration() throws SQLException {
        postgres.start();

        // 1. Migration arrêtée à V19 (état juste avant l'introduction du ledger).
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("api_password", "loyertracker_api_test"))
                .target("19")
                .load()
                .migrate();

        // 2. Insertion de 3 garanties « historiques » représentant les 3 états métier possibles.
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword())) {
            bailleurId = UUID.randomUUID();
            seedBailleurBienBail(c, bailleurId);

            garantieDetenue = seedGarantie(c, bailleurId, new BigDecimal("800.00"), "DETENU",
                    BigDecimal.ZERO, null);
            garantiePartielle = seedGarantie(c, bailleurId, new BigDecimal("900.00"),
                    "RESTITUE_PARTIEL", new BigDecimal("200.00"), "Dégâts constatés");
            garantieTotale = seedGarantie(c, bailleurId, new BigDecimal("500.00"), "RESTITUE_TOTAL",
                    BigDecimal.ZERO, null);
        }

        // 3. Migration menée à son terme (V20 inclus) : déclenche le backfill rétroactif.
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("api_password", "loyertracker_api_test"))
                .load()
                .migrate();
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @Test
    void garantieDetenueALeSoldeEgalAuMontantEtUnSeulMouvement() throws SQLException {
        assertThat(soldeActuel(garantieDetenue)).isEqualByComparingTo("800.00");
        assertThat(sommeParType(garantieDetenue, "DEPOT_INITIAL", "credit")).isEqualByComparingTo("800.00");
        assertThat(nombreMouvements(garantieDetenue)).isEqualTo(1);
    }

    @Test
    void garantiePartielleALeSoldeEgalAuMontantMoinsLaRetenue() throws SQLException {
        // 900 (DEPOT_INITIAL) - 200 (AJUSTEMENT, retenue historique) = 700.
        assertThat(soldeActuel(garantiePartielle)).isEqualByComparingTo("700.00");
        assertThat(sommeParType(garantiePartielle, "DEPOT_INITIAL", "credit")).isEqualByComparingTo("900.00");
        assertThat(sommeParType(garantiePartielle, "AJUSTEMENT", "debit")).isEqualByComparingTo("200.00");
        assertThat(nombreMouvements(garantiePartielle)).isEqualTo(2);
    }

    @Test
    void garantieTotaleALeSoldeRameneAZero() throws SQLException {
        // 500 (DEPOT_INITIAL) - 500 (RESTITUTION, dossier clos) = 0 ; aucune retenue historique ici.
        assertThat(soldeActuel(garantieTotale)).isEqualByComparingTo("0.00");
        assertThat(sommeParType(garantieTotale, "DEPOT_INITIAL", "credit")).isEqualByComparingTo("500.00");
        assertThat(sommeParType(garantieTotale, "RESTITUTION", "debit")).isEqualByComparingTo("500.00");
        assertThat(nombreMouvements(garantieTotale)).isEqualTo(2);
    }

    @Test
    void invariantLedgerVerifiePourLesTroisGaranties() throws SQLException {
        for (UUID id : new UUID[] {garantieDetenue, garantiePartielle, garantieTotale}) {
            BigDecimal solde = soldeActuel(id);
            BigDecimal sommeCredit = sommeTous(id, "credit");
            BigDecimal sommeDebit = sommeTous(id, "debit");
            assertThat(sommeCredit.subtract(sommeDebit))
                    .as("invariant solde = somme(credit) - somme(debit) pour %s", id)
                    .isEqualByComparingTo(solde);
        }
    }

    @Test
    void bailDepotGarantieColonneSupprimee() throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_name = 'bail' AND column_name = 'depot_garantie'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("bail.depot_garantie doit être supprimée par V20").isFalse();
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------

    private void seedBailleurBienBail(Connection c, UUID bailleurId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,'N','P')")) {
            ps.setObject(1, bailleurId);
            ps.setString(2, "kc-" + bailleurId);
            ps.setString(3, bailleurId + "@test.local");
            ps.executeUpdate();
        }
        UUID patrimoineId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO patrimoine (bailleur_id, nom, adresse) VALUES (?, 'Patrimoine test', "
                        + "'1 rue Test') RETURNING id")) {
            ps.setObject(1, bailleurId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                patrimoineId = rs.getObject(1, UUID.class);
            }
        }
        UUID bienId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bien (id, bailleur_id, adresse, type, patrimoine_id) "
                        + "VALUES (?, ?, '2 rue Ledger', 'APPARTEMENT', ?)")) {
            ps.setObject(1, bienId);
            ps.setObject(2, bailleurId);
            ps.setObject(3, patrimoineId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bail (id, bailleur_id, bien_id, locataire_nom, loyer_hc, "
                        + "provision_charges, loyer_cc, date_debut, devise) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'Locataire Historique', 850.00, 0.00, "
                        + "850.00, '2025-01-01', 'EUR')")) {
            ps.setObject(1, bailleurId);
            ps.setObject(2, bienId);
            ps.executeUpdate();
        }
    }

    private UUID seedGarantie(Connection c, UUID bailleurId, BigDecimal montant, String statut,
            BigDecimal montantRetenu, String motifRetenue) throws SQLException {
        UUID bailId;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM bail WHERE bailleur_id = ? ORDER BY date_debut DESC LIMIT 1")) {
            ps.setObject(1, bailleurId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                bailId = rs.getObject(1, UUID.class);
            }
        }
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO garantie (id, bailleur_id, bail_id, montant, type_garantie, date_depot, "
                        + "statut, montant_retenu, motif_retenue) "
                        + "VALUES (?, ?, ?, ?, 'CAUTION', '2025-01-01', ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, bailleurId);
            ps.setObject(3, bailId);
            ps.setBigDecimal(4, montant);
            ps.setString(5, statut);
            ps.setBigDecimal(6, montantRetenu);
            ps.setString(7, motifRetenue);
            ps.executeUpdate();
        }
        return id;
    }

    private BigDecimal soldeActuel(UUID garantieId) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT solde_actuel FROM garantie WHERE id = ?")) {
            ps.setObject(1, garantieId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal sommeParType(UUID garantieId, String type, String colonne) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT coalesce(sum(" + colonne + "),0) FROM garantie_movement "
                                + "WHERE garantie_id = ? AND type = ?")) {
            ps.setObject(1, garantieId);
            ps.setString(2, type);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal sommeTous(UUID garantieId, String colonne) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT coalesce(sum(" + colonne + "),0) FROM garantie_movement "
                                + "WHERE garantie_id = ?")) {
            ps.setObject(1, garantieId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private int nombreMouvements(UUID garantieId) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM garantie_movement WHERE garantie_id = ?")) {
            ps.setObject(1, garantieId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
