package com.loyertracker.alertes;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlerteDto(UUID id, String type, UUID bienId, UUID bailId, String periode,
        String message, String statut, OffsetDateTime dateCreation, OffsetDateTime dateLecture) {

    public static AlerteDto from(Alerte a) {
        return new AlerteDto(a.getId(), a.getType().name(), a.getBienId(), a.getBailId(),
                a.getPeriode(), a.getMessage(), a.getStatut().name(), a.getDateCreation(),
                a.getDateLecture());
    }
}
