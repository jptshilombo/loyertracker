package com.loyertracker.patrimoine;

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
 * Tests d'intégration Patrimoine (US-80) & TypeBien (US-81) : CRUD patrimoine (créer/renommer/
 * archiver), cloisonnement cross-bailleur (ReBAC), rattachement obligatoire et validation du type
 * à la création d'un bien (US-82), et exposition du référentiel TypeBien.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class PatrimoineIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE bien, patrimoine, invitation, bailleur, gestionnaire
                RESTART IDENTITY CASCADE
                """);
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
    void patrimoineCrudCreerRenommerArchiver() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);

        String patrimoineId = JsonPath.read(mockMvc.perform(post("/api/patrimoines")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Patrimoine A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Patrimoine A"))
                .andExpect(jsonPath("$.statut").value("ACTIF"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // 2 patrimoines : celui créé ci-dessus + le « Patrimoine principal » posé par défaut à
        // l'inscription (Hotfix 2026-06-24, cf. InscriptionService).
        mockMvc.perform(get("/api/patrimoines").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.nom == 'Patrimoine A')]").exists());

        mockMvc.perform(put("/api/patrimoines/{id}", patrimoineId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Patrimoine A renommé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Patrimoine A renommé"));

        mockMvc.perform(delete("/api/patrimoines/{id}", patrimoineId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));

        // RS-06 (garde d'archivage si biens actifs) est différé au Sprint 2 : l'archivage reste
        // inconditionnel et le patrimoine archivé demeure visible dans la liste, aux côtés du
        // « Patrimoine principal » par défaut qui reste ACTIF.
        mockMvc.perform(get("/api/patrimoines").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + patrimoineId + "' && @.statut == 'ARCHIVE')]").exists())
                .andExpect(jsonPath("$[?(@.id != '" + patrimoineId + "' && @.statut == 'ACTIF')]").exists());
    }

    @Test
    void patrimoineEcrituresRefuseesCrossBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String patrimoineId = creerPatrimoine(bailleurA, "Patrimoine privé");

        mockMvc.perform(put("/api/patrimoines/{id}", patrimoineId)
                        .with(bailleurJwt(bailleurB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"hack\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/patrimoines/{id}", patrimoineId).with(bailleurJwt(bailleurB)))
                .andExpect(status().isForbidden());

        // Contrôle positif : le propriétaire légitime garde la main sur son patrimoine.
        mockMvc.perform(put("/api/patrimoines/{id}", patrimoineId)
                        .with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Patrimoine renommé par A\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void bienRattachementPatrimoineObligatoireEtValide() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String patrimoineA = creerPatrimoine(bailleurA, "Patrimoine A");
        String patrimoineB = creerPatrimoine(bailleurB, "Patrimoine B");

        // patrimoineId absent → rejeté par la validation Bean Validation (@NotNull).
        mockMvc.perform(post("/api/biens").with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"1 rue Sans Patrimoine\",\"type\":\"APPARTEMENT\","
                                + "\"statut\":\"LIBRE\"}"))
                .andExpect(status().isBadRequest());

        // Type hors référentiel TypeBien (US-81) → 400, même avec un patrimoine valide.
        mockMvc.perform(post("/api/biens").with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bienJson("2 rue Type Invalide", "MAISON", "LIBRE", patrimoineA)))
                .andExpect(status().isBadRequest());

        // Patrimoine d'un autre bailleur (cross-tenant) : invisible sous RLS → 404 (fail-closed).
        mockMvc.perform(post("/api/biens").with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bienJson("3 rue Patrimoine Etranger", "APPARTEMENT", "LIBRE",
                                patrimoineB)))
                .andExpect(status().isNotFound());

        // Rattachement valide : type et patrimoine cohérents avec le bailleur courant → 201.
        mockMvc.perform(post("/api/biens").with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bienJson("4 rue Valide", "APPARTEMENT", "LIBRE", patrimoineA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patrimoineId").value(patrimoineA));
    }

    @Test
    void typesBiensListesAuxDeuxRolesEtRefusesSansRole() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g@test.local");

        mockMvc.perform(get("/api/types-biens").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.code == 'APPARTEMENT')]").exists());

        mockMvc.perform(get("/api/types-biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));

        mockMvc.perform(get("/api/types-biens").with(jwt()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers --------------------------------------------------------------------

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerPatrimoine(String keycloakId, String nom) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/patrimoines")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID insererGestionnaire(String keycloakId, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                id, keycloakId, email, "Martin", "Bob");
        return id;
    }

    private String keycloakId(UUID gestionnaireId) {
        return jdbc.queryForObject("SELECT keycloak_id FROM gestionnaire WHERE id = ?", String.class,
                gestionnaireId);
    }

    private static String bienJson(String adresse, String type, String statut, String patrimoineId) {
        return "{\"adresse\":\"" + adresse + "\",\"type\":\"" + type
                + "\",\"statut\":\"" + statut + "\",\"patrimoineId\":\"" + patrimoineId + "\"}";
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

    private static JwtRequestPostProcessor gestionnaireJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token.subject(keycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"));
    }
}
