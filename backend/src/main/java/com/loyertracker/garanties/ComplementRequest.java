package com.loyertracker.garanties;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Réapprovisionnement d'une garantie active (US-96). */
public record ComplementRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal montant,
        @NotBlank String motif) {
}
