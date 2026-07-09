package com.loyertracker.locataires;

/**
 * Statut de cycle de vie du Locataire (ADR-16 D2, EP-15). Contrairement au Gestionnaire, aucun
 * état {@code SUSPENDU} : le besoin métier PO ne l'exige pas pour le Locataire.
 */
public enum StatutLocataire {
    ACTIVE,
    ARCHIVE
}
