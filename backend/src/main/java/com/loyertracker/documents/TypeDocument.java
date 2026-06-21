package com.loyertracker.documents;

/**
 * Document locatif généré à la volée (jamais stocké, arbitrage C) à partir d'un loyer.
 *
 * <ul>
 *   <li>{@link #QUITTANCE} — atteste un loyer intégralement reçu ({@code RECU}).</li>
 *   <li>{@link #AVIS_ECHEANCE} — notifie un loyer dû/non soldé (avant ou après échéance).</li>
 * </ul>
 */
public enum TypeDocument {
    QUITTANCE,
    AVIS_ECHEANCE
}
