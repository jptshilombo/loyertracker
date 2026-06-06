package com.loyertracker.bailleur;

import java.util.UUID;

/**
 * Vue exposée d'un bailleur (réponse d'inscription). N'expose pas {@code keycloakId} (donnée
 * d'identité technique interne).
 */
public record BailleurDto(UUID id, String email, String nom, String prenom) {

    static BailleurDto from(Bailleur b) {
        return new BailleurDto(b.getId(), b.getEmail(), b.getNom(), b.getPrenom());
    }
}
