package com.loyertracker.patrimoine;

import java.util.UUID;

/** Vue API d'un patrimoine. */
public record PatrimoineDto(UUID id, String nom, String adresse, String statut) {

    public static PatrimoineDto from(Patrimoine patrimoine) {
        return new PatrimoineDto(patrimoine.getId(), patrimoine.getNom(),
                patrimoine.getAdresse(), patrimoine.getStatut().name());
    }
}
