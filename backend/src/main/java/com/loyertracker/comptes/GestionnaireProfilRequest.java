package com.loyertracker.comptes;

import jakarta.validation.constraints.Size;

/**
 * Requête de complétion/modification du profil métier d'un Gestionnaire (K1, ADR-16 : jamais la
 * création du compte technique, réservée à l'invitation).
 */
public record GestionnaireProfilRequest(
        @Size(max = 50) String telephone,
        String photoBase64,
        @Size(max = 2000) String observations) {
}
