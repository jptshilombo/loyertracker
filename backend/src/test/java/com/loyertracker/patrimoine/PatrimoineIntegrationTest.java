package com.loyertracker.patrimoine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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

    // =====================================================================================
    // Régression "Historique des biens" : un bailleur/gestionnaire doit voir TOUS les biens
    // de son périmètre, pas seulement le dernier créé. Aucune trace de ce défaut n'a été
    // reproduite dans le code actuel (BienRepository.findByBailleurIdOrderByAdresseAsc et
    // biens_affectes_gestionnaire ne portent ni LIMIT ni Pageable) ; ces tests couvrent
    // explicitement la matrice demandée pour fermer l'écart de couverture constaté (aucun
    // test existant ne créait plus d'un bien par bailleur avant de relire GET /api/biens).
    // =====================================================================================

    @Test
    void historiqueBiensUnBailleurUnPatrimoineCinqBiens() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoine = creerPatrimoine(bailleur, "Patrimoine unique");

        for (int i = 1; i <= 5; i++) {
            creerBien(bailleur, patrimoine, "Bien " + i + " du patrimoine unique");
        }

        mockMvc.perform(get("/api/biens").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.adresse == 'Bien 1 du patrimoine unique')]").exists())
                .andExpect(jsonPath("$[?(@.adresse == 'Bien 5 du patrimoine unique')]").exists());
    }

    @Test
    void historiqueBiensUnBailleurPlusieursPatrimoines() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineA = creerPatrimoine(bailleur, "Patrimoine A");
        String patrimoineB = creerPatrimoine(bailleur, "Patrimoine B");

        creerBien(bailleur, patrimoineA, "Bien A1");
        creerBien(bailleur, patrimoineA, "Bien A2");
        creerBien(bailleur, patrimoineB, "Bien B1");
        creerBien(bailleur, patrimoineB, "Bien B2");
        creerBien(bailleur, patrimoineB, "Bien B3");

        String body = mockMvc.perform(get("/api/biens").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        List<?> biensPatrimoineA = JsonPath.read(body, "$[?(@.patrimoineId == '" + patrimoineA + "')]");
        List<?> biensPatrimoineB = JsonPath.read(body, "$[?(@.patrimoineId == '" + patrimoineB + "')]");
        assertThat(biensPatrimoineA).hasSize(2);
        assertThat(biensPatrimoineB).hasSize(3);
    }

    @Test
    void historiqueBiensIsolationPlusieursBailleurs() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String patrimoineA = creerPatrimoine(bailleurA, "Patrimoine bailleur A");
        String patrimoineB = creerPatrimoine(bailleurB, "Patrimoine bailleur B");

        creerBien(bailleurA, patrimoineA, "Bien 1 bailleur A");
        creerBien(bailleurA, patrimoineA, "Bien 2 bailleur A");
        creerBien(bailleurA, patrimoineA, "Bien 3 bailleur A");
        creerBien(bailleurB, patrimoineB, "Bien 1 bailleur B");

        mockMvc.perform(get("/api/biens").with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/biens").with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void historiqueBiensGestionnaireAffectePatrimoineVoitTousLesBiens() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoine = creerPatrimoine(bailleur, "Patrimoine affecté");
        creerBien(bailleur, patrimoine, "Bien 1 patrimoine affecté");
        creerBien(bailleur, patrimoine, "Bien 2 patrimoine affecté");
        creerBien(bailleur, patrimoine, "Bien 3 patrimoine affecté");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g-patrimoine@test.local");

        creerAffectationPatrimoine(bailleur, patrimoine, gestionnaire);

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void historiqueBiensGestionnaireAffecteBienSpecifiqueNeVoitQueCeBien() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoine = creerPatrimoine(bailleur, "Patrimoine partiel");
        String bienAffecte = creerBien(bailleur, patrimoine, "Bien affecté individuellement");
        creerBien(bailleur, patrimoine, "Bien non affecté 1");
        creerBien(bailleur, patrimoine, "Bien non affecté 2");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g-bien@test.local");

        creerAffectationBien(bailleur, bienAffecte, gestionnaire);

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bienAffecte));
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

    private String creerBien(String keycloakId, String patrimoineId, String adresse) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bienJson(adresse, "APPARTEMENT", "LIBRE", patrimoineId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void creerAffectationPatrimoine(String bailleurKeycloakId, String patrimoineId, UUID gestionnaireId)
            throws Exception {
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleurKeycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson("patrimoineId", patrimoineId, gestionnaireId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
    }

    private void creerAffectationBien(String bailleurKeycloakId, String bienId, UUID gestionnaireId)
            throws Exception {
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleurKeycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson("bienId", bienId, gestionnaireId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
    }

    private static String affectationJson(String perimetreField, String perimetreId, UUID gestionnaireId) {
        return "{\"" + perimetreField + "\":\"" + perimetreId + "\",\"gestionnaireId\":\"" + gestionnaireId
                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":5,"
                + "\"dateDebut\":\"" + LocalDate.now() + "\"}";
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
