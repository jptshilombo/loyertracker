package com.loyertracker.paiements;

/** Statut de pointage d'un loyer mensuel (EF-30). */
public enum StatutPaiement {
    /** Loyer généré pour une période future, non encore exigible (US-60). */
    A_VENIR,
    IMPAYE,
    PARTIEL,
    RECU,
    EN_RETARD
}
