package com.loyertracker.notifications;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {

    List<NotificationEvent> findByAggregateIdOrderByDateCreationDesc(UUID aggregateId);
}
