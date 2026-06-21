package com.loyertracker.biens;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BienRequest(
        @NotBlank @Size(max = 500) String adresse,
        @NotBlank @Size(max = 50) String type,
        @NotNull StatutBien statut,
        @NotNull UUID patrimoineId) {
}
