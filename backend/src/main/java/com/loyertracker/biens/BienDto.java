package com.loyertracker.biens;

import java.util.UUID;

/** Vue API d'un bien locatif. */
public record BienDto(UUID id, String adresse, String type, String statut, UUID patrimoineId) {

    public static BienDto from(Bien bien) {
        return new BienDto(bien.getId(), bien.getAdresse(), bien.getType(), bien.getStatut().name(),
                bien.getPatrimoineId());
    }
}
