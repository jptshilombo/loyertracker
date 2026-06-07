package com.loyertracker.comptes;

/**
 * Cycle de vie d'une {@link Invitation} (EF-02/03). Les valeurs correspondent exactement à la
 * contrainte {@code CHECK} de la colonne {@code invitation.statut} (migration V1).
 */
public enum StatutInvitation {
    /** Émise, non encore acceptée, dans sa fenêtre de validité (72 h). */
    PENDING,
    /** Consommée : un compte gestionnaire a été créé/rattaché (usage unique). */
    ACCEPTED,
    /** Périmée : fenêtre de 72 h dépassée sans acceptation. */
    EXPIRED
}
