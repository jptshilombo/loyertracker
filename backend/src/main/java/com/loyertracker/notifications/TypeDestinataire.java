package com.loyertracker.notifications;

/** Nature polymorphe d'un destinataire de notification (ADR-18, patron Alerte/AuditLog). */
public enum TypeDestinataire {
    BAILLEUR,
    GESTIONNAIRE,
    LOCATAIRE
}
