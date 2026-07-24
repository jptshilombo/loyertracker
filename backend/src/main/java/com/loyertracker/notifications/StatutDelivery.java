package com.loyertracker.notifications;

/**
 * Cycle de vie d'une {@link NotificationDelivery} (US-123, ADR-18 §Ledger) — reflète les statuts de
 * callback Twilio (Message Status Callback). Ordre de progression normal :
 * {@link #QUEUED} → {@link #ACCEPTED} → {@link #SENT} → {@link #DELIVERED} → {@link #READ}.
 * {@link #FAILED}/{@link #UNDELIVERED}/{@link #CANCELLED} sont terminaux. La logique de
 * progression (idempotence des callbacks dupliqués/hors ordre) est appliquée en base par la
 * fonction {@code SECURITY DEFINER notification_delivery_appliquer_statut} (V28) — le callback
 * arrive sans contexte tenant (aucun bailleur authentifié), donc hors de portée de la RLS
 * applicative, même patron que {@code lire_quittance_publique} (V22).
 */
public enum StatutDelivery {
    QUEUED,
    ACCEPTED,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    UNDELIVERED,
    CANCELLED
}
