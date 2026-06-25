package com.loyertracker.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import com.loyertracker.testsupport.RlsTestDataSourceConfig;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valide le moteur d'autorisation fine (US-13) contre PostgreSQL réel : prédicats de propriété et
 * d'affectation ACTIVE, et la décision {@code peutAccederBien} pour chaque rôle — objectif
 * <b>0 accès cross-bailleur / cross-affectation</b> (ENF-02, ADR-01/ADR-02).
 */
@SpringBootTest
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class AuthorizationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    AuthorizationService authz;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Seule l'URL est dynamique : datasource applicatif sous loyertracker_api (creds statiques
        // dans application.properties), Flyway en admin. On ne surcharge plus username/password.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://localhost/auth/realms/loyertracker");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/realms/loyertracker/protocol/openid-connect/certs");
    }

    @Test
    void prédicatsDePropriétéEtDAffectation() {
        Fixture f = seed();

        assertThat(authz.estBailleurProprietaire(f.bienId, f.bailleurId)).isTrue();
        assertThat(authz.estBailleurProprietaire(f.bienId, UUID.randomUUID())).isFalse();

        assertThat(authz.estGestionnaireAffecteActif(f.bienId, f.gestionnaireId)).isTrue();
        assertThat(authz.estGestionnaireAffecteActif(f.bienAutreId, f.gestionnaireId)).isFalse();
    }

    @Test
    void bailleurAccedeUniquementASesBiens() {
        Fixture f = seed();
        Authentication bailleur = jwtAuth(f.bailleurKc, "ROLE_BAILLEUR");

        assertThat(authz.peutAccederBien(f.bienId, bailleur)).isTrue();
        assertThat(authz.peutAccederBien(f.bienAutreId, bailleur)).isFalse(); // bien d'un autre bailleur
    }

    @Test
    void gestionnaireAccedeUniquementAuxBiensAffectesActifs() {
        Fixture f = seed();
        Authentication gestionnaire = jwtAuth(f.gestionnaireKc, "ROLE_GESTIONNAIRE");

        assertThat(authz.peutAccederBien(f.bienId, gestionnaire)).isTrue();       // affecté ACTIVE
        assertThat(authz.peutAccederBien(f.bienAutreId, gestionnaire)).isFalse(); // non affecté
    }

    @Test
    void jwtSansRoleMetierNAccedeARien() {
        Fixture f = seed();
        assertThat(authz.peutAccederBien(f.bienId, jwtAuth("kc-anon", "ROLE_USER"))).isFalse();
    }

    @Test
    void migrationV13AutoriseAffectationPatrimoineEtImposeUnSeulPerimetre() {
        UUID bailleurId = UUID.randomUUID();
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                bailleurId, "kc-" + bailleurId, bailleurId + "@test.local", "N", "P");
        UUID patrimoineId = insertPatrimoine(bailleurId);
        UUID bienId = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID gestionnaireId = UUID.randomUUID();
        jdbc.update("INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                gestionnaireId, "kc-g-" + gestionnaireId, gestionnaireId + "@test.local", "N", "P");

        assertThatCode(() -> jdbc.update("INSERT INTO affectation (id, bailleur_id, bien_id, patrimoine_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut) "
                        + "VALUES (?,?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE')",
                UUID.randomUUID(), bailleurId, null, patrimoineId, gestionnaireId))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO affectation (id, bailleur_id, bien_id, patrimoine_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut) "
                        + "VALUES (?,?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE')",
                UUID.randomUUID(), bailleurId, null, null, gestionnaireId))
                .hasMessageContaining("affectation_un_seul_perimetre");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO affectation (id, bailleur_id, bien_id, patrimoine_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut) "
                        + "VALUES (?,?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE')",
                UUID.randomUUID(), bailleurId, bienId, patrimoineId, gestionnaireId))
                .hasMessageContaining("affectation_un_seul_perimetre");

        UUID autreGestionnaireId = UUID.randomUUID();
        jdbc.update("INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                autreGestionnaireId, "kc-g-" + autreGestionnaireId, autreGestionnaireId + "@test.local", "N", "P");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO affectation (id, bailleur_id, bien_id, patrimoine_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut) "
                        + "VALUES (?,?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE')",
                UUID.randomUUID(), bailleurId, null, patrimoineId, autreGestionnaireId))
                .hasMessageContaining("uq_affectation_patrimoine_active");
    }

    /**
     * RM-98/US-85 — les 4 combinaisons de résolution (`securite-patrimoine.md` §5) : une
     * affectation bien ACTIVE court-circuite toujours l'héritage patrimoine, qu'elle soit
     * INCLUSION (redondante, tolérée) ou EXCLUSION (carve-out, bloquant) ; en son absence, l'accès
     * retombe sur l'affectation patrimoine héritée.
     */
    @Test
    void combinaisonPatrimoineSeulSansException() {
        UUID bailleurId = insertBailleur();
        UUID patrimoineId = insertPatrimoine(bailleurId);
        UUID bienA = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID bienB = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID gestionnaireId = insertGestionnaire();
        affecterPatrimoine(bailleurId, patrimoineId, gestionnaireId);

        assertThat(authz.estGestionnaireAffecteActif(bienA, gestionnaireId)).isTrue();
        assertThat(authz.estGestionnaireAffecteActif(bienB, gestionnaireId)).isTrue();
    }

    @Test
    void combinaisonPatrimoineEtInclusionRedondanteResteAutorise() {
        UUID bailleurId = insertBailleur();
        UUID patrimoineId = insertPatrimoine(bailleurId);
        UUID bienA = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID gestionnaireId = insertGestionnaire();
        affecterPatrimoine(bailleurId, patrimoineId, gestionnaireId);
        affecterBien(bailleurId, bienA, gestionnaireId, "INCLUSION");

        assertThat(authz.estGestionnaireAffecteActif(bienA, gestionnaireId)).isTrue();
    }

    @Test
    void combinaisonPatrimoineEtExclusionBloqueUniquementLeBienExclu() {
        UUID bailleurId = insertBailleur();
        UUID patrimoineId = insertPatrimoine(bailleurId);
        UUID bienExclu = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID bienAutre = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID gestionnaireId = insertGestionnaire();
        affecterPatrimoine(bailleurId, patrimoineId, gestionnaireId);
        affecterBien(bailleurId, bienExclu, gestionnaireId, "EXCLUSION");

        assertThat(authz.estGestionnaireAffecteActif(bienExclu, gestionnaireId))
                .as("l'exclusion bien court-circuite l'héritage patrimoine").isFalse();
        assertThat(authz.estGestionnaireAffecteActif(bienAutre, gestionnaireId))
                .as("les autres biens du patrimoine restent accessibles").isTrue();
    }

    @Test
    void combinaisonBienSeulSansPatrimoineAccesInchange() {
        UUID bailleurId = insertBailleur();
        UUID bienId = insertBien(bailleurId);
        UUID gestionnaireId = insertGestionnaire();
        affecterBien(bailleurId, bienId, gestionnaireId, "INCLUSION");

        assertThat(authz.estGestionnaireAffecteActif(bienId, gestionnaireId)).isTrue();
    }

    @Test
    void affectationPatrimoineRevoqueePerdAccesSaufInclusionBienIndependante() {
        UUID bailleurId = insertBailleur();
        UUID patrimoineId = insertPatrimoine(bailleurId);
        UUID bienHerite = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID bienInclus = insertBienDansPatrimoine(bailleurId, patrimoineId);
        UUID gestionnaireId = insertGestionnaire();
        UUID affectationPatrimoineId = affecterPatrimoine(bailleurId, patrimoineId, gestionnaireId);
        affecterBien(bailleurId, bienInclus, gestionnaireId, "INCLUSION");

        jdbc.update("UPDATE affectation SET statut = 'REVOQUEE' WHERE id = ?", affectationPatrimoineId);

        assertThat(authz.estGestionnaireAffecteActif(bienHerite, gestionnaireId))
                .as("héritage patrimoine perdu après révocation").isFalse();
        assertThat(authz.estGestionnaireAffecteActif(bienInclus, gestionnaireId))
                .as("inclusion bien indépendante conservée").isTrue();
    }

    // --- Fixture & helpers -----------------------------------------------------------

    private record Fixture(UUID bailleurId, String bailleurKc, UUID bienId, UUID bienAutreId,
            UUID gestionnaireId, String gestionnaireKc) {
    }

    /** Sème un bailleur (1 bien affecté + 1 bien d'un autre bailleur) et un gestionnaire affecté. */
    private Fixture seed() {
        UUID bailleurId = UUID.randomUUID();
        String bailleurKc = "kc-" + bailleurId;
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                bailleurId, bailleurKc, bailleurId + "@test.local", "N", "P");
        UUID bienId = insertBien(bailleurId);

        // Bien appartenant à un AUTRE bailleur (contrôle cross-bailleur).
        UUID autreBailleur = UUID.randomUUID();
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                autreBailleur, "kc-" + autreBailleur, autreBailleur + "@test.local", "N", "P");
        UUID bienAutreId = insertBien(autreBailleur);

        UUID gestionnaireId = UUID.randomUUID();
        String gestionnaireKc = "kc-g-" + gestionnaireId;
        jdbc.update("INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                gestionnaireId, gestionnaireKc, gestionnaireId + "@test.local", "N", "P");
        jdbc.update("INSERT INTO affectation (id, bailleur_id, bien_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut) "
                        + "VALUES (?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE')",
                UUID.randomUUID(), bailleurId, bienId, gestionnaireId);

        return new Fixture(bailleurId, bailleurKc, bienId, bienAutreId, gestionnaireId, gestionnaireKc);
    }

    private UUID insertBien(UUID bailleurId) {
        UUID patrimoineId = insertPatrimoine(bailleurId);
        return insertBienDansPatrimoine(bailleurId, patrimoineId);
    }

    private UUID insertPatrimoine(UUID bailleurId) {
        UUID patrimoineId = UUID.randomUUID();
        jdbc.update("INSERT INTO patrimoine (id, bailleur_id, nom) VALUES (?,?, 'Patrimoine test')",
                patrimoineId, bailleurId);
        return patrimoineId;
    }

    private UUID insertBienDansPatrimoine(UUID bailleurId, UUID patrimoineId) {
        UUID bienId = UUID.randomUUID();
        jdbc.update("INSERT INTO bien (id, bailleur_id, adresse, type, patrimoine_id) "
                        + "VALUES (?,?,?, 'APPARTEMENT', ?)",
                bienId, bailleurId, "adresse-" + bienId, patrimoineId);
        return bienId;
    }

    private UUID insertBailleur() {
        UUID bailleurId = UUID.randomUUID();
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                bailleurId, "kc-" + bailleurId, bailleurId + "@test.local", "N", "P");
        return bailleurId;
    }

    private UUID insertGestionnaire() {
        UUID gestionnaireId = UUID.randomUUID();
        jdbc.update("INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                gestionnaireId, "kc-g-" + gestionnaireId, gestionnaireId + "@test.local", "N", "P");
        return gestionnaireId;
    }

    /** Affectation patrimoine ACTIVE (US-84) ; retourne l'id pour permettre une révocation ciblée. */
    private UUID affecterPatrimoine(UUID bailleurId, UUID patrimoineId, UUID gestionnaireId) {
        UUID affectationId = UUID.randomUUID();
        jdbc.update("INSERT INTO affectation (id, bailleur_id, patrimoine_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut) "
                        + "VALUES (?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE')",
                affectationId, bailleurId, patrimoineId, gestionnaireId);
        return affectationId;
    }

    /** Affectation bien ACTIVE avec exception explicite (US-85, RM-98 niveau prioritaire). */
    private UUID affecterBien(UUID bailleurId, UUID bienId, UUID gestionnaireId, String typeException) {
        UUID affectationId = UUID.randomUUID();
        jdbc.update("INSERT INTO affectation (id, bailleur_id, bien_id, gestionnaire_id, "
                        + "type_honoraires, montant_honoraires, date_debut, statut, type_exception) "
                        + "VALUES (?,?,?,?, 'POURCENTAGE', 10, CURRENT_DATE, 'ACTIVE', ?)",
                affectationId, bailleurId, bienId, gestionnaireId, typeException);
        return affectationId;
    }

    private static Authentication jwtAuth(String keycloakId, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(keycloakId)
                .build();
        AbstractAuthenticationToken token = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority(role)));
        return token;
    }
}
