package com.loyertracker.quittances;

/**
 * Cycle de vie d'une quittance certifiée (ADR-15, kickoff K3 : pas de BROUILLON — une quittance
 * n'est émise que pour un loyer intégralement reçu).
 */
public enum StatutQuittance {
    /** Exemplaire officiel courant. Au plus une par loyer (index partiel V22). */
    EMISE,
    /** Annulée par le bailleur. Le numéro n'est jamais réutilisé. */
    ANNULEE,
    /** Remplacée par une version supérieure ({@code remplaceePar}). */
    REMPLACEE
}
