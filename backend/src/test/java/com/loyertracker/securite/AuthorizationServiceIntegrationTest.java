package com.loyertracker.securite;

import static org.assertj.core.api.Assertions.assertThat;

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
        UUID bienId = UUID.randomUUID();
        jdbc.update("INSERT INTO bien (id, bailleur_id, adresse, type) VALUES (?,?,?, 'APPARTEMENT')",
                bienId, bailleurId, "adresse-" + bienId);
        return bienId;
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
