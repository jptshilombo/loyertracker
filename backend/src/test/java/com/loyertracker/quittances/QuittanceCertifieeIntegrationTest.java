package com.loyertracker.quittances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
 * Tests d'intégration des quittances certifiées (EP-14a, US-99/100/101, ADR-15) : émission
 * persistante via le contrat HTTP historique, idempotence, ré-émission versionnée, annulation,
 * numérotation par bailleur+année (dont concurrence), cloisonnement RLS et audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class QuittanceCertifieeIntegrationTest {

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
                TRUNCATE quittance, quittance_numerotation, quittance_verification_log,
                         audit_log, garantie, paiement, affectation, bail, locataire, bien,
                         patrimoine, invitation, bailleur, gestionnaire
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
    void emissionPersisteLExemplaireOfficielNumeroteEtHache() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-01-31");

        byte[] pdf = telechargerQuittance(bailleur, bienId, "2026-01");
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf).hasSizeGreaterThan(10_000); // logo + QR embarqués : jamais un PDF squelette

        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM quittance");
        assertThat(q).containsEntry("numero", "QT-" + LocalDate.now().getYear() + "-000001")
                .containsEntry("version", 1)
                .containsEntry("statut", "EMISE")
                .containsEntry("remplacee_par", null);
        assertThat(((Number) q.get("token_kid")).intValue()).isEqualTo(1);
        // L'exemplaire servi EST l'exemplaire stocké, et les empreintes sont recalculables.
        assertThat((byte[]) q.get("pdf")).isEqualTo(pdf);
        assertThat(q).containsEntry("pdf_hash", ContenuQuittance.sha256Hex(pdf))
                .containsEntry("content_hash",
                        ContenuQuittance.sha256Hex((String) q.get("contenu")));
        assertThat((String) q.get("contenu")).startsWith("{\"schema\":1");

        assertThat(actionsAuditees()).containsExactly("EMETTRE_QUITTANCE");
    }

    @Test
    void redemanderUnLoyerInchangeRenvoieLeMemeExemplaireSansNouvelleVersion() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-01-31");

        byte[] premier = telechargerQuittance(bailleur, bienId, "2026-01");
        byte[] second = telechargerQuittance(bailleur, bienId, "2026-01");

        assertThat(second).isEqualTo(premier);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quittance", Integer.class))
                .isEqualTo(1);
        assertThat(actionsAuditees()).containsExactly("EMETTRE_QUITTANCE");
    }

    @Test
    void loyerModifieProduitUneVersionSuivanteChaineeSousLeMemeNumero() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-01-31");
        telechargerQuittance(bailleur, bienId, "2026-01");

        // L'adresse du bailleur fait partie du contenu certifié : sa correction change
        // l'empreinte métier (un loyer soldé ne peut plus être re-pointé, lui).
        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"99 boulevard Corrigé, 75002 Paris\"}"))
                .andExpect(status().isOk());
        byte[] v2 = telechargerQuittance(bailleur, bienId, "2026-01");
        assertThat(new String(v2, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        List<Map<String, Object>> versions =
                jdbc.queryForList("SELECT * FROM quittance ORDER BY version");
        assertThat(versions).hasSize(2);
        Map<String, Object> ancienne = versions.get(0);
        Map<String, Object> remplacante = versions.get(1);

        // Même numéro permanent, version incrémentée, chaînage remplacee_par (US-99).
        assertThat(remplacante).containsEntry("numero", ancienne.get("numero"))
                .containsEntry("version", 2)
                .containsEntry("statut", "EMISE");
        assertThat(ancienne).containsEntry("statut", "REMPLACEE")
                .containsEntry("remplacee_par", remplacante.get("id"));
        assertThat(actionsAuditees())
                .containsExactly("EMETTRE_QUITTANCE", "REEMETTRE_QUITTANCE");
    }

    @Test
    void annulationConsommeLeNumeroAJamais() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-01-31");
        telechargerQuittance(bailleur, bienId, "2026-01");
        UUID quittanceId = jdbc.queryForObject("SELECT id FROM quittance", UUID.class);

        mockMvc.perform(post("/api/quittances/{id}/annulation", quittanceId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT statut FROM quittance", String.class))
                .isEqualTo("ANNULEE");

        // Une quittance non EMISE ne peut pas être annulée une seconde fois.
        mockMvc.perform(post("/api/quittances/{id}/annulation", quittanceId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isConflict());

        // Redemander la quittance ré-émet sous un NOUVEAU numéro : un numéro consommé n'est
        // jamais réutilisé, même annulé (K1).
        telechargerQuittance(bailleur, bienId, "2026-01");
        assertThat(jdbc.queryForList("SELECT numero FROM quittance ORDER BY numero", String.class))
                .containsExactly(
                        "QT-" + LocalDate.now().getYear() + "-000001",
                        "QT-" + LocalDate.now().getYear() + "-000002");
        assertThat(actionsAuditees()).containsExactly(
                "EMETTRE_QUITTANCE", "ANNULER_QUITTANCE", "EMETTRE_QUITTANCE");
    }

    @Test
    void annulationCrossTenantEstMasqueeParLaRls() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bienA = loyerRecu(bailleurA, "2026-01", "2026-01-31");
        telechargerQuittance(bailleurA, bienA, "2026-01");
        UUID quittanceId = jdbc.queryForObject("SELECT id FROM quittance", UUID.class);

        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurB);

        // 404 indifférencié : la RLS masque l'existence même de la quittance d'un autre tenant.
        mockMvc.perform(post("/api/quittances/{id}/annulation", quittanceId)
                        .with(bailleurJwt(bailleurB)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT statut FROM quittance", String.class))
                .isEqualTo("EMISE");

        // Un gestionnaire n'est jamais habilité à annuler (réservé au bailleur propriétaire).
        mockMvc.perform(post("/api/quittances/{id}/annulation", quittanceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void laNumerotationParBailleurEtAnneeEstStrictementCroissante() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-02-28");
        pointerRecu(bailleur, bienId, "2026-02", "850.00");

        telechargerQuittance(bailleur, bienId, "2026-01");
        telechargerQuittance(bailleur, bienId, "2026-02");

        // Un autre bailleur démarre son propre compteur à 000001 (numérotation par bailleur, K1).
        String autre = "kc-" + UUID.randomUUID();
        String bienAutre = loyerRecu(autre, "2026-01", "2026-01-31");
        telechargerQuittance(autre, bienAutre, "2026-01");

        int annee = LocalDate.now().getYear();
        assertThat(jdbc.queryForList("SELECT numero FROM quittance ORDER BY emise_le, numero",
                String.class))
                .containsExactlyInAnyOrder("QT-" + annee + "-000001", "QT-" + annee + "-000002",
                        "QT-" + annee + "-000001");
    }

    @Test
    void deuxEmissionsSimultaneesObtiennentDesNumerosDistincts() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-02-28");
        pointerRecu(bailleur, bienId, "2026-02", "850.00");

        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> janvier = executor.submit(() -> {
                depart.await();
                return telechargerQuittance(bailleur, bienId, "2026-01");
            });
            Future<byte[]> fevrier = executor.submit(() -> {
                depart.await();
                return telechargerQuittance(bailleur, bienId, "2026-02");
            });
            depart.countDown();
            janvier.get();
            fevrier.get();
        } finally {
            executor.shutdown();
        }

        // Le verrou de ligne du compteur V22 garantit deux numéros distincts sans trou ici.
        assertThat(jdbc.queryForList("SELECT numero FROM quittance ORDER BY numero", String.class))
                .containsExactly(
                        "QT-" + LocalDate.now().getYear() + "-000001",
                        "QT-" + LocalDate.now().getYear() + "-000002");
    }

    // --- Helpers (calqués sur le harnais Documents/S03) --------------------------------------

    /** Inscrit le bailleur (adresse comprise), crée bien+bail, génère et solde le loyer de la 1re période. */
    private String loyerRecu(String bailleur, String premierePeriode, String finBail)
            throws Exception {
        inscrireBailleur(bailleur);
        mockMvc.perform(put("/api/bailleurs/profil").with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"10 rue du Bailleur, 75001 Paris\"}"))
                .andExpect(status().isOk());
        String bienId = creerBien(bailleur, "5 avenue du Bien " + UUID.randomUUID());
        String locataireId = creerLocataire(bailleur);
        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireId\":\"" + locataireId
                                + "\",\"loyerHc\":800.00,\"provisionCharges\":50.00,"
                                + "\"dateDebut\":\"" + premierePeriode + "-01\",\"dateFin\":\"" + finBail + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk());
        pointerRecu(bailleur, bienId, premierePeriode, "850.00");
        return bienId;
    }

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerLocataire(String keycloakId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/locataires").with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Bob Martin\",\"email\":\"bob@test.local\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
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

    private byte[] telechargerQuittance(String keycloakId, String bienId, String periode)
            throws Exception {
        return mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, periode)
                        .with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private List<String> actionsAuditees() {
        return jdbc.queryForList(
                "SELECT action FROM audit_log WHERE entity_type = 'quittance' ORDER BY horodatage",
                String.class);
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
