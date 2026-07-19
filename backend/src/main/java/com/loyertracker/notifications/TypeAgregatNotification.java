package com.loyertracker.notifications;

/** Type de l'agrégat métier à l'origine d'un {@link NotificationEvent} (ADR-18 §Modèle). */
public enum TypeAgregatNotification {
    BAIL,
    GARANTIE,
    QUITTANCE,
    PAIEMENT
}
