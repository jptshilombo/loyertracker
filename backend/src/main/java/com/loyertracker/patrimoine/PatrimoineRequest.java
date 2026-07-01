package com.loyertracker.patrimoine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatrimoineRequest(
        @NotBlank @Size(max = 255) String nom,
        @NotBlank @Size(max = 255) String adresse,
        @Size(max = 255) String ville,
        @Size(max = 255) String commune,
        @Size(max = 255) String quartier,
        @Size(max = 255) String provinceEtat,
        @Size(max = 255) String pays,
        String description,
        @Size(max = 100) String referenceInterne) {
}
