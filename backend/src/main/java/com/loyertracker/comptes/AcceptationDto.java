package com.loyertracker.comptes;

import java.util.UUID;

/**
 * Réponse à l'acceptation d'une invitation (US-12).
 *
 * @param gestionnaireId id applicatif du gestionnaire (créé ou réutilisé)
 * @param email e-mail du compte
 * @param compteCree {@code true} si le compte IdP a été créé, {@code false} s'il a été réutilisé (EF-05)
 */
public record AcceptationDto(UUID gestionnaireId, String email, boolean compteCree) {

    static AcceptationDto from(Gestionnaire gestionnaire, boolean compteCree) {
        return new AcceptationDto(gestionnaire.getId(), gestionnaire.getEmail(), compteCree);
    }
}
