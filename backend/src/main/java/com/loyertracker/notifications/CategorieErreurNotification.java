package com.loyertracker.notifications;

/** Classification d'une erreur de livraison (US-123) — pilote la politique de retry de l'Outbox. */
public enum CategorieErreurNotification {
    /** Réseau, timeout, indisponibilité momentanée du fournisseur : nouvelle tentative possible. */
    TEMPORAIRE,
    /** Numéro invalide, opt-out, template rejeté : nouvelle tentative inutile. */
    PERMANENT
}
