package com.loyertracker.baux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * Tests d'intégration EP-13 — clôture/réouverture de bail (US-115/116), purge de l'échéancier
 * futur (US-117) et non-régression du batch d'alertes (US-118, cf. aussi
 * {@code S04AlertesAuditIntegrationTest#loyerEnRetardNonGenereApresClotureDuBail}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class BailClotureIntegrationTest {

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
                TRUNCATE audit_log, alerte, honoraire, garantie, paiement, affectation, bail,
                         locataire, bien, patrimoine, invitation, bailleur, gestionnaire
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
    void clotureAvecAvertissementsEtPurgeEchéancierFutur() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "1 rue Cloture");
        // Bail à cheval sur aujourd'hui : mois passés -> EN_RETARD après génération, mois futurs
        // -> A_VENIR (V18 : la génération couvre tout l'intervalle dateDebut -> dateFin).
        LocalDate aujourdHui = LocalDate.now();
        LocalDate debut = aujourdHui.minusMonths(6).withDayOfMonth(1);
        LocalDate fin = aujourdHui.plusMonths(6);
        String bailId = creerBail(bailleur, bienId, debut.toString(), fin.toString());
        genererEcheances(bailleur);
        creerGarantie(bienId, bailId, bailleur, "900.00"); // reste DETENU, jamais restituée

        String moisCloture = aujourdHui.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String corps = mockMvc.perform(cloturer(bienId, bailId, bailleur, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bail.statut").value("CLOS"))
                .andExpect(jsonPath("$.bail.dateClotureEffective").value(aujourdHui.toString()))
                .andExpect(jsonPath("$.bail.dateFin").value(fin.toString()))
                .andExpect(jsonPath("$.avertissements.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(corps, "$.avertissements[0]")).isNotBlank();

        // US-117/K6 : plus aucun A_VENIR strictement postérieur au mois de clôture.
        Integer aVenirFuturs = jdbc.queryForObject(
                "SELECT count(*) FROM paiement WHERE bail_id = ?::uuid AND statut = 'A_VENIR' "
                        + "AND periode > ?",
                Integer.class, bailId, moisCloture);
        assertThat(aVenirFuturs).isZero();

        // Les faits historiques (EN_RETARD, mois passés) restent intacts.
        Integer enRetardConserves = jdbc.queryForObject(
                "SELECT count(*) FROM paiement WHERE bail_id = ?::uuid AND statut = 'EN_RETARD'",
                Integer.class, bailId);
        assertThat(enRetardConserves).isEqualTo(6);
    }

    @Test
    void reouvertureRemetLeBailActifEtEfaceLaDateCloture() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "2 rue Reouverture");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-12-31");

        mockMvc.perform(cloturer(bienId, bailId, bailleur, null)).andExpect(status().isOk());

        mockMvc.perform(rouvrir(bienId, bailId, bailleur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"))
                .andExpect(jsonPath("$.dateClotureEffective").doesNotExist());
    }

    @Test
    void reouvertureRefuseeSiUnAutreBailActifExisteDejaSurLeBien() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "3 rue Collision");
        String bailA = creerBail(bailleur, bienId, "2026-01-01", "2026-06-30");
        mockMvc.perform(cloturer(bienId, bailA, bailleur, null)).andExpect(status().isOk());

        // Le bien redevient LIBRE côté base uniquement pour permettre la création du bail B :
        // aucun endpoint applicatif ne remet Bien.statut à LIBRE après clôture (hors périmètre
        // ADR-17, cf. Plan d'Exécution — libérer le bien reste une action bailleur séparée).
        jdbc.update("UPDATE bien SET statut = 'LIBRE' WHERE id = ?::uuid", bienId);
        creerBail(bailleur, bienId, "2026-07-01", "2027-06-30");

        mockMvc.perform(rouvrir(bienId, bailA, bailleur)).andExpect(status().isConflict());
    }

    @Test
    void clotureDunBailDejaClosRefusee() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "4 rue DoubleCloture");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-06-30");

        mockMvc.perform(cloturer(bienId, bailId, bailleur, null)).andExpect(status().isOk());
        mockMvc.perform(cloturer(bienId, bailId, bailleur, null)).andExpect(status().isConflict());
    }

    @Test
    void clotureEtReouvertureRefuseesPourUnBailDunAutreBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String bienId = creerBien(bailleurA, "5 rue Privee");
        String bailId = creerBail(bailleurA, bienId, "2026-01-01", "2026-06-30");

        mockMvc.perform(cloturer(bienId, bailId, bailleurB, null)).andExpect(status().isForbidden());
    }

    @Test
    void aucuneNouvelleAlerteLoyerEnRetardApresUneClotureReelle() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "6 rue PostCloture");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-03-31");
        genererEcheances(bailleur); // 3 échéances échues -> EN_RETARD (non purgées : pas A_VENIR)

        mockMvc.perform(cloturer(bienId, bailId, bailleur, null)).andExpect(status().isOk());

        mockMvc.perform(genererAlertes(bailleur))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertesCreees").value(0));
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
                        .content("{\"nom\":\"" + nom + "\",\"adresse\":\"1 rue du " + nom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
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

    private String creerLocataire(String keycloakId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/locataires").with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Locataire\",\"email\":\"loc@test.local\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerBail(String keycloakId, String bienId, String debut, String fin)
            throws Exception {
        String locataireId = creerLocataire(keycloakId);
        return JsonPath.read(mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireId\":\"" + locataireId
                                + "\",\"loyerHc\":850.00,\"provisionCharges\":0.00,\"dateDebut\":\""
                                + debut + "\",\"dateFin\":\"" + fin + "\",\"devise\":\"EUR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void genererEcheances(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
    }

    private String creerGarantie(String bienId, String bailId, String bailleurKc, String montant)
            throws Exception {
        return JsonPath.read(mockMvc.perform(
                        post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                                .with(bailleurJwt(bailleurKc))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"montant\":" + montant
                                        + ",\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder cloturer(
            String bienId, String bailId, String bailleurKc, String body) {
        var builder = post("/api/biens/{bienId}/baux/{bailId}/cloture", bienId, bailId)
                .with(bailleurJwt(bailleurKc));
        return body == null ? builder
                : builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder rouvrir(
            String bienId, String bailId, String bailleurKc) {
        return post("/api/biens/{bienId}/baux/{bailId}/reouverture", bienId, bailId)
                .with(bailleurJwt(bailleurKc));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder genererAlertes(
            String bailleurKc) {
        return post("/api/batch/alertes").with(bailleurJwt(bailleurKc));
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
