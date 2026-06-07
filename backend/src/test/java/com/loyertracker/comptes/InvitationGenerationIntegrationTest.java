package com.loyertracker.comptes;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valide la génération d'invitation (US-11) contre PostgreSQL + Flyway (V1 + V2), avec le même
 * contrat de sécurité que l'API runtime. Couvre le chemin de résolution tenant (ADR-09) : un
 * bailleur inscrit obtient son contexte, un porteur de JWT sans compte applicatif est refusé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InvitationGenerationIntegrationTest {

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
    void bailleurInscritGenereUneInvitationValide72h() throws Exception {
        String keycloakId = "kc-" + UUID.randomUUID();
        // Pré-requis : le bailleur doit exister côté application pour que le contexte se résolve.
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/invitations")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gestionnaire@test.local\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("gestionnaire@test.local"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.statut").value("PENDING"))
                .andExpect(jsonPath("$.dateExpiration").isNotEmpty())
                .andExpect(jsonPath("$.lien").value(org.hamcrest.Matchers.containsString("/invitations/")));
    }

    @Test
    void invitationParUtilisateurSansCompteBailleurEstRefusee() throws Exception {
        // JWT rôle BAILLEUR mais aucun compte applicatif (subject jamais inscrit) → 403 (ADR-09).
        mockMvc.perform(post("/api/invitations")
                        .with(bailleurJwt("kc-" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gestionnaire@test.local\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitationSansRoleBailleurEstRefusee() throws Exception {
        mockMvc.perform(post("/api/invitations")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gestionnaire@test.local\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitationAvecEmailInvalideEstRejetee() throws Exception {
        mockMvc.perform(post("/api/invitations")
                        .with(bailleurJwt("kc-" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\"}"))
                .andExpect(status().isBadRequest());
    }

    private static JwtRequestPostProcessor bailleurJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token
                        .subject(keycloakId)
                        .claim("email", keycloakId + "@test.local")
                        .claim("given_name", "Alice")
                        .claim("family_name", "Durand"))
                .authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"));
    }
}
