package com.loyertracker.comptes;

/**
 * Statut de cycle de vie du compte Gestionnaire (ADR-16 D1, EP-15).
 *
 * <p><strong>Portée globale</strong> : ce statut est partagé par tous les bailleurs qui
 * emploient ce gestionnaire (décision PO 2026-07-08) — pas un statut par relation
 * bailleur-gestionnaire (celle-ci reste portée par {@code Affectation}).</p>
 */
public enum StatutGestionnaire {
    ACTIVE,
    SUSPENDU,
    ARCHIVE
}
