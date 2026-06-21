package com.loyertracker.bailleur;

import java.util.UUID;

/**
 * Vue exposée d'un bailleur (réponse d'inscription et profil). N'expose pas {@code keycloakId}
 * (donnée d'identité technique interne). L'{@code adresse} (V11) peut être {@code null} tant que le
 * bailleur ne l'a pas renseignée.
 */
public record BailleurDto(UUID id, String email, String nom, String prenom, String adresse) {

    static BailleurDto from(Bailleur b) {
        return new BailleurDto(b.getId(), b.getEmail(), b.getNom(), b.getPrenom(), b.getAdresse());
    }
}
