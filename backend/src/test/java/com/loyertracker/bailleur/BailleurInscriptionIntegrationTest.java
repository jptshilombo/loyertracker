package com.loyertracker.bailleur;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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
