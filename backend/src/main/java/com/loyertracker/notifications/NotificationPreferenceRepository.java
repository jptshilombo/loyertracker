package com.loyertracker.notifications;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByBailleurIdAndRecipientTypeAndRecipientId(
            UUID bailleurId, TypeDestinataire recipientType, UUID recipientId);

    /**
     * Variante sans {@code recipientType} (US-122, {@link NotificationDispatcher}) : {@code
     * notification_outbox} ne porte que {@code recipient_id}, jamais le type. Les identifiants de
     * destinataire étant des UUID générés indépendamment par table (bailleur/gestionnaire/locataire),
     * une collision inter-types est négligeable en pratique — même hypothèse que le fan-out à
     * l'émission ({@link NotificationOutboxService#emettre}).
     */
    Optional<NotificationPreference> findFirstByBailleurIdAndRecipientId(UUID bailleurId,
            UUID recipientId);
}
