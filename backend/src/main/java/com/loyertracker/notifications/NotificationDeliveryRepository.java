package com.loyertracker.notifications;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    Optional<NotificationDelivery> findFirstByProviderMessageId(String providerMessageId);
}
