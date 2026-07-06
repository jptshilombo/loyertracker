package com.loyertracker.quittances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
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
 * Tests d'intégration de la surface publique de vérification des quittances (EP-14b, US-102/104,
 * ADR-15 D5/D7) : contrat K2 strict, <strong>non-fuite</strong> des champs interdits, échec
 * <strong>indifférencié</strong> (aucun oracle), téléchargement conditionné à l'intégrité du PDF,
 * indication de la version remplaçante, compteurs et exposition RGPD au bailleur.
 *
 * <p>Le chemin testé n'est jamais authentifié (aucun {@code jwt()} sur les appels publics) : la
 * seule preuve d'autorisation est le token HMAC, forgé ici via {@link TokenQuittanceService} avec
 * le même secret que l'application.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class PublicQuittanceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;
    @Autowired
    TokenQuittanceService tokens;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE quittance, quittance_numerotation, quittance_verification_log,
                         audit_log, garantie, paiement, affectation, bail, bien, patrimoine,
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
        registry.add("quittances.hmac-secret", () -> "secret-hmac-de-test");
    }

    @Test
    void verificationValideRenvoieLeContratK2SansAucunChampInterdit() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        emettreQuittance(bailleur);
        Quittance q = quittanceEmise();
        String token = tokens.generer(q.id, q.version, q.contentHash);

        String corps = mockMvc.perform(get("/api/public/receipts/{id}", q.id).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("VALIDE"))
                .andExpect(jsonPath("$.quittance.numero").value(q.numero))
                .andExpect(jsonPath("$.quittance.statut").value("EMISE"))
                .andExpect(jsonPath("$.quittance.montantRecu").exists())
                .andExpect(jsonPath("$.quittance.contentHash").value(q.contentHash))
                .andReturn().getResponse().getContentAsString();

        // Non-fuite K2 (US-102) : le contenu certifié porte paiement.mode, garantie_retenue et
        // l'email du locataire — aucun ne doit transiter par la projection publique.
        assertThat(corps).doesNotContain("paiement", "mode", "garantie", "@test.local", "paiementId");

        // Compteur par quittance incrémenté, journal RGPD-minimal alimenté (US-104).
        assertThat(jdbc.queryForObject("SELECT nb_verifications FROM quittance", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT resultat FROM quittance_verification_log WHERE type_evenement = 'VERIFICATION'",
                String.class)).isEqualTo("VALIDE");
    }

    @Test
    void toutEchecDeVerificationEstIndifferencie() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        emettreQuittance(bailleur);
        Quittance q = quittanceEmise();
        String bon = tokens.generer(q.id, q.version, q.contentHash);

        // Id inconnu, token forgé, token d'une mauvaise version, token absent : réponse identique.
        verifierInvalide(UUID.randomUUID().toString(), bon);
        verifierInvalide(q.id.toString(), "jeton-forge");
        verifierInvalide(q.id.toString(), tokens.generer(q.id, q.version + 1, q.contentHash));
        verifierInvalide(q.id.toString(), null);

        assertThat(jdbc.queryForObject("SELECT nb_verifications FROM quittance", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM quittance_verification_log WHERE resultat = 'INVALIDE'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM quittance_verification_log
                WHERE resultat = 'INVALIDE' AND quittance_id IS NULL
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void telechargementSertLExemplaireStockeSiTokenEtIntegriteOk() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        emettreQuittance(bailleur);
        Quittance q = quittanceEmise();
        String token = tokens.generer(q.id, q.version, q.contentHash);
        byte[] stocke = jdbc.queryForObject("SELECT pdf FROM quittance", byte[].class);

        byte[] servi = mockMvc.perform(
                        get("/api/public/receipts/{id}/download", q.id).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(servi).isEqualTo(stocke);
        assertThat(jdbc.queryForObject("SELECT nb_telechargements FROM quittance", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void telechargementRefuseUnTokenInvalideOuUnPdfAltereEnBase() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        emettreQuittance(bailleur);
        Quittance q = quittanceEmise();
        String token = tokens.generer(q.id, q.version, q.contentHash);

        // Token invalide → 404 indifférencié.
        mockMvc.perform(get("/api/public/receipts/{id}/download", q.id).param("token", "faux"))
                .andExpect(status().isNotFound());

        // Altération du PDF en base sans toucher pdf_hash : le re-hash ne correspond plus → refus
        // de servir (défense contre une falsification de l'exemplaire stocké).
        jdbc.update("UPDATE quittance SET pdf = decode('deadbeef', 'hex')");
        mockMvc.perform(get("/api/public/receipts/{id}/download", q.id).param("token", token))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT nb_telechargements FROM quittance", Integer.class))
                .isZero();
    }

    @Test
    void uneQuittanceRemplaceeIndiqueSaVersionRemplacante() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = emettreQuittance(bailleur);
        Quittance v1 = quittanceEmise();
        String tokenV1 = tokens.generer(v1.id, v1.version, v1.contentHash);

        // Corriger l'adresse du bailleur change le contenu certifié : la ré-émission produit une v2
        // et bascule la v1 en REMPLACEE (chaînage remplacee_par).
        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"99 boulevard Corrigé, 75002 Paris\"}"))
                .andExpect(status().isOk());
        telechargerQuittance(bailleur, bienId, "2026-01");

        mockMvc.perform(get("/api/public/receipts/{id}", v1.id).param("token", tokenV1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("VALIDE"))
                .andExpect(jsonPath("$.quittance.statut").value("REMPLACEE"))
                .andExpect(jsonPath("$.quittance.remplacanteVersion").value(2));
    }

    @Test
    void lesCompteursSontExposesAuBailleurParLExportRgpd() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        emettreQuittance(bailleur);
        Quittance q = quittanceEmise();
        String token = tokens.generer(q.id, q.version, q.contentHash);

        mockMvc.perform(get("/api/public/receipts/{id}", q.id).param("token", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/receipts/{id}/download", q.id).param("token", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bailleurs/export").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quittances[0].nbVerifications").value(1))
                .andExpect(jsonPath("$.quittances[0].nbTelechargements").value(1));
    }

    // --- Helpers -----------------------------------------------------------------------------

    private void verifierInvalide(String id, String token) throws Exception {
        var requete = get("/api/public/receipts/{id}", id);
        if (token != null) {
            requete = requete.param("token", token);
        }
        mockMvc.perform(requete)
                .andExpect(status().isOk())
                .andExpect(content().json("{\"resultat\":\"INVALIDE\",\"quittance\":null}"));
    }

    private Quittance quittanceEmise() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, numero, version, content_hash FROM quittance WHERE statut = 'EMISE'");
        return new Quittance((UUID) row.get("id"), (String) row.get("numero"),
                ((Number) row.get("version")).intValue(), (String) row.get("content_hash"));
    }

    private record Quittance(UUID id, String numero, int version, String contentHash) {
    }

    /** Émet une quittance certifiée pour la période 2026-01 et renvoie l'id du bien. */
    private String emettreQuittance(String bailleur) throws Exception {
        inscrireBailleur(bailleur);
        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"10 rue du Bailleur, 75001 Paris\"}"))
                .andExpect(status().isOk());
        String bienId = creerBien(bailleur, "5 avenue du Bien " + UUID.randomUUID());
        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId).with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireNom\":\"Bob Martin\",\"locataireEmail\":\"bob@test.local\","
                                + "\"loyerHc\":800.00,\"provisionCharges\":50.00,"
                                + "\"dateDebut\":\"2026-01-01\",\"dateFin\":\"2026-01-31\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk());
        pointerRecu(bailleur, bienId, "2026-01", "850.00");
        telechargerQuittance(bailleur, bienId, "2026-01");
        return bienId;
    }

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerBien(String keycloakId, String adresse) throws Exception {
        String patrimoineId = JsonPath.read(mockMvc.perform(post("/api/patrimoines")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Patrimoine " + UUID.randomUUID()
                                + "\",\"adresse\":\"1 rue du Patrimoine\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"" + adresse + "\",\"type\":\"APPARTEMENT\","
                                + "\"statut\":\"LIBRE\",\"patrimoineId\":\"" + patrimoineId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void pointerRecu(String keycloakId, String bienId, String periode, String montant)
            throws Exception {
        mockMvc.perform(patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, periode)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantRecu\":" + montant + ",\"statut\":\"RECU\"}"))
                .andExpect(status().isOk());
    }

    private void telechargerQuittance(String keycloakId, String bienId, String periode)
            throws Exception {
        mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, periode)
                        .with(bailleurJwt(keycloakId)))
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
