package com.loyertracker.comptes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
 * Tests d'intégration EP-15 Sprint A (US-105→108) : profil, cycle de vie global du Gestionnaire
 * (suspension/réactivation/archivage/restauration), garde cross-tenant d'archivage (ADR-16 D4),
 * RBAC (RM-107 : un Gestionnaire n'administre jamais un autre Gestionnaire, ni un bailleur sans
 * relation), recherche/doublons et historique sans fuite cross-bailleur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class GestionnaireLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;
    @Autowired
    TrackingFakeIdp fakeIdp;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE audit_log, affectation, bien, patrimoine, invitation, bailleur, gestionnaire
                RESTART IDENTITY CASCADE
                """);
        fakeIdp.activations.clear();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://localhost/auth/realms/loyertracker");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/realms/loyertracker/protocol/openid-connect/certs");
    }

    /** Double du port IdP qui trace les activations/désactivations (US-105/106). */
    @TestConfiguration
    static class FakeIdpConfig {
        @Bean
        @Primary
        TrackingFakeIdp fakeIdp() {
            return new TrackingFakeIdp();
        }
    }

    static class TrackingFakeIdp implements GestionnaireIdentityProvider {
        private final ConcurrentMap<String, String> parEmail = new ConcurrentHashMap<>();
        final ConcurrentMap<String, Boolean> activations = new ConcurrentHashMap<>();

        @Override
        public GestionnaireIdentity creerOuRecuperer(String email, String nom, String prenom, String motDePasse) {
            String existant = parEmail.get(email);
            if (existant != null) {
                return new GestionnaireIdentity(existant, false);
            }
            String id = "kc-g-" + UUID.randomUUID();
            parEmail.put(email, id);
            return new GestionnaireIdentity(id, true);
        }

        @Override
        public void definirActivation(String keycloakId, boolean actif) {
            activations.put(keycloakId, actif);
        }
    }

    @Test
    void profilModifieEtAuditeParBailleurEnRelation() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoine = creerPatrimoine(bailleur, "Patrimoine A");
        String bien = creerBien(bailleur, patrimoine, "1 rue A");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g1@test.local");
        creerAffectationBien(bailleur, bien, gestionnaire);

        mockMvc.perform(put("/api/gestionnaires/{id}", gestionnaire)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\":\"+243900000000\",\"observations\":\"Fiable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("+243900000000"))
                .andExpect(jsonPath("$.observations").value("Fiable"))
                .andExpect(jsonPath("$.statut").value("ACTIVE"));

        mockMvc.perform(get("/api/gestionnaires/{id}/historique", gestionnaire).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit[?(@.action == 'MODIFIER_GESTIONNAIRE')]").exists());
    }

    @Test
    void suspensionImmediateEtReactivation() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoine = creerPatrimoine(bailleur, "Patrimoine A");
        String bien = creerBien(bailleur, patrimoine, "1 rue A");
        String keycloakId = "kc-g-" + UUID.randomUUID();
        UUID gestionnaire = insererGestionnaire(keycloakId, "g2@test.local");
        creerAffectationBien(bailleur, bien, gestionnaire);

        // Suspension : aucune pré-condition, même avec une affectation ACTIVE.
        mockMvc.perform(post("/api/gestionnaires/{id}/suspension", gestionnaire).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SUSPENDU"));
        assertThat(fakeIdp.activations.get(keycloakId)).isFalse();

        mockMvc.perform(post("/api/gestionnaires/{id}/reactivation", gestionnaire).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
        assertThat(fakeIdp.activations.get(keycloakId)).isTrue();
    }

    @Test
    void archivageBloqueParAffectationActiveChezUnAutreBailleurPuisAutorise() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String patrimoineA = creerPatrimoine(bailleurA, "Patrimoine A");
        String patrimoineB = creerPatrimoine(bailleurB, "Patrimoine B");
        String bienA = creerBien(bailleurA, patrimoineA, "1 rue A");
        String bienB = creerBien(bailleurB, patrimoineB, "1 rue B");
        String keycloakId = "kc-g-" + UUID.randomUUID();
        UUID gestionnairePartage = insererGestionnaire(keycloakId, "partage@test.local");

        creerAffectationBien(bailleurA, bienA, gestionnairePartage); // ACTIVE, invisible sous RLS pour B
        String affectationBId = creerAffectationBien(bailleurB, bienB, gestionnairePartage);

        // bailleurB révoque SA PROPRE affectation, mais celle de bailleurA reste ACTIVE.
        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationBId).with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk());

        // Cross-tenant : l'archivage doit rester bloqué (affectation ACTIVE de bailleurA,
        // invisible sous RLS à bailleurB — seule la fonction SECURITY DEFINER le révèle).
        mockMvc.perform(post("/api/gestionnaires/{id}/archivage", gestionnairePartage).with(bailleurJwt(bailleurB)))
                .andExpect(status().isConflict());
        assertThat(fakeIdp.activations).doesNotContainKey(keycloakId);

        // bailleurA révoque à son tour : plus aucune affectation ACTIVE nulle part → archivage OK.
        // (on retrouve l'id de l'affectation de bailleurA via une nouvelle affectation bien pour simplifier)
        String affectationAId = JsonPath.read(mockMvc.perform(get("/api/biens/{id}/affectations", bienA)
                        .with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationAId).with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/gestionnaires/{id}/archivage", gestionnairePartage).with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ARCHIVE"));
        assertThat(fakeIdp.activations.get(keycloakId)).isFalse();

        mockMvc.perform(post("/api/gestionnaires/{id}/restauration", gestionnairePartage).with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
        assertThat(fakeIdp.activations.get(keycloakId)).isTrue();
    }

    @Test
    void rbacGestionnaireNAdministrePasEtBailleurSansRelationRefuse() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurC = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurC);
        String patrimoineA = creerPatrimoine(bailleurA, "Patrimoine A");
        String bienA = creerBien(bailleurA, patrimoineA, "1 rue A");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g3@test.local");
        creerAffectationBien(bailleurA, bienA, gestionnaire);
        UUID autreGestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g4@test.local");

        // Un Gestionnaire (rôle GESTIONNAIRE) ne peut jamais administrer un autre Gestionnaire.
        mockMvc.perform(post("/api/gestionnaires/{id}/suspension", gestionnaire)
                        .with(gestionnaireJwt(keycloakId(autreGestionnaire))))
                .andExpect(status().isForbidden());

        // Un bailleur sans aucune relation (jamais affecté) avec ce gestionnaire est refusé.
        mockMvc.perform(post("/api/gestionnaires/{id}/suspension", gestionnaire).with(bailleurJwt(bailleurC)))
                .andExpect(status().isForbidden());

        // Contrôle positif : le bailleur en relation garde la main.
        mockMvc.perform(post("/api/gestionnaires/{id}/suspension", gestionnaire).with(bailleurJwt(bailleurA)))
                .andExpect(status().isOk());
    }

    @Test
    void rechercheMulticritereEtDetectionDoublon() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String patrimoine = creerPatrimoine(bailleur, "Patrimoine A");
        String bien = creerBien(bailleur, patrimoine, "1 rue A");
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "recherche@test.local");
        creerAffectationBien(bailleur, bien, gestionnaire);

        mockMvc.perform(get("/api/gestionnaires").param("q", "recherche").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("recherche@test.local"));

        mockMvc.perform(get("/api/gestionnaires").param("q", "inconnu").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/gestionnaires/verification-doublon")
                        .param("email", "recherche@test.local").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void historiqueSansFuiteCrossBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String patrimoineA = creerPatrimoine(bailleurA, "Patrimoine A");
        String patrimoineB = creerPatrimoine(bailleurB, "Patrimoine B");
        String bienA = creerBien(bailleurA, patrimoineA, "1 rue A");
        String bienB = creerBien(bailleurB, patrimoineB, "1 rue B");
        UUID gestionnairePartage = insererGestionnaire("kc-g-" + UUID.randomUUID(), "partage2@test.local");
        creerAffectationBien(bailleurA, bienA, gestionnairePartage);
        creerAffectationBien(bailleurB, bienB, gestionnairePartage);

        // bailleurB ne voit que SA PROPRE affectation (RLS), jamais celle de bailleurA.
        mockMvc.perform(get("/api/gestionnaires/{id}/historique", gestionnairePartage).with(bailleurJwt(bailleurB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectations.length()").value(1));
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

    private String creerBien(String keycloakId, String patrimoineId, String adresse) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"" + adresse + "\",\"type\":\"APPARTEMENT\","
                                + "\"statut\":\"LIBRE\",\"patrimoineId\":\"" + patrimoineId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerAffectationBien(String bailleurKeycloakId, String bienId, UUID gestionnaireId)
            throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(bailleurKeycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":5,"
                                + "\"dateDebut\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"))
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
