package com.loyertracker.notifications;

/** Cycle de vie interne d'une ligne {@code notification_outbox} (ADR-18 §Statuts). */
public enum StatutOutbox {
    PENDING,
    PROCESSING,
    PROCESSED,
    RETRY,
    DEAD
}
