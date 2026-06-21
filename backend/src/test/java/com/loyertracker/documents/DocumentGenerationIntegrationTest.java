package com.loyertracker.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
 * Tests d'intégration des documents locatifs (quittance / avis d'échéance) générés à la volée en
 * PDF : flux complet bien→bail→loyer→pointage, états requis, mention d'adresse, cloisonnement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class DocumentGenerationIntegrationTest {

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
                TRUNCATE audit_log, garantie, paiement, affectation, bail, bien, patrimoine,
                         invitation, bailleur, gestionnaire
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
    void quittanceEtAvisGeneresEnPdfSelonLEtatDuLoyer() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        renseignerAdresse(bailleur);
        String bienId = creerBien(bailleur, "5 avenue du Bien");
        creerBail(bailleur, bienId, "2026-01-01", "2026-02-28"); // périodes 2026-01, 2026-02
        genererEcheances(bailleur);

        // 2026-01 soldé → quittance disponible (PDF), avis refusé (409).
        pointerRecu(bailleur, bienId, "2026-01");
        byte[] pdf = mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, "2026-01")
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("quittance-2026-01.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/avis-echeance", bienId, "2026-01")
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isConflict());

        // 2026-02 non soldé (EN_RETARD) → avis d'échéance disponible, quittance refusée (409).
        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/avis-echeance", bienId, "2026-02")
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, "2026-02")
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isConflict());
    }

    @Test
    void quittanceSansAdresseBailleurEstRefusee() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur); // pas d'adresse renseignée
        String bienId = creerBien(bailleur, "5 avenue du Bien");
        creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");
        genererEcheances(bailleur);
        pointerRecu(bailleur, bienId, "2026-01");

        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, "2026-01")
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isConflict());
    }

    @Test
    void documentSurPeriodeInconnueRenvoie404() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        renseignerAdresse(bailleur);
        String bienId = creerBien(bailleur, "5 avenue du Bien");
        creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");

        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, "2099-01")
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isNotFound());
    }

    @Test
    void quittanceInaccessibleAUnAutreBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        renseignerAdresse(bailleurA);
        String bienA = creerBien(bailleurA, "5 avenue du Bien");
        creerBail(bailleurA, bienA, "2026-01-01", "2026-01-31");
        genererEcheances(bailleurA);
        pointerRecu(bailleurA, bienA, "2026-01");

        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurB);

        // B ne peut pas accéder au bien de A (ReBAC) : refus avant toute génération.
        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienA, "2026-01")
                        .with(bailleurJwt(bailleurB)))
                .andExpect(status().isForbidden());
    }

    // --- Helpers (calqués sur le harnais S03) ------------------------------------------------

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private void renseignerAdresse(String keycloakId) throws Exception {
        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"10 rue du Bailleur, 75001 Paris\"}"))
                .andExpect(status().isOk());
    }

    private String creerBien(String keycloakId, String adresse) throws Exception {
        String patrimoineId = creerPatrimoine(keycloakId, "Patrimoine " + adresse);
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"" + adresse + "\",\"type\":\"APPARTEMENT\",\"statut\":\"LIBRE\","
                                + "\"patrimoineId\":\"" + patrimoineId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerPatrimoine(String keycloakId, String nom) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/patrimoines")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void creerBail(String keycloakId, String bienId, String debut, String fin)
            throws Exception {
        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireNom\":\"Locataire\",\"locataireEmail\":\"loc@test.local\","
                                + "\"loyerHc\":800.00,\"provisionCharges\":50.00,\"depotGarantie\":850.00,"
                                + "\"dateDebut\":\"" + debut + "\",\"dateFin\":\"" + fin + "\"}"))
                .andExpect(status().isCreated());
    }

    private void genererEcheances(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
    }

    private void pointerRecu(String keycloakId, String bienId, String periode) throws Exception {
        mockMvc.perform(patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, periode)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantRecu\":850.00,\"statut\":\"RECU\"}"))
                .andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor bailleurJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token.subject(keycloakId)
                        .claim("email", keycloakId + "@test.local")
                        .claim("given_name", "Alice")
                        .claim("family_name", "Durand"))
                .authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"));
    }
}
