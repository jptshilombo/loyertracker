package com.loyertracker.s04;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/**
 * Tests d'intégration S04 — alertes de pilotage (US-50/51/52) & consultation de l'audit (US-62).
 * Couvre la génération des trois types d'alertes (PREAVIS reporté), l'anti-doublon (EF-65), le
 * marquage « lue », le scoping du gestionnaire à ses affectations actives, et l'accès à l'audit
 * réservé au bailleur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class S04AlertesAuditIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE audit_log, alerte, honoraire, garantie, paiement, affectation, bail, bien,
                         invitation, bailleur, gestionnaire
                RESTART IDENTITY CASCADE
                """);
    }

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
    void loyerEnRetardEtFinBailGeneresAntiDoublonEtLecture() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "1 rue Alerte");
        // Bail ACTIF de 3 mois échus → 3 loyers EN_RETARD ; terme passé (≤ J+60) → 1 FIN_BAIL.
        creerBail(bailleur, bienId, "2026-01-01", "2026-03-31");
        genererEcheances(bailleur);

        // 1er passage : 3 LOYER_EN_RETARD + 1 FIN_BAIL = 4 alertes.
        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertesCreees").value(4));

        // 2e passage : toutes déjà NON_LUE → anti-doublon (EF-65) → 0.
        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertesCreees").value(0));

        // Le bailleur voit ses alertes (les deux types présents).
        String corps = mockMvc.perform(get("/api/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$..type", hasItems("LOYER_EN_RETARD", "FIN_BAIL")))
                .andReturn().getResponse().getContentAsString();

        // Marquage « lue » d'une alerte.
        String alerteId = JsonPath.read(corps, "$[0].id");
        mockMvc.perform(patch("/api/alertes/{id}/lecture", alerteId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LUE"));
    }

    @Test
    void garantieNonRestitueeGeneree() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "2 rue Caution");
        String bailId = creerBail(bailleur, bienId, "2025-01-01", "2025-06-30");
        deposerGarantie(bailleur, bienId, bailId);
        // Bail clos il y a plus de 30 jours, garantie toujours DETENU → GARANTIE_NON_RESTITUEE.
        jdbc.update("UPDATE bail SET statut = 'CLOS', date_fin = DATE '2025-06-30' WHERE id = ?",
                UUID.fromString(bailId));

        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertesCreees").value(1));
        mockMvc.perform(get("/api/alertes").with(bailleurJwt(bailleur)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("GARANTIE_NON_RESTITUEE"));
    }

    @Test
    void preavisGenereDansLaBandeExclusifDeFinBailEtAntiDoublon() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "6 rue Preavis");
        // Terme dans la bande de préavis ]J+60 ; J+90] (date du jour ≈ 2026-06) → 1 PREAVIS, 0 FIN_BAIL.
        // Aucune échéance générée → aucun loyer EN_RETARD : on isole le type PREAVIS.
        creerBail(bailleur, bienId, "2026-06-01", "2026-09-01");

        // 1er passage : 1 seule alerte PREAVIS (terme > J+60 → pas de FIN_BAIL).
        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertesCreees").value(1));
        // 2e passage : alerte déjà NON_LUE → anti-doublon (EF-65) → 0.
        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertesCreees").value(0));

        mockMvc.perform(get("/api/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PREAVIS"));
    }

    @Test
    void gestionnaireNeVoitQueLesAlertesDeSesBiensAffectes() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienA = creerBien(bailleur, "3 rue Affectee");
        String bienB = creerBien(bailleur, "4 rue NonAffectee");
        creerBail(bailleur, bienA, "2026-01-01", "2026-01-31");
        creerBail(bailleur, bienB, "2026-01-01", "2026-01-31");
        genererEcheances(bailleur);
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g@test.local");
        affecter(bailleur, bienA, gestionnaire); // affecté à A uniquement
        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk());
        String gJwt = keycloakId(gestionnaire);

        // Le gestionnaire ne voit que les alertes du bien A (scoping US-52, EF-64).
        mockMvc.perform(get("/api/alertes").with(gestionnaireJwt(gJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..bienId", hasItem(bienA)))
                .andExpect(jsonPath("$..bienId", not(hasItem(bienB))));
    }

    @Test
    void auditConsultableParBailleurInterditAuGestionnaire() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "5 rue Audit");
        creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");
        genererEcheances(bailleur);
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk());

        // Le bailleur consulte son journal (au moins le pointage est tracé).
        mockMvc.perform(get("/api/audit").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..action", hasItem("POINTER_PAIEMENT")));

        // Le gestionnaire n'a pas accès à l'audit (US-62, ENF-05) → 403.
        mockMvc.perform(get("/api/audit")
                        .with(gestionnaireJwt("kc-g-" + UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    // ---- helpers --------------------------------------------------------------------

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerBien(String keycloakId, String adresse) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"" + adresse + "\",\"type\":\"APPARTEMENT\",\"statut\":\"LIBRE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerBail(String keycloakId, String bienId, String debut, String fin)
            throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireNom\":\"Locataire\",\"locataireEmail\":\"loc@test.local\","
                                + "\"loyerCc\":850.00,\"depotGarantie\":850.00,\"dateDebut\":\""
                                + debut + "\",\"dateFin\":\"" + fin + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void deposerGarantie(String keycloakId, String bienId, String bailId) throws Exception {
        mockMvc.perform(post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":850.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2025-01-01\"}"))
                .andExpect(status().isCreated());
    }

    private void genererEcheances(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
    }

    private void affecter(String keycloakId, String bienId, UUID gestionnaireId) throws Exception {
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                                + "\"dateDebut\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pointer(
            String bienId, String periode, String bailleurKc, String montantRecu, String statut) {
        var builder = patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, periode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantRecu\":" + montantRecu + ",\"statut\":\"" + statut + "\"}");
        return bailleurKc == null ? builder : builder.with(bailleurJwt(bailleurKc));
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

    private static JwtRequestPostProcessor bailleurJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token.subject(keycloakId)
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
