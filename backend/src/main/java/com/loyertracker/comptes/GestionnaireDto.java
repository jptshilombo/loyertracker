package com.loyertracker.comptes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vue API d'un Gestionnaire (EP-15). La photo n'est jamais embarquée en base64 dans cette vue
 * (poids, cohérent avec l'absence de vue « liste » alourdie) — seule sa présence est signalée.
 */
public record GestionnaireDto(UUID id, String email, String nom, String prenom, String statut,
        String telephone, boolean photoPresente, String observations, OffsetDateTime dateCreation,
        OffsetDateTime dateSuspension, OffsetDateTime dateArchivage) {

    public static GestionnaireDto from(Gestionnaire g) {
        return new GestionnaireDto(g.getId(), g.getEmail(), g.getNom(), g.getPrenom(),
                g.getStatut().name(), g.getTelephone(), g.getPhoto() != null, g.getObservations(),
                g.getDateCreation(), g.getDateSuspension(), g.getDateArchivage());
    }
}
