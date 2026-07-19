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
 * Événement de notification recipient-agnostique (US-120, ADR-18 §Modèle) : le fan-out par
 * destinataire/canal se fait exclusivement dans {@link NotificationOutbox}. Mapping en lecture
 * seule (l'écriture passe par {@link NotificationOutboxService}, SQL natif) — la colonne
 * {@code payload_minimal} (JSONB) n'est pas mappée, même convention que {@code audit_log.details}
 * ({@link com.loyertracker.audit.AuditLog}).
 */
@Entity
@Table(name = "notification_event")
public class NotificationEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private TypeEvenementNotification eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 30)
    private TypeAgregatNotification aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "payload_version", nullable = false, updatable = false)
    private short payloadVersion;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    protected NotificationEvent() {
        // requis par JPA ; écriture exclusivement via SQL natif (NotificationOutboxService)
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public TypeEvenementNotification getEventType() { return eventType; }
    public TypeAgregatNotification getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public short getPayloadVersion() { return payloadVersion; }
    public OffsetDateTime getDateCreation() { return dateCreation; }
}
