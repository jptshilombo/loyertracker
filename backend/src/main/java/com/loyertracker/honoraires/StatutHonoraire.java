package com.loyertracker.honoraires;

/** Cycle de vie d'un honoraire de gestion (EF-50/52) : dû → en attente de paiement → payé (figé). */
public enum StatutHonoraire {
    DU,
    EN_ATTENTE,
    PAYE
}
