package com.loyertracker.notifications;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Suivi de livraison d'un envoi externe réellement tenté auprès du fournisseur (US-123, ADR-18
 * §Ledger) — créé uniquement lorsque {@link com.loyertracker.notifications.provider.NotificationProvider#envoyer}
 * a effectivement accepté la demande (jamais pour un template non approuvé ou un échec immédiat du
 * fournisseur, qui restent au niveau de {@link NotificationOutbox} seul). La création se fait en
 * JPA (contexte tenant déjà positionné par {@link NotificationDispatcher}) ; la mise à jour par
 * callback Twilio se fait exclusivement via la fonction {@code SECURITY DEFINER
 * notification_delivery_appliquer_statut} (V28, {@link NotificationDeliveryService#appliquerCallback})
 * — le callback arrive sans authentification ni contexte bailleur, donc hors de portée de la RLS
 * applicative (même patron que {@code lire_quittance_publique}, V22) ; cette entité n'expose donc
 * aucune méthode de mutation de statut.
 */
@Entity
@Table(name = "notification_delivery")
public class NotificationDelivery {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CanalNotification channel;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutDelivery statut;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", length = 20)
    private CategorieErreurNotification errorCategory;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    protected NotificationDelivery() {
        // requis par JPA
    }

    public NotificationDelivery(UUID bailleurId, UUID eventId, UUID recipientId,
            CanalNotification channel, String providerMessageId) {
        this.id = UUID.randomUUID();
        this.bailleurId = bailleurId;
        this.eventId = eventId;
        this.recipientId = recipientId;
        this.channel = channel;
        this.provider = "TWILIO";
        this.providerMessageId = providerMessageId;
        this.statut = StatutDelivery.QUEUED;
        this.attemptCount = 1;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getEventId() { return eventId; }
    public UUID getRecipientId() { return recipientId; }
    public CanalNotification getChannel() { return channel; }
    public String getProvider() { return provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public StatutDelivery getStatut() { return statut; }
    public int getAttemptCount() { return attemptCount; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getReadAt() { return readAt; }
    public OffsetDateTime getFailedAt() { return failedAt; }
    public String getErrorCode() { return errorCode; }
    public CategorieErreurNotification getErrorCategory() { return errorCategory; }
    public OffsetDateTime getDateCreation() { return dateCreation; }
}
