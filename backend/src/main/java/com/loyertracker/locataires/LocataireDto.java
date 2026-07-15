package com.loyertracker.locataires;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Vue API d'un Locataire. La photo n'est jamais embarquée en base64 — seule sa présence l'est. */
public record LocataireDto(UUID id, String nom, String prenom, String telephone, String email,
        String profession, LocalDate dateNaissance, String typePieceIdentite,
        String numeroPieceIdentite, boolean photoPresente, String contactUrgence,
        String observations, String statut, OffsetDateTime dateCreation, OffsetDateTime dateArchivage) {

    public static LocataireDto from(Locataire l) {
        return new LocataireDto(l.getId(), l.getNom(), l.getPrenom(), l.getTelephone(), l.getEmail(),
                l.getProfession(), l.getDateNaissance(), l.getTypePieceIdentite(),
                l.getNumeroPieceIdentite(), l.getPhoto() != null, l.getContactUrgence(),
                l.getObservations(), l.getStatut().name(), l.getDateCreation(), l.getDateArchivage());
    }
}
