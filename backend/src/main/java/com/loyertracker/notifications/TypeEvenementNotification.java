package com.loyertracker.notifications;

/**
 * Type d'événement notifiable (ADR-18 §2). Voie A (batch, {@code generer_alertes()}) :
 * {@link #LOYER_EN_RETARD}/{@link #FIN_BAIL}/{@link #PREAVIS}/{@link #GARANTIE_NON_RESTITUEE}.
 * Voie B (transactionnelle, écriture inline) : {@link #QUITTANCE_DISPONIBLE}/
 * {@link #GARANTIE_DEBITEE}/{@link #PAIEMENT_RECU}/{@link #BAIL_CREE}/{@link #BAIL_CLOS}.
 */
public enum TypeEvenementNotification {
    QUITTANCE_DISPONIBLE,
    LOYER_EN_RETARD,
    FIN_BAIL,
    PREAVIS,
    GARANTIE_NON_RESTITUEE,
    GARANTIE_DEBITEE,
    PAIEMENT_RECU,
    BAIL_CREE,
    BAIL_CLOS
}
