package com.loyertracker.patrimoine;

import java.util.UUID;

/** Vue API d'un patrimoine. */
public record PatrimoineDto(UUID id, String nom, String adresse, String ville, String commune,
        String quartier, String provinceEtat, String pays, String description,
        String referenceInterne, String statut) {

    public static PatrimoineDto from(Patrimoine patrimoine) {
        return new PatrimoineDto(patrimoine.getId(), patrimoine.getNom(), patrimoine.getAdresse(),
                patrimoine.getVille(), patrimoine.getCommune(), patrimoine.getQuartier(),
                patrimoine.getProvinceEtat(), patrimoine.getPays(), patrimoine.getDescription(),
                patrimoine.getReferenceInterne(), patrimoine.getStatut().name());
    }
}
