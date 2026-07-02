package com.loyertracker.rgpd;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Tests d'intégration US-70 — RGPD : export bailleur et effacement locataire.
 * Vérifie le cloisonnement (cross-bailleur → 404), le RBAC (gestionnaire → 403),
 * et la pseudonymisation effective (locataireNom = "[anonymisé]", email = null).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class RgpdIntegrationTest {

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
                TRUNCATE affectation, bail, bien, patrimoine, invitation, bailleur, gestionnaire
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

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    @Test
    void export_bailleurSansData_retourne200AvecListeVide() throws Exception {
        String kcId = "kc-" + UUID.randomUUID();
        inscrireBailleur(kcId);

        mockMvc.perform(get("/api/bailleurs/export").with(bailleurJwt(kcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bailleurId").exists())
                .andExpect(jsonPath("$.dateExport").exists())
                .andExpect(jsonPath("$.biens").isArray())
                .andExpect(jsonPath("$.biens").isEmpty());
    }

    @Test
    void export_avecBienEtBail_contientDonneesLocataire() throws Exception {
        String kcId = "kc-" + UUID.randomUUID();
        inscrireBailleur(kcId);
        String bienId = creerBien(kcId, "12 rue de l'Export");
        creerBail(kcId, bienId, "Jeanne Durand", "durand@test.local");

        mockMvc.perform(get("/api/bailleurs/export").with(bailleurJwt(kcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biens[0].bien.adresse").value("12 rue de l'Export"))
                .andExpect(jsonPath("$.biens[0].baux[0].bail.locataireNom").value("Jeanne Durand"))
                .andExpect(jsonPath("$.biens[0].baux[0].bail.locataireEmail").value("durand@test.local"));
    }

    @Test
    void export_avecPaiement_contientLaDeviseDuBailParent() throws Exception {
        // US-93 (ADR-13) : l'export RGPD des paiements doit porter la devise résolue via le bail
        // parent, cohérente avec l'affichage frontend (Paiements/Honoraires).
        String kcId = "kc-" + UUID.randomUUID();
        inscrireBailleur(kcId);
        String bienId = creerBien(kcId, "42 rue Devise RGPD");
        creerBail(kcId, bienId, "Nadia Kalonji", "kalonji@test.local", "USD");
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(kcId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bailleurs/export").with(bailleurJwt(kcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biens[0].paiements[0].devise").value("USD"));
    }

    @Test
    void export_sansJwt_renvoie401() throws Exception {
        mockMvc.perform(get("/api/bailleurs/export"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void export_gestionnaire_renvoie403() throws Exception {
        mockMvc.perform(get("/api/bailleurs/export")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Effacement locataire
    // -------------------------------------------------------------------------

    @Test
    void effacement_anonymisePiiEtConserveFinancier() throws Exception {
        String kcId = "kc-" + UUID.randomUUID();
        inscrireBailleur(kcId);
        String bienId = creerBien(kcId, "99 avenue RGPD");
        String bailId = creerBail(kcId, bienId, "Marc Lefort", "lefort@test.local");

        // Effacement RGPD
        mockMvc.perform(delete("/api/biens/{bienId}/baux/{bailId}/locataire", bienId, bailId)
                        .with(bailleurJwt(kcId)))
                .andExpect(status().isNoContent());

        // Vérification via historique du bail
        mockMvc.perform(get("/api/biens/{bienId}/baux", bienId).with(bailleurJwt(kcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locataireNom").value("[anonymisé]"))
                .andExpect(jsonPath("$[0].locataireEmail").doesNotExist())
                // loyer conservé
                .andExpect(jsonPath("$[0].loyerHc").value(850.0));
    }

    @Test
    void effacement_exportRefleteAnonymisation() throws Exception {
        String kcId = "kc-" + UUID.randomUUID();
        inscrireBailleur(kcId);
        String bienId = creerBien(kcId, "1 place Anonyme");
        String bailId = creerBail(kcId, bienId, "Sophie Martin", "martin@test.local");

        mockMvc.perform(delete("/api/biens/{bienId}/baux/{bailId}/locataire", bienId, bailId)
                        .with(bailleurJwt(kcId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bailleurs/export").with(bailleurJwt(kcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biens[0].baux[0].bail.locataireNom").value("[anonymisé]"))
                .andExpect(jsonPath("$.biens[0].baux[0].bail.locataireEmail").doesNotExist());
    }

    @Test
    void effacement_crossBailleur_renvoie404() throws Exception {
        String kcA = "kc-" + UUID.randomUUID();
        String kcB = "kc-" + UUID.randomUUID();
        inscrireBailleur(kcA);
        inscrireBailleur(kcB);
        String bienId = creerBien(kcA, "5 rue Privée");
        String bailId = creerBail(kcA, bienId, "Inconnu", "inconnu@test.local");

        // bailleurB tente d'effacer le locataire d'un bail bailleurA → RLS masque → 404
        mockMvc.perform(delete("/api/biens/{bienId}/baux/{bailId}/locataire", bienId, bailId)
                        .with(bailleurJwt(kcB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void effacement_gestionnaire_renvoie403() throws Exception {
        mockMvc.perform(delete("/api/biens/{bienId}/baux/{bailId}/locataire",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void effacement_sansJwt_renvoie401() throws Exception {
        mockMvc.perform(delete("/api/biens/{bienId}/baux/{bailId}/locataire",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerBien(String keycloakId, String adresse) throws Exception {
        String patrimoineId = JsonPath.read(
                mockMvc.perform(post("/api/patrimoines").with(bailleurJwt(keycloakId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nom\":\"Patrimoine RGPD\",\"adresse\":\"1 rue RGPD\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
        return JsonPath.read(
                mockMvc.perform(post("/api/biens").with(bailleurJwt(keycloakId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"adresse\":\"" + adresse
                                        + "\",\"type\":\"APPARTEMENT\",\"statut\":\"LIBRE\",\"patrimoineId\":\""
                                        + patrimoineId + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerBail(String keycloakId, String bienId, String locataireNom,
            String locataireEmail) throws Exception {
        return creerBail(keycloakId, bienId, locataireNom, locataireEmail, "EUR");
    }

    private String creerBail(String keycloakId, String bienId, String locataireNom,
            String locataireEmail, String devise) throws Exception {
        return JsonPath.read(
                mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                                .with(bailleurJwt(keycloakId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"locataireNom\":\"" + locataireNom
                                        + "\",\"locataireEmail\":\"" + locataireEmail
                                        + "\",\"loyerHc\":850.00,\"provisionCharges\":0.00,"
                                        + "\"dateDebut\":\"2026-01-01\","
                                        + "\"dateFin\":\"2026-12-31\",\"devise\":\"" + devise + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static JwtRequestPostProcessor bailleurJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token
                        .subject(keycloakId)
                        .claim("email", keycloakId + "@test.local")
                        .claim("given_name", "Test")
                        .claim("family_name", "Bailleur"))
                .authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"));
    }
}
