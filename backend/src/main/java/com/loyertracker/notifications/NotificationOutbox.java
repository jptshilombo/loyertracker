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
 * Ligne de la file d'attente transactionnelle des notifications externes (US-120, ADR-18 §3/§4).
 * Écrite dans la même transaction que l'opération métier (voie B) ou par {@code generer_alertes()}
 * (voie A) — jamais d'appel réseau à l'écriture. Consommée par le futur {@code NotificationDispatcher}
 * (Sprint N+1, {@code SELECT ... FOR UPDATE SKIP LOCKED}) : {@link NotificationOutboxService#reclamerLot}
 * pose déjà ce verrouillage non bloquant pour le prouver dès ce sprint.
 */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

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
    @Column(name = "notification_type", nullable = false, updatable = false, length = 40)
    private TypeEvenementNotification notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CanalNotification channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutOutbox statut;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    protected NotificationOutbox() {
        // requis par JPA
    }

    public NotificationOutbox(UUID bailleurId, UUID eventId, UUID recipientId,
            TypeEvenementNotification notificationType, CanalNotification channel) {
        this.id = UUID.randomUUID();
        this.bailleurId = bailleurId;
        this.eventId = eventId;
        this.recipientId = recipientId;
        this.notificationType = notificationType;
        this.channel = channel;
        this.statut = StatutOutbox.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getEventId() { return eventId; }
    public UUID getRecipientId() { return recipientId; }
    public TypeEvenementNotification getNotificationType() { return notificationType; }
    public CanalNotification getChannel() { return channel; }
    public StatutOutbox getStatut() { return statut; }
    public int getAttemptCount() { return attemptCount; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getLockedAt() { return lockedAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public OffsetDateTime getDateCreation() { return dateCreation; }
}
