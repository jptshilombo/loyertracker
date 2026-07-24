package com.loyertracker.notifications;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    /** Le référentiel n'est pas RLS-scopé (global) : toujours accessible quel que soit le tenant. */
    List<NotificationTemplate> findByCodeAndChannelAndLanguageOrderByVersionDesc(String code,
            CanalNotification channel, String language);
}
