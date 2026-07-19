package com.loyertracker.notifications;

/** Canal de notification (ADR-18 §6) : IN_APP obligatoire (K2), WHATSAPP principal, SMS secours. */
public enum CanalNotification {
    IN_APP,
    WHATSAPP,
    SMS
}
