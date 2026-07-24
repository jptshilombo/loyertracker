package com.loyertracker.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyertracker.notifications.provider.NotificationProvider;
import com.loyertracker.notifications.provider.NotificationProvider.ResultatEnvoi;
import com.loyertracker.securite.TenantContext;
import com.loyertracker.testsupport.RlsTestDataSourceConfig;

import jakarta.persistence.EntityManager;

/**
 * Tests d'intégration EP-16 Sprint N+1 — WhatsApp P0 (US-122/123, ADR-18). Le {@link
 * NotificationDispatcher} sous test est reconstruit avec un {@link NotificationProvider} de test
 * contrôlable (le bean Spring actif reste {@code NoopNotificationProvider}, {@code
 * TWILIO_WHATSAPP_ENABLED} n'étant jamais activé en test) — mêmes collaborateurs réels
 * (repositories, {@code EntityManager}, {@link TenantContext}) que la production. Couvre les
 * critères GO du sprint : template non approuvé ⇒ {@code DEAD} sans envoi, échec transitoire ⇒
 * retry puis {@code DEAD}, callback signature invalide/dupliqué sans effet de bord.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class NotificationDispatchIntegrationTest {

    private static final String AUTH_TOKEN_TEST = "test-twilio-auth-token";
    private static final String CALLBACK_URL = "https://loyertracker.loyerpro.org/api/public/notifications/callback";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;
    @Autowired
    EntityManager em;
    @Autowired
    TenantContext tenant;
    @Autowired
    PlatformTransactionManager txManager;
    @Autowired
    NotificationOutboxService outboxService;
    @Autowired
    NotificationOutboxRepository outboxRepository;
    @Autowired
    NotificationPreferenceRepository preferenceRepository;
    @Autowired
    NotificationTemplateRepository templateRepository;
    @Autowired
    NotificationDeliveryService deliveryService;
    @Autowired
    NotificationDeliveryRepository deliveryRepository;
    @Autowired
    ObjectMapper json;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE notification_outbox, notification_delivery, notification_event,
                         notification_preference, notification_template, bailleur
                RESTART IDENTITY CASCADE
                """);
        seedTemplatesP0();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://localhost/auth/realms/loyertracker");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/realms/loyertracker/protocol/openid-connect/certs");
        registry.add("twilio.auth-token", () -> AUTH_TOKEN_TEST);
        registry.add("twilio.status-callback-base-url", () -> "https://loyertracker.loyerpro.org");
    }

    // --- NotificationDispatcher : envoi accepté ----------------------------------------------

    @Test
    void envoiAccepteCreeUneLivraisonEtMarqueLaLigneTraitee() {
        UUID bailleurId = seedBailleur();
        UUID eventId = seedEvent(bailleurId, "QUITTANCE_DISPONIBLE");
        UUID recipientId = UUID.randomUUID();
        seedPreference(bailleurId, recipientId);
        UUID outboxId = seedOutboxPending(bailleurId, eventId, recipientId, "QUITTANCE_DISPONIBLE");

        NotificationDispatcher dispatcher = dispatcherAvec(demande -> new ResultatEnvoi(true, "SID123", null));
        int traites = dispatcher.traiterLot(50);

        assertThat(traites).isEqualTo(1);
        assertThat(statutOutbox(outboxId)).isEqualTo("PROCESSED");
        assertThat(compter("SELECT count(*) FROM notification_delivery WHERE provider_message_id = 'SID123'"))
                .isEqualTo(1);
    }

    // --- Template non approuvé : critère GO explicite --------------------------------------

    @Test
    void templateNonApprouveMarqueLaLigneDeadSansAucunEnvoi() {
        jdbc.update("UPDATE notification_template SET approval_status = 'SOUMIS', enabled = false "
                + "WHERE code = 'LOYER_EN_RETARD'");
        UUID bailleurId = seedBailleur();
        UUID eventId = seedEvent(bailleurId, "LOYER_EN_RETARD");
        UUID recipientId = UUID.randomUUID();
        seedPreference(bailleurId, recipientId);
        UUID outboxId = seedOutboxPending(bailleurId, eventId, recipientId, "LOYER_EN_RETARD");

        NotificationDispatcher dispatcher = dispatcherAvec(
                demande -> { throw new AssertionError("le fournisseur ne doit jamais être appelé"); });
        dispatcher.traiterLot(50);

        assertThat(statutOutbox(outboxId)).isEqualTo("DEAD");
        assertThat(compter("SELECT count(*) FROM notification_delivery")).isZero();
    }

    // --- Échec transitoire : retry puis DEAD au-delà du plafond ----------------------------

    @Test
    void echecTransitoireRepasseEnRetryPuisDeadAuDelaDuPlafond() {
        UUID bailleurId = seedBailleur();
        UUID eventId = seedEvent(bailleurId, "GARANTIE_DEBITEE");
        UUID recipientId = UUID.randomUUID();
        seedPreference(bailleurId, recipientId);
        UUID outboxId = seedOutboxPending(bailleurId, eventId, recipientId, "GARANTIE_DEBITEE");

        NotificationDispatcher dispatcher = dispatcherAvec(
                demande -> new ResultatEnvoi(false, null, "ERREUR_TRANSPORT_TWILIO"), 2);

        dispatcher.traiterLot(50);
        assertThat(statutOutbox(outboxId)).isEqualTo("RETRY");
        assertThat(tentatives(outboxId)).isEqualTo(1);

        // Force la ré-éligibilité immédiate (backoff ignoré pour le test).
        jdbc.update("UPDATE notification_outbox SET next_attempt_at = now() WHERE id = ?", outboxId);
        dispatcher.traiterLot(50);

        assertThat(statutOutbox(outboxId)).isEqualTo("DEAD");
        assertThat(tentatives(outboxId)).isEqualTo(2);
        assertThat(compter("SELECT count(*) FROM notification_delivery")).isZero();
    }

    // --- Callback Twilio : signature invalide, sans effet de bord --------------------------

    @Test
    void callbackSignatureInvalideEstRejeteSansEffetDeBord() throws Exception {
        UUID bailleurId = seedBailleur();
        UUID eventId = seedEvent(bailleurId, "QUITTANCE_DISPONIBLE");
        creerDeliveryDirecte(bailleurId, eventId, "SID-CB-1");

        mockMvc.perform(post("/api/public/notifications/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("X-Twilio-Signature", "signature-totalement-invalide")
                        .param("MessageSid", "SID-CB-1")
                        .param("MessageStatus", "delivered"))
                .andExpect(status().isForbidden());

        assertThat(statutDelivery("SID-CB-1")).isEqualTo("QUEUED");
    }

    // --- Callback Twilio : signature valide fait progresser le statut ----------------------

    @Test
    void callbackSignatureValideFaitProgresserLeStatut() throws Exception {
        UUID bailleurId = seedBailleur();
        UUID eventId = seedEvent(bailleurId, "QUITTANCE_DISPONIBLE");
        creerDeliveryDirecte(bailleurId, eventId, "SID-CB-2");

        appelerCallbackSigne("SID-CB-2", "delivered", null).andExpect(status().isNoContent());

        assertThat(statutDelivery("SID-CB-2")).isEqualTo("DELIVERED");
    }

    // --- Callback Twilio : dupliqué, sans transition supplémentaire (idempotence) ----------

    @Test
    void callbackDupliqueNEntraineAucuneTransitionSupplementaire() throws Exception {
        UUID bailleurId = seedBailleur();
        UUID eventId = seedEvent(bailleurId, "QUITTANCE_DISPONIBLE");
        creerDeliveryDirecte(bailleurId, eventId, "SID-CB-3");

        appelerCallbackSigne("SID-CB-3", "delivered", null).andExpect(status().isNoContent());
        String deliveredAtApresPremier = jdbc.queryForObject(
                "SELECT delivered_at::text FROM notification_delivery WHERE provider_message_id = 'SID-CB-3'",
                String.class);

        // Callback dupliqué (même statut) : aucune transition supplémentaire, delivered_at inchangé.
        appelerCallbackSigne("SID-CB-3", "delivered", null).andExpect(status().isNoContent());
        String deliveredAtApresSecond = jdbc.queryForObject(
                "SELECT delivered_at::text FROM notification_delivery WHERE provider_message_id = 'SID-CB-3'",
                String.class);

        assertThat(statutDelivery("SID-CB-3")).isEqualTo("DELIVERED");
        assertThat(deliveredAtApresSecond).isEqualTo(deliveredAtApresPremier);
    }

    // --- Helpers -----------------------------------------------------------------------------

    /** Reconstruit un Dispatcher avec un fournisseur de test contrôlable, mêmes collaborateurs réels. */
    private NotificationDispatcher dispatcherAvec(NotificationProvider provider) {
        return dispatcherAvec(provider, 5);
    }

    private NotificationDispatcher dispatcherAvec(NotificationProvider provider, int maxTentatives) {
        return new NotificationDispatcher(em, tenant, txManager, outboxService, outboxRepository,
                preferenceRepository, templateRepository, deliveryService, provider, json, maxTentatives);
    }

    private ResultActions appelerCallbackSigne(String sid, String messageStatus, String errorCode)
            throws Exception {
        Map<String, String> parametres = errorCode == null
                ? Map.of("MessageSid", sid, "MessageStatus", messageStatus)
                : Map.of("MessageSid", sid, "MessageStatus", messageStatus, "ErrorCode", errorCode);
        String signature = signer(parametres);
        MockHttpServletRequestBuilder requete = post("/api/public/notifications/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Twilio-Signature", signature)
                .param("MessageSid", sid)
                .param("MessageStatus", messageStatus);
        if (errorCode != null) {
            requete = requete.param("ErrorCode", errorCode);
        }
        return mockMvc.perform(requete);
    }

    private static String signer(Map<String, String> parametres) throws NoSuchAlgorithmException, InvalidKeyException {
        StringBuilder donnees = new StringBuilder(CALLBACK_URL);
        parametres.keySet().stream().sorted().forEach(cle -> donnees.append(cle).append(parametres.get(cle)));
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(AUTH_TOKEN_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(donnees.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private int compter(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String statutOutbox(UUID id) {
        return jdbc.queryForObject("SELECT statut FROM notification_outbox WHERE id = ?", String.class, id);
    }

    private int tentatives(UUID id) {
        return jdbc.queryForObject("SELECT attempt_count FROM notification_outbox WHERE id = ?", Integer.class, id);
    }

    private String statutDelivery(String sid) {
        return jdbc.queryForObject(
                "SELECT statut FROM notification_delivery WHERE provider_message_id = ?", String.class, sid);
    }

    private void seedTemplatesP0() {
        jdbc.update("""
                INSERT INTO notification_template (code, channel, language, version, approval_status, enabled)
                VALUES ('QUITTANCE_DISPONIBLE', 'WHATSAPP', 'fr', 1, 'APPROUVE', true),
                       ('LOYER_EN_RETARD',      'WHATSAPP', 'fr', 1, 'APPROUVE', true),
                       ('GARANTIE_DEBITEE',     'WHATSAPP', 'fr', 1, 'APPROUVE', true)
                """);
    }

    private UUID seedBailleur() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bailleur (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                id, "kc-" + id, id + "@test.local", "Nom", "Prenom");
        return id;
    }

    private UUID seedEvent(UUID bailleurId, String eventType) {
        return UUID.fromString(jdbc.queryForObject("""
                INSERT INTO notification_event (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
                VALUES (?, ?, 'BAIL', gen_random_uuid(), '{"periode":"2026-01"}'::jsonb)
                RETURNING id
                """, String.class, bailleurId, eventType));
    }

    private void seedPreference(UUID bailleurId, UUID recipientId) {
        jdbc.update("""
                INSERT INTO notification_preference
                    (id, bailleur_id, recipient_type, recipient_id, phone_e164, preferred_channel,
                     whatsapp_opt_in, sms_opt_in, consent_at, consent_source, enabled)
                VALUES (gen_random_uuid(), ?, 'LOCATAIRE', ?, '+33600000000', 'WHATSAPP', true, false,
                        now(), 'FORMULAIRE_LOYERTRACKER', true)
                """, bailleurId, recipientId);
    }

    private UUID seedOutboxPending(UUID bailleurId, UUID eventId, UUID recipientId, String notificationType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_outbox
                    (id, bailleur_id, event_id, recipient_id, notification_type, channel)
                VALUES (?, ?, ?, ?, ?, 'WHATSAPP')
                """, id, bailleurId, eventId, recipientId, notificationType);
        return id;
    }

    private void creerDeliveryDirecte(UUID bailleurId, UUID eventId, String providerMessageId) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            tenant.positionner(bailleurId);
            deliveryService.creer(bailleurId, eventId, UUID.randomUUID(), CanalNotification.WHATSAPP,
                    providerMessageId);
        });
    }
}
