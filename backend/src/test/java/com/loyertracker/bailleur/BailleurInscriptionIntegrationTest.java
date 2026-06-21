package com.loyertracker.bailleur;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valide l'inscription bailleur contre PostgreSQL + Flyway, avec le meme contrat de securite
 * que l'API runtime.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BailleurInscriptionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    // Datasource APPLICATIF (primaire) : doit etre le role restreint, pas le superutilisateur.
    @Autowired
    DataSource dataSource;

    /**
     * Verrou de fidelite RLS (ADR-01) : prouve que l'application se connecte sous le role restreint
     * {@code loyertracker_api} (NOSUPERUSER NOBYPASSRLS), donc que la RLS FORCE est reellement
     * exercee sur le chemin applicatif. Echoue si quelqu'un re-branche le datasource sur le
     * superutilisateur (regression silencieuse que ce lot vise precisement a empecher).
     */
    @Test
    void applicationConnecteeSousRoleRestreintNonBypassRls() {
        JdbcTemplate appJdbc = new JdbcTemplate(dataSource);
        String role = appJdbc.queryForObject("SELECT current_user", String.class);
        assertThat(role).isEqualTo("loyertracker_api");
        Boolean bypassRls = appJdbc.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        assertThat(bypassRls).as("le role applicatif ne doit pas contourner la RLS").isFalse();
        Boolean superUser = appJdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        assertThat(superUser).as("le role applicatif ne doit pas etre superutilisateur").isFalse();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Seule l'URL est dynamique : le datasource applicatif se connecte sous loyertracker_api
        // (identifiants statiques dans application.properties) ; Flyway migre en admin (creds fixes
        // du conteneur). On ne surcharge donc PLUS username/password ici.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://localhost/auth/realms/loyertracker");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/realms/loyertracker/protocol/openid-connect/certs");
    }

    @Test
    void inscriptionAvecRoleBailleurCreeLeCompteApplicatif() throws Exception {
        String keycloakId = "kc-" + UUID.randomUUID();
        String email = "bailleur-" + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nom").value("Durand"))
                .andExpect(jsonPath("$.prenom").value("Alice"));
    }

    @Test
    void inscriptionSansRoleBailleurEstRefusee() throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void inscriptionSansEmailEstRefusee() throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(jwt()
                        .jwt(token -> token
                                .subject("kc-" + UUID.randomUUID())
                                .claim("given_name", "Alice")
                                .claim("family_name", "Durand"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doubleInscriptionDuMemeCompteRenvoie409() throws Exception {
        String keycloakId = "kc-" + UUID.randomUUID();
        String email = "bailleur-" + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isConflict());
    }

    @Test
    void profilApresInscriptionRenvoieIdentiteSansAdresse() throws Exception {
        String keycloakId = "kc-" + UUID.randomUUID();
        String email = "bailleur-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/bailleurs/profil").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nom").value("Durand"))
                .andExpect(jsonPath("$.adresse").doesNotExist());
    }

    @Test
    void miseAJourAdresseProfilEstPersistee() throws Exception {
        String keycloakId = "kc-" + UUID.randomUUID();
        String email = "bailleur-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(keycloakId, email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"12 rue des Lilas, 75011 Paris\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresse").value("12 rue des Lilas, 75011 Paris"));

        // Relecture : l'adresse est bien persistée pour ce tenant.
        mockMvc.perform(get("/api/bailleurs/profil").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresse").value("12 rue des Lilas, 75011 Paris"));
    }

    @Test
    void miseAJourAdresseVideEstRefusee() throws Exception {
        String keycloakId = "kc-" + UUID.randomUUID();
        String email = "bailleur-" + UUID.randomUUID() + "@test.local";
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId, email)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(keycloakId, email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profilSansRoleBailleurEstRefuse() throws Exception {
        mockMvc.perform(get("/api/bailleurs/profil").with(jwt()))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor bailleurJwt(
            String keycloakId, String email) {
        return jwt()
                .jwt(token -> token
                        .subject(keycloakId)
                        .claim("email", email)
                        .claim("given_name", "Alice")
                        .claim("family_name", "Durand"))
                .authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"));
    }
}
