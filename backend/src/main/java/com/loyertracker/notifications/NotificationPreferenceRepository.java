package com.loyertracker.notifications;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByBailleurIdAndRecipientTypeAndRecipientId(
            UUID bailleurId, TypeDestinataire recipientType, UUID recipientId);
}
