package com.loyertracker.s04;

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
 * Tests d'intégration S04 — honoraires de gestion (US-40, EF-50/51/52, Annexe A.6). Couvre le
 * calcul POURCENTAGE recalculé sur le loyer encaissé (hook synchrone au pointage), le calcul
 * FORFAIT et l'idempotence du batch, le gel à {@code PAYE}, la réserve de la validation au bailleur
 * (gestionnaire 403) et le cloisonnement cross-bailleur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class S04HonorairesIntegrationTest {

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
                TRUNCATE audit_log, honoraire, garantie, paiement, affectation, bail, bien,
                         invitation, bailleur, gestionnaire
                RESTART IDENTITY CASCADE
                """);
    }

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

    @Test
    void honorairesPourcentageRecalculesAuPointagePuisFigesAPaye() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "1 rue Honoraires");
        creerBail(bailleur, bienId, "2026-01-01", "2026-02-28");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g1@test.local");
        affecter(bailleur, bienId, gestionnaire, "POURCENTAGE", "10.00");
        genererEcheances(bailleur);

        // Pointage du loyer 2026-01 (850 encaissés) → recalcul synchrone : honoraire = 10% × 850 = 85.00.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk());

        // GET (tri période desc) : [0]=2026-02 (loyer non encaissé → 0.00), [1]=2026-01 (85.00, DU).
        String corps = mockMvc.perform(get("/api/biens/{bienId}/honoraires", bienId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].periode").value("2026-02"))
                .andExpect(jsonPath("$[0].montant").value(0.00))
                .andExpect(jsonPath("$[1].periode").value("2026-01"))
                .andExpect(jsonPath("$[1].montant").value(85.00))
                .andExpect(jsonPath("$[1].statut").value("DU"))
                .andReturn().getResponse().getContentAsString();
        String honoraireJanvier = JsonPath.read(corps, "$[1].id");

        // Validation par le bailleur → PAYE (figé).
        mockMvc.perform(changerStatut(honoraireJanvier, bailleur, "PAYE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PAYE"));

        // Nouvel encaissement plus faible (PARTIEL 400) sur 2026-01 : le recalcul NE TOUCHE PAS un
        // honoraire PAYE (gel EF-52) — il reste à 85.00.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "400.00", "PARTIEL"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/biens/{bienId}/honoraires", bienId).with(bailleurJwt(bailleur)))
                .andExpect(jsonPath("$[1].periode").value("2026-01"))
                .andExpect(jsonPath("$[1].montant").value(85.00))
                .andExpect(jsonPath("$[1].statut").value("PAYE"));
    }

    @Test
    void honorairesForfaitFixesEtBatchIdempotent() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "2 rue Forfait");
        creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g2@test.local");
        affecter(bailleur, bienId, gestionnaire, "FORFAIT", "50.00");
        genererEcheances(bailleur);

        // Batch : honoraire FORFAIT = 50.00, indépendant du loyer encaissé (0 ici).
        mockMvc.perform(post("/api/batch/honoraires").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.honorairesCalcules").value(1));
        mockMvc.perform(get("/api/biens/{bienId}/honoraires", bienId).with(bailleurJwt(bailleur)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].periode").value("2026-01"))
                .andExpect(jsonPath("$[0].montant").value(50.00));

        // 2e passage : aucun changement → idempotent (0 honoraire recalculé).
        mockMvc.perform(post("/api/batch/honoraires").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.honorairesCalcules").value(0));
    }

    @Test
    void validationReserveeAuBailleurEtCloisonnement() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String bienA = creerBien(bailleurA, "3 rue Privee");
        creerBail(bailleurA, bienA, "2026-01-01", "2026-01-31");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g3@test.local");
        affecter(bailleurA, bienA, gestionnaire, "POURCENTAGE", "10.00");
        genererEcheances(bailleurA);
        mockMvc.perform(pointer(bienA, "2026-01", bailleurA, "850.00", "RECU"))
                .andExpect(status().isOk());
        String gJwt = keycloakId(gestionnaire);

        // Le gestionnaire affecté actif consulte les honoraires du bien…
        String corps = mockMvc.perform(get("/api/biens/{bienId}/honoraires", bienA)
                        .with(gestionnaireJwt(gJwt)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String honoraireId = JsonPath.read(corps, "$[0].id");

        // …mais ne peut PAS valider (réservé au bailleur, EF-52) → 403.
        mockMvc.perform(changerStatut(honoraireId, null, "PAYE").with(gestionnaireJwt(gJwt)))
                .andExpect(status().isForbidden());

        // Bailleur B : ni consultation (ReBAC 403) ni validation (RLS → introuvable 404) cross-tenant.
        mockMvc.perform(get("/api/biens/{bienId}/honoraires", bienA).with(bailleurJwt(bailleurB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(changerStatut(honoraireId, bailleurB, "PAYE"))
                .andExpect(status().isNotFound());
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

    private void affecter(String keycloakId, String bienId, UUID gestionnaireId, String type,
            String montant) throws Exception {
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                                + "\",\"typeHonoraires\":\"" + type + "\",\"montantHonoraires\":" + montant
                                + ",\"dateDebut\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());
    }

    private void genererEcheances(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pointer(
            String bienId, String periode, String bailleurKc, String montantRecu, String statut) {
        var builder = patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, periode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantRecu\":" + montantRecu + ",\"statut\":\"" + statut + "\"}");
        return bailleurKc == null ? builder : builder.with(bailleurJwt(bailleurKc));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder changerStatut(
            String honoraireId, String bailleurKc, String statut) {
        var builder = patch("/api/honoraires/{id}/statut", honoraireId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"" + statut + "\"}");
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
