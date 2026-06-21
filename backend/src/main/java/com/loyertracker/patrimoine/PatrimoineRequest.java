package com.loyertracker.patrimoine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatrimoineRequest(@NotBlank @Size(max = 255) String nom) {
}
