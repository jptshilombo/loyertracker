package com.loyertracker.s03;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Tests d'intégration S03 — paiements (US-30/31) & garanties (US-32). Couvre la génération à terme
 * échu (Annexe A.3) et son idempotence, le pointage, le cycle de vie de la garantie (A.5),
 * l'autorisation cross-bailleur / cross-affectation (DoD) et la journalisation d'audit (BNF-05).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class S03PaiementsGarantiesIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE audit_log, garantie, paiement, affectation, bail, bien, invitation,
                         bailleur, gestionnaire
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
    void echeancesGenereesATermeEchuEtIdempotentes() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "1 rue des Loyers");
        // Bail de 3 mois civils consommés : 2026-01, 2026-02, 2026-03.
        creerBail(bailleur, bienId, "2026-01-01", "2026-03-31");

        // 1er passage : 3 échéances créées (mois début → mois terme, sans prorata).
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancesCreees").value(3));

        // 2e passage : idempotent (uq_paiement_periode) → 0.
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancesCreees").value(0));

        // La plus récente période (tri desc) = 2026-03, exigible le 1er du mois suivant, montant = loyer CC.
        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].periode").value("2026-03"))
                .andExpect(jsonPath("$[0].dateExigibilite").value("2026-04-01"))
                .andExpect(jsonPath("$[0].montantAttendu").value(850.00))
                .andExpect(jsonPath("$[0].statut").value("IMPAYE"))
                .andExpect(jsonPath("$[2].periode").value("2026-01"));
    }

    @Test
    void pointageLoyerRecuPartielEtControles() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "2 rue Pointage");
        creerBail(bailleur, bienId, "2026-01-01", "2026-02-28");
        genererEcheances(bailleur);

        // Pointage RECU : reste dû nul.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECU"))
                .andExpect(jsonPath("$.resteDu").value(0.00));

        // Pointage PARTIEL : reste dû = attendu - reçu.
        mockMvc.perform(pointer(bienId, "2026-02", bailleur, "300.00", "PARTIEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PARTIEL"))
                .andExpect(jsonPath("$.resteDu").value(550.00));

        // PARTIEL incohérent (reçu >= attendu) → 400.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "PARTIEL"))
                .andExpect(status().isBadRequest());

        // Période inexistante → 404.
        mockMvc.perform(pointer(bienId, "2030-12", bailleur, "10.00", "RECU"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cycleVieGarantie() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "3 rue Caution");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-12-31");

        String garantieId = JsonPath.read(mockMvc.perform(
                        post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                                .with(bailleurJwt(bailleur))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"montant\":850.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("DETENU"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // Restitution partielle avec retenue + motif → RESTITUE_PARTIEL.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur,
                        "{\"type\":\"PARTIELLE\",\"montantRetenu\":100.00,\"motifRetenue\":\"Dégâts\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RESTITUE_PARTIEL"))
                .andExpect(jsonPath("$.montantRetenu").value(100.00));

        // Puis restitution totale → RESTITUE_TOTAL.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur, "{\"type\":\"TOTALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RESTITUE_TOTAL"));

        // Une garantie déjà totalement restituée ne peut plus l'être → 409.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur, "{\"type\":\"TOTALE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void accesFinancierRefuseCrossBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String bienA = creerBien(bailleurA, "4 rue Privee");
        String bailA = creerBail(bailleurA, bienA, "2026-01-01", "2026-06-30");
        genererEcheances(bailleurA);

        // Bailleur B n'a aucun droit sur le bien de A : ReBAC refuse (403).
        mockMvc.perform(pointer(bienA, "2026-01", bailleurB, "850.00", "RECU"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienA).with(bailleurJwt(bailleurB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/biens/{bienId}/baux/{bailId}/garanties", bienA, bailA)
                        .with(bailleurJwt(bailleurB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":1.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestionnaireAffecteActifPuisRevoque() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "5 rue Delegation");
        creerBail(bailleur, bienId, "2026-01-01", "2026-03-31");
        genererEcheances(bailleur);
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g@test.local");
        String affectationId = affecter(bailleur, bienId, gestionnaire);
        String gJwt = keycloakId(gestionnaire);

        // Gestionnaire affecté actif : peut pointer.
        mockMvc.perform(pointer(bienId, "2026-01", null, "850.00", "RECU").with(gestionnaireJwt(gJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECU"));

        // Après révocation : accès refusé (403).
        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk());
        mockMvc.perform(pointer(bienId, "2026-02", null, "850.00", "RECU").with(gestionnaireJwt(gJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ecrituresFinancieresJournalisees() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "6 rue Audit");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");
        genererEcheances(bailleur);

        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":850.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());

        assertThat(compterAudit("POINTER_PAIEMENT")).isEqualTo(1);
        assertThat(compterAudit("CREATE_GARANTIE")).isEqualTo(1);
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

    private void genererEcheances(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
    }

    private String affecter(String keycloakId, String bienId, UUID gestionnaireId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                                + "\"dateDebut\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pointer(
            String bienId, String periode, String bailleurKc, String montantRecu, String statut) {
        var builder = patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, periode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantRecu\":" + montantRecu + ",\"statut\":\"" + statut + "\"}");
        return bailleurKc == null ? builder : builder.with(bailleurJwt(bailleurKc));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder restituer(
            String bienId, String bailId, String garantieId, String bailleurKc, String body) {
        return post("/api/biens/{bienId}/baux/{bailId}/garanties/{gid}/restitution",
                bienId, bailId, garantieId)
                .with(bailleurJwt(bailleurKc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
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

    private Integer compterAudit(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ?", Integer.class,
                action);
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
