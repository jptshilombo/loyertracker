package com.loyertracker.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.loyertracker.notifications.provider.NoopNotificationProvider;
import com.loyertracker.notifications.provider.NotificationProvider;
import com.loyertracker.securite.TenantContext;
import com.loyertracker.testsupport.RlsTestDataSourceConfig;

/**
 * Tests d'intégration EP-16 Sprint N — Fondation (US-119/120/121, ADR-18). Couvre les deux voies
 * d'alimentation de l'Outbox (A : {@code generer_alertes()} ; B : écriture inline dans les services
 * métier), le consentement (K3 — absence de préférence = absence d'envoi), l'idempotence
 * (contrainte unique), la concurrence de réclamation ({@code FOR UPDATE SKIP LOCKED}), le rollback
 * transactionnel, et le démarrage sûr sans configuration Twilio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class NotificationFondationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;
    @Autowired
    NotificationOutboxService outboxService;
    @Autowired
    NotificationOutboxRepository outboxRepository;
    @Autowired
    PlatformTransactionManager txManager;
    @Autowired
    TenantContext tenant;
    @Autowired
    NotificationProvider provider;
    @Value("${app.notifications.external.enabled}")
    boolean externalEnabled;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE notification_outbox, notification_event, notification_preference,
                         notification_delivery, notification_template, quittance,
                         quittance_numerotation, quittance_verification_log, audit_log, alerte,
                         garantie, paiement, affectation, bail, locataire, bien, patrimoine,
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

    // --- Démarrage sûr sans configuration Twilio (US-121) -----------------------------------

    @Test
    void demarreSansConfigurationTwilioAvecLeFournisseurSandboxEtLesFlagsDesactives() {
        assertThat(provider).as("seul fournisseur disponible en Sprint N")
                .isInstanceOf(NoopNotificationProvider.class);
        assertThat(externalEnabled).as("NOTIFICATIONS_EXTERNAL_ENABLED par défaut (K8)").isFalse();
    }

    // --- Voie B : écriture inline transactionnelle (QuittanceCertifieeService) -------------

    @Test
    void quittanceDisponibleAvecConsentementWhatsappCreeUneLigneOutboxEligible() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-06-30");
        UUID bailleurId = idBailleur(bailleur);
        UUID locataireId = idLocataireDuBien(bienId);
        seedPreferenceWhatsapp(bailleurId, TypeDestinataire.LOCATAIRE, locataireId);

        telechargerQuittance(bailleur, bienId, "2026-01");

        assertThat(compter(
                "SELECT count(*) FROM notification_event WHERE event_type = 'QUITTANCE_DISPONIBLE'"))
                .isEqualTo(1);
        assertThat(compter("""
                SELECT count(*) FROM notification_outbox o
                JOIN notification_event e ON e.id = o.event_id
                WHERE e.event_type = 'QUITTANCE_DISPONIBLE'
                  AND o.recipient_id = CAST(? AS uuid)
                  AND o.channel = 'WHATSAPP' AND o.statut = 'PENDING'
                """, locataireId.toString())).isEqualTo(1);
    }

    @Test
    void quittanceDisponibleSansConsentementNeCreeAucuneLigneOutbox() throws Exception {
        // K3 : l'absence de NotificationPreference équivaut à une absence de consentement, jamais
        // à un envoi par défaut — même si le locataire a un numéro (hors périmètre ici, aucun n'en
        // a de toute façon en Sprint N).
        String bailleur = "kc-" + UUID.randomUUID();
        String bienId = loyerRecu(bailleur, "2026-01", "2026-06-30");

        telechargerQuittance(bailleur, bienId, "2026-01");

        assertThat(compter(
                "SELECT count(*) FROM notification_event WHERE event_type = 'QUITTANCE_DISPONIBLE'"))
                .isEqualTo(1);
        assertThat(compter("SELECT count(*) FROM notification_outbox")).isZero();
    }

    // --- Voie A : extension de generer_alertes() --------------------------------------------

    @Test
    void loyerEnRetardAvecConsentementBailleurCreeUneLigneOutbox() throws Exception {
        String bailleurKc = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurKc);
        String bienId = creerBien(bailleurKc, "1 rue Retard " + UUID.randomUUID());
        creerBailEcheances(bailleurKc, bienId, "2026-01-01", "2026-03-31");
        UUID bailleurId = idBailleur(bailleurKc);
        seedPreferenceWhatsapp(bailleurId, TypeDestinataire.BAILLEUR, bailleurId);

        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleurKc)))
                .andExpect(status().isOk());

        assertThat(compter("""
                SELECT count(*) FROM notification_outbox o
                JOIN notification_event e ON e.id = o.event_id
                WHERE e.event_type = 'LOYER_EN_RETARD' AND o.channel = 'WHATSAPP'
                """)).isGreaterThan(0);
    }

    @Test
    void loyerEnRetardSansConsentementBailleurNeCreeAucuneLigneOutbox() throws Exception {
        String bailleurKc = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurKc);
        String bienId = creerBien(bailleurKc, "2 rue Retard " + UUID.randomUUID());
        creerBailEcheances(bailleurKc, bienId, "2026-01-01", "2026-03-31");

        mockMvc.perform(post("/api/batch/alertes").with(bailleurJwt(bailleurKc)))
                .andExpect(status().isOk());

        assertThat(compter(
                "SELECT count(*) FROM notification_event WHERE event_type = 'LOYER_EN_RETARD'"))
                .isGreaterThan(0);
        assertThat(compter("SELECT count(*) FROM notification_outbox")).isZero();
    }

    // --- Idempotence (US-120, RSV-EP16-01/02) -----------------------------------------------

    @Test
    void contrainteUniqueEmpecheDeuxLignesOutboxIdentiques() {
        UUID bailleurId = seedBailleurMinimal();
        UUID eventId = seedEvent(bailleurId);
        UUID recipientId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Les deux inserts doivent être dans la même transaction : le GUC RLS (is_local=true) posé
        // par TenantContext.positionner ne survit pas au-delà de la transaction qui le positionne.
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            tenant.positionner(bailleurId);
            outboxRepository.saveAndFlush(new NotificationOutbox(bailleurId, eventId, recipientId,
                    TypeEvenementNotification.LOYER_EN_RETARD, CanalNotification.WHATSAPP));
            outboxRepository.saveAndFlush(new NotificationOutbox(bailleurId, eventId, recipientId,
                    TypeEvenementNotification.LOYER_EN_RETARD, CanalNotification.WHATSAPP));
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Rollback métier (US-120) ------------------------------------------------------------

    @Test
    void rollbackMetierNAucuneLignePersisteeApresEchecPostEmission() {
        UUID bailleurId = seedBailleurMinimal();
        UUID aggregateId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            tenant.positionner(bailleurId);
            outboxService.emettre(bailleurId, TypeEvenementNotification.BAIL_CREE,
                    TypeAgregatNotification.BAIL, aggregateId, Map.of(), List.of());
            throw new IllegalStateException("échec métier simulé après écriture notification");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(compter("SELECT count(*) FROM notification_event WHERE aggregate_id = CAST(? AS uuid)",
                aggregateId.toString())).isZero();
    }

    // --- Concurrence de réclamation (RSV-EP16-02) --------------------------------------------

    @Test
    void reclamerLotEstMutuellementExclusifSousConcurrence() throws Exception {
        UUID bailleurId = seedBailleurMinimal();
        UUID eventId = seedEvent(bailleurId);
        int total = 20;
        for (int i = 0; i < total; i++) {
            jdbc.update("""
                    INSERT INTO notification_outbox
                        (id, bailleur_id, event_id, recipient_id, notification_type, channel)
                    VALUES (gen_random_uuid(), ?, ?, ?, 'LOYER_EN_RETARD', 'WHATSAPP')
                    """, bailleurId, eventId, UUID.randomUUID());
        }

        TransactionTemplate tx = new TransactionTemplate(txManager);
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<UUID>>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    depart.await();
                    // positionner + reclamerLot dans la même transaction par thread (GUC RLS local).
                    return tx.execute(status -> {
                        tenant.positionner(bailleurId);
                        return outboxService.reclamerLot(total);
                    });
                }));
            }
            depart.countDown();
            List<UUID> tousReclames = new ArrayList<>();
            for (Future<List<UUID>> f : futures) {
                tousReclames.addAll(f.get());
            }
            // Chaque ligne réclamée par un seul thread (SKIP LOCKED) : aucune perte, aucun doublon.
            assertThat(tousReclames).hasSize(total);
            assertThat(new HashSet<>(tousReclames)).hasSize(total);
        } finally {
            executor.shutdown();
        }
        assertThat(compter("SELECT count(*) FROM notification_outbox WHERE statut = 'PROCESSING'"))
                .isEqualTo(total);
    }

    // --- Helpers -----------------------------------------------------------------------------

    private int compter(String sql, Object... params) {
        return jdbc.queryForObject(sql, Integer.class, params);
    }

    private UUID seedBailleurMinimal() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                id, "kc-" + id, id + "@test.local", "Nom", "Prenom");
        return id;
    }

    private UUID seedEvent(UUID bailleurId) {
        return UUID.fromString(jdbc.queryForObject("""
                INSERT INTO notification_event (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
                VALUES (?, 'LOYER_EN_RETARD', 'BAIL', gen_random_uuid(), '{}'::jsonb)
                RETURNING id
                """, String.class, bailleurId));
    }

    private void seedPreferenceWhatsapp(UUID bailleurId, TypeDestinataire type, UUID recipientId) {
        jdbc.update("""
                INSERT INTO notification_preference
                    (id, bailleur_id, recipient_type, recipient_id, phone_e164, preferred_channel,
                     whatsapp_opt_in, sms_opt_in, consent_at, consent_source, enabled)
                VALUES (gen_random_uuid(), ?, ?, ?, '+33600000000', 'WHATSAPP', true, false, now(),
                        'FORMULAIRE_LOYERTRACKER', true)
                """, bailleurId, type.name(), recipientId);
    }

    private UUID idBailleur(String keycloakId) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT id FROM bailleur WHERE keycloak_id = ?", String.class, keycloakId));
    }

    private UUID idLocataireDuBien(String bienId) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT locataire_id FROM bail WHERE bien_id = CAST(? AS uuid)", String.class, bienId));
    }

    private String loyerRecu(String bailleur, String premierePeriode, String finBail) throws Exception {
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
        mockMvc.perform(patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, premierePeriode)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montantRecu\":850.00,\"statut\":\"RECU\"}"))
                .andExpect(status().isOk());
        return bienId;
    }

    private void creerBailEcheances(String keycloakId, String bienId, String debut, String fin)
            throws Exception {
        String locataireId = creerLocataire(keycloakId);
        mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireId\":\"" + locataireId
                                + "\",\"loyerHc\":850.00,\"provisionCharges\":0.00,\"dateDebut\":\""
                                + debut + "\",\"dateFin\":\"" + fin + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
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

    private byte[] telechargerQuittance(String keycloakId, String bienId, String periode) throws Exception {
        return mockMvc.perform(get("/api/biens/{b}/paiements/{p}/quittance", bienId, periode)
                        .with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
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
