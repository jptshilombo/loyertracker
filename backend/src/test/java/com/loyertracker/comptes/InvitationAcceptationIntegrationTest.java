package com.loyertracker.comptes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import com.loyertracker.testsupport.RlsTestDataSourceConfig;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/**
 * Valide l'acceptation d'invitation (US-12) contre PostgreSQL + Flyway, avec un faux IdP en mémoire
 * (ADR-10) : création de compte, réutilisation multi-bailleur (EF-05), usage unique et rejets
 * (token inconnu, déjà utilisé, expiré).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class InvitationAcceptationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
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

    /** Double du port IdP : crée un id au 1ᵉʳ appel pour un e-mail, le réutilise ensuite (EF-05). */
    @TestConfiguration
    static class FakeIdpConfig {
        @Bean
        @Primary
        GestionnaireIdentityProvider fakeIdp() {
            ConcurrentMap<String, String> parEmail = new ConcurrentHashMap<>();
            return (email, nom, prenom, motDePasse) -> {
                String existant = parEmail.get(email);
                if (existant != null) {
                    return new GestionnaireIdentity(existant, false);
                }
                String id = "kc-g-" + UUID.randomUUID();
                parEmail.put(email, id);
                return new GestionnaireIdentity(id, true);
            };
        }
    }

    private static final String MDP = "MotDePasse12!";

    @Test
    void acceptationCreeLeCompteGestionnaireEtConsommeLInvitation() throws Exception {
        String token = inscrireEtInviter("kc-" + UUID.randomUUID(), "g1@test.local");

        // 1ʳᵉ acceptation : compte créé.
        mockMvc.perform(accepter(token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gestionnaireId").isNotEmpty())
                .andExpect(jsonPath("$.email").value("g1@test.local"))
                .andExpect(jsonPath("$.compteCree").value(true));

        // 2ᵉ acceptation du même token : usage unique → 409.
        mockMvc.perform(accepter(token))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptationTokenInconnuRenvoie404() throws Exception {
        mockMvc.perform(accepter(UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void memeGestionnaireInviteParDeuxBailleursEstReutilise() throws Exception {
        String tokenA = inscrireEtInviter("kc-" + UUID.randomUUID(), "partage@test.local");
        String tokenB = inscrireEtInviter("kc-" + UUID.randomUUID(), "partage@test.local");

        String idA = JsonPath.read(mockMvc.perform(accepter(tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compteCree").value(true))
                .andReturn().getResponse().getContentAsString(), "$.gestionnaireId");

        String idB = JsonPath.read(mockMvc.perform(accepter(tokenB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compteCree").value(false)) // réutilisation (EF-05)
                .andReturn().getResponse().getContentAsString(), "$.gestionnaireId");

        org.assertj.core.api.Assertions.assertThat(idB).isEqualTo(idA);
    }

    @Test
    void acceptationInvitationExpireeRenvoie409() throws Exception {
        UUID bailleurId = UUID.randomUUID();
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                bailleurId, "kc-" + bailleurId, "exp-" + bailleurId + "@test.local", "N", "P");
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO invitation (id, bailleur_id, email, token, statut, date_expiration) "
                        + "VALUES (?,?,?,?, 'PENDING', ?)",
                UUID.randomUUID(), bailleurId, "gexp@test.local", token,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        mockMvc.perform(accepter(token))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptationMotDePasseTropCourtRenvoie400() throws Exception {
        String token = inscrireEtInviter("kc-" + UUID.randomUUID(), "g2@test.local");
        mockMvc.perform(post("/api/invitations/{token}/acceptation", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Martin\",\"prenom\":\"Bob\",\"motDePasse\":\"court\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---------------------------------------------------------------------

    /** Inscrit un bailleur (subject donné) puis émet une invitation ; renvoie le token. */
    private String inscrireEtInviter(String keycloakId, String emailInvite) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
        String reponse = mockMvc.perform(post("/api/invitations")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailInvite + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.token");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder accepter(
            String token) {
        return post("/api/invitations/{token}/acceptation", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Martin\",\"prenom\":\"Bob\",\"motDePasse\":\"" + MDP + "\"}");
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
