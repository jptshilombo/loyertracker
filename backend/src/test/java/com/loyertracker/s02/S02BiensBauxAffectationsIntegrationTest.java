package com.loyertracker.s02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class S02BiensBauxAffectationsIntegrationTest {

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
                TRUNCATE affectation, bail, locataire, bien, patrimoine, invitation, bailleur,
                         gestionnaire
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
    void biensSontScopesParBailleurEtArchivables() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);

        String bienId = creerBien(bailleurA, "10 rue A");

        mockMvc.perform(get("/api/biens").with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/biens").with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/biens/{id}", bienId)
                        .with(bailleurJwt(bailleurB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bienJson("hack", "APPARTEMENT", "LIBRE", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/biens/{id}/archivage", bienId).with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));
    }

    @Test
    void bailActifEstUniqueEtHistorise() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "20 rue Bail");

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bailJson(bailleur)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIF"));

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bailJson(bailleur)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/biens/{bienId}/baux", bienId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void creationBailAvecLocataireIdInconnuRenvoie404() throws Exception {
        // US-113/V26 : locataireId doit référencer un Locataire existant du bailleur courant.
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "21 rue Bail Inconnu");

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireId\":\"" + UUID.randomUUID()
                                + "\",\"loyerHc\":850.00,\"provisionCharges\":0.00,"
                                + "\"dateDebut\":\"2026-06-01\",\"dateFin\":\"2027-05-31\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creationBailAvecLocataireArchiveRenvoie409() throws Exception {
        // US-113/V26 : un locataire archivé ne peut plus être sélectionné pour un nouveau bail.
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "22 rue Bail Archive");
        String locataireId = JsonPath.read(
                mockMvc.perform(post("/api/locataires").with(bailleurJwt(bailleur))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nom\":\"Locataire Archive\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(delete("/api/locataires/{id}", locataireId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireId\":\"" + locataireId
                                + "\",\"loyerHc\":850.00,\"provisionCharges\":0.00,"
                                + "\"dateDebut\":\"2026-06-01\",\"dateFin\":\"2027-05-31\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void affectationRotationEtAccesGestionnaire() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "30 rue Gestion");
        UUID gestionnaire1 = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g1@test.local");
        UUID gestionnaire2 = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g2@test.local");

        String affectationId = JsonPath.read(mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson(bienId, gestionnaire1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson(bienId, gestionnaire2)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(gestionnaireJwt(keycloakId(gestionnaire1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bailJson(bailleur)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REVOQUEE"));

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/biens/{bienId}/baux", bienId)
                        .with(gestionnaireJwt(keycloakId(gestionnaire1))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson(bienId, gestionnaire2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        mockMvc.perform(get("/api/biens/{bienId}/affectations", bienId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void affectationEcrituresRefuseesCrossBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String bienA = creerBien(bailleurA, "40 rue Proprietaire");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g@test.local");

        // Bailleur A affecte un gestionnaire sur son propre bien.
        String affectationId = JsonPath.read(mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson(bienA, gestionnaire)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        // Bailleur B ne possède pas le bien : le ReBAC (peutAccederBien) refuse la création (403).
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleurB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson(bienA, gestionnaire)))
                .andExpect(status().isForbidden());

        // Bailleur B ne voit pas l'affectation d'autrui sous RLS : la révocation est introuvable (404).
        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationId)
                        .with(bailleurJwt(bailleurB)))
                .andExpect(status().isNotFound());

        // L'affectation reste ACTIVE pour son propriétaire légitime.
        mockMvc.perform(get("/api/biens/{bienId}/affectations", bienA).with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].statut").value("ACTIVE"));
    }

    @Test
    void affectationPeutCiblerUnPatrimoineAvecUnSeulPerimetre() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineId = creerPatrimoine(bailleur, "Portefeuille A");
        String bienId = creerBien(bailleur, "50 rue Mixte");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "gp@test.local");

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bienId").doesNotExist())
                .andExpect(jsonPath("$.patrimoineId").value(patrimoineId))
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationJson(bienId, patrimoineId, gestionnaire)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archivagePatrimoineRefuseSiAffectationActive() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineId = creerPatrimoine(bailleur, "Portefeuille Bloque");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "gblock@test.local");

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        mockMvc.perform(delete("/api/patrimoines/{id}", patrimoineId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/patrimoines").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + patrimoineId + "' && @.statut == 'ACTIF')]").exists());
    }

    @Test
    void affectationPatrimoineDonneAccesAuxBiensDuPortefeuillePuisRevoque() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineId = creerPatrimoine(bailleur, "Portefeuille Herite");
        String bienA = creerBienDansPatrimoine(bailleur, patrimoineId, "60 rue Heritee A");
        String bienB = creerBienDansPatrimoine(bailleur, patrimoineId, "61 rue Heritee B");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "gpat@test.local");

        String affectationId = JsonPath.read(mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + bienA + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + bienB + "')]").exists());

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienA)
                        .with(gestionnaireJwt(keycloakId(gestionnaire)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bailJson(bailleur)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REVOQUEE"));

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienB)
                        .with(gestionnaireJwt(keycloakId(gestionnaire)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bailJson(bailleur)))
                .andExpect(status().isForbidden());
    }

    @Test
    void affectationExclusionSansAffectationPatrimoineEstRejetee400() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineId = creerPatrimoine(bailleur, "Portefeuille RS04");
        String bienId = creerBienDansPatrimoine(bailleur, patrimoineId, "70 rue RS04");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "grs04@test.local");

        // RS-04 : aucune affectation patrimoine active pour ce gestionnaire -> EXCLUSION incohérente.
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationBienAvecExceptionJson(bienId, gestionnaire, "EXCLUSION")))
                .andExpect(status().isBadRequest());

        // Une fois l'affectation patrimoine active posée, la même EXCLUSION devient recevable.
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationBienAvecExceptionJson(bienId, gestionnaire, "EXCLUSION")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeException").value("EXCLUSION"));
    }

    @Test
    void affectationPatrimoineAvecExceptionEstRejetee400() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineId = creerPatrimoine(bailleur, "Portefeuille Exception Invalide");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "ginvalide@test.local");

        // US-85/RS-04 : typeException n'a de sens que sur une affectation bien (bienId requis).
        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineAvecExceptionJson(patrimoineId, gestionnaire, "INCLUSION")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void affectationPatrimoineAvecExclusionBienExcluDeLaListeDesBiens() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoineId = creerPatrimoine(bailleur, "Portefeuille Exclusion");
        String bienExclu = creerBienDansPatrimoine(bailleur, patrimoineId, "80 rue Exclue");
        String bienAutre = creerBienDansPatrimoine(bailleur, patrimoineId, "81 rue Incluse");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "gexcl@test.local");

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationBienAvecExceptionJson(bienExclu, gestionnaire, "EXCLUSION")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/biens").with(gestionnaireJwt(keycloakId(gestionnaire))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bienAutre));

        mockMvc.perform(post("/api/biens/{bienId}/baux", bienExclu)
                        .with(gestionnaireJwt(keycloakId(gestionnaire)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bailJson(bailleur)))
                .andExpect(status().isForbidden());
    }

    @Test
    void historiqueAffectationsPatrimoineScopeParBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);

        String patrimoineId = creerPatrimoine(bailleurA, "Portefeuille Historique");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "ghist@test.local");

        mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleurA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(affectationPatrimoineJson(patrimoineId, gestionnaire)))
                .andExpect(status().isCreated());

        // Bailleur A voit son historique (1 affectation ACTIVE)
        mockMvc.perform(get("/api/patrimoines/{id}/affectations", patrimoineId)
                        .with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].statut").value("ACTIVE"));

        // Bailleur B ne peut pas accéder au patrimoine d'autrui (RLS → 404 via peutAccederPatrimoine)
        mockMvc.perform(get("/api/patrimoines/{id}/affectations", patrimoineId)
                        .with(bailleurJwt(bailleurB)))
                .andExpect(status().isForbidden());

        // Patrimoine inexistant → 403 (peutAccederPatrimoine échoue avant la couche service)
        mockMvc.perform(get("/api/patrimoines/{id}/affectations", UUID.randomUUID())
                        .with(bailleurJwt(bailleurA)))
                .andExpect(status().isForbidden());
    }

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerBien(String keycloakId, String adresse) throws Exception {
        String patrimoineId = creerPatrimoine(keycloakId, "Patrimoine " + adresse);
        return creerBienDansPatrimoine(keycloakId, patrimoineId, adresse);
    }

    private String creerBienDansPatrimoine(String keycloakId, String patrimoineId, String adresse) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bienJson(adresse, "APPARTEMENT", "LIBRE", patrimoineId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerPatrimoine(String keycloakId, String nom) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/patrimoines")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\",\"adresse\":\"1 rue du " + nom + "\"}"))
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

    /** Crée un locataire (BAILLEUR) puis renvoie le JSON de création de bail correspondant. */
    private String bailJson(String bailleurKeycloakId) throws Exception {
        String locataireId = JsonPath.read(
                mockMvc.perform(post("/api/locataires").with(bailleurJwt(bailleurKeycloakId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nom\":\"Locataire\",\"email\":\"locataire@test.local\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(), "$.id");
        return "{\"locataireId\":\"" + locataireId + "\","
                + "\"loyerHc\":850.00,\"provisionCharges\":0.00,\"dateDebut\":\"2026-06-01\",\"dateFin\":\"2027-05-31\"}";
    }

    private static String affectationJson(String bienId, UUID gestionnaireId) {
        return "{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                + "\"dateDebut\":\"2026-06-01\"}";
    }

    private static String affectationBienAvecExceptionJson(String bienId, UUID gestionnaireId,
            String typeException) {
        return "{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                + "\"dateDebut\":\"2026-06-01\",\"typeException\":\"" + typeException + "\"}";
    }

    private static String affectationPatrimoineJson(String patrimoineId, UUID gestionnaireId) {
        return "{\"patrimoineId\":\"" + patrimoineId + "\",\"gestionnaireId\":\"" + gestionnaireId
                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                + "\"dateDebut\":\"2026-06-01\"}";
    }

    private static String affectationPatrimoineAvecExceptionJson(String patrimoineId, UUID gestionnaireId,
            String typeException) {
        return "{\"patrimoineId\":\"" + patrimoineId + "\",\"gestionnaireId\":\"" + gestionnaireId
                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                + "\"dateDebut\":\"2026-06-01\",\"typeException\":\"" + typeException + "\"}";
    }

    private static String affectationJson(String bienId, String patrimoineId, UUID gestionnaireId) {
        return "{\"bienId\":\"" + bienId + "\",\"patrimoineId\":\"" + patrimoineId
                + "\",\"gestionnaireId\":\"" + gestionnaireId
                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                + "\"dateDebut\":\"2026-06-01\"}";
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
        assertThat(keycloakId).isNotBlank();
        return jwt()
                .jwt(token -> token.subject(keycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"));
    }
}
