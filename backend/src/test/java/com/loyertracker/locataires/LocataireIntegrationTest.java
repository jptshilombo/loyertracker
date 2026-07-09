package com.loyertracker.locataires;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.loyertracker.testsupport.RlsTestDataSourceConfig;

/**
 * Tests d'intégration EP-15 Sprint B (US-109→112) : CRUD Locataire, cloisonnement RLS
 * cross-bailleur, cycle de vie (archivage sans pré-condition, restauration), recherche
 * multicritère, détection de doublons, historique (audit RLS-scopé).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class LocataireIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("TRUNCATE audit_log, locataire, bailleur RESTART IDENTITY CASCADE");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://localhost/auth/realms/loyertracker");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/realms/loyertracker/protocol/openid-connect/certs");
    }

    @Test
    void locataireCrudCreerModifierArchiverRestaurer() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);

        String id = JsonPath.read(mockMvc.perform(post("/api/locataires")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Kabongo\",\"prenom\":\"Alice\",\"telephone\":\"+243900000001\","
                                + "\"email\":\"alice@test.local\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Kabongo"))
                .andExpect(jsonPath("$.statut").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/locataires/{id}", id)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Kabongo\",\"prenom\":\"Alice\",\"telephone\":\"+243900000002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("+243900000002"));

        mockMvc.perform(delete("/api/locataires/{id}", id).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));

        mockMvc.perform(post("/api/locataires/{id}/restauration", id).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        mockMvc.perform(get("/api/locataires/{id}/historique", id).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit.length()").value(4)) // creer/modifier/archiver/restaurer
                .andExpect(jsonPath("$.audit[?(@.action == 'RESTAURER_LOCATAIRE')]").exists());
    }

    @Test
    void archivageSansPreConditionEtIdempotenceRejetee() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String id = creerLocataire(bailleur, "Mputu", "Jean");

        mockMvc.perform(delete("/api/locataires/{id}", id).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk());

        // Un locataire déjà archivé ne peut pas être archivé de nouveau.
        mockMvc.perform(delete("/api/locataires/{id}", id).with(bailleurJwt(bailleur)))
                .andExpect(status().isConflict());
    }

    @Test
    void isolationRlsCrossBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String idA = creerLocataire(bailleurA, "Privé", "DeA");

        mockMvc.perform(get("/api/locataires/{id}", idA).with(bailleurJwt(bailleurB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/locataires/{id}", idA)
                        .with(bailleurJwt(bailleurB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"hack\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/locataires").with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/locataires").with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rechercheMulticritereEtDetectionDoublon() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        creerLocataireComplet(bailleur, "Tshibola", "Annie", "+243911111111", "annie@test.local", "CD1234567");

        mockMvc.perform(get("/api/locataires").param("q", "tshibola").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/locataires").param("q", "CD1234567").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/locataires/verification-doublon")
                        .param("numeroPieceIdentite", "CD1234567").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/locataires/verification-doublon")
                        .param("email", "inconnu@test.local").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rbacGestionnaireRefuse() throws Exception {
        mockMvc.perform(get("/api/locataires")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))))
                .andExpect(status().isForbidden());
    }

    // ---- helpers --------------------------------------------------------------------

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerLocataire(String bailleurKeycloakId, String nom, String prenom) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/locataires")
                        .with(bailleurJwt(bailleurKeycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\",\"prenom\":\"" + prenom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void creerLocataireComplet(String bailleurKeycloakId, String nom, String prenom,
            String telephone, String email, String numeroPieceIdentite) throws Exception {
        mockMvc.perform(post("/api/locataires")
                        .with(bailleurJwt(bailleurKeycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\",\"prenom\":\"" + prenom + "\","
                                + "\"telephone\":\"" + telephone + "\",\"email\":\"" + email + "\","
                                + "\"numeroPieceIdentite\":\"" + numeroPieceIdentite + "\"}"))
                .andExpect(status().isCreated());
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
