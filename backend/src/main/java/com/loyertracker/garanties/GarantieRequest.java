package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Enregistrement d'un dépôt de garantie (US-32/EF-40). */
public record GarantieRequest(
        @NotNull @DecimalMin("0.00") BigDecimal montant,
        @NotBlank @Size(max = 50) String typeGarantie,
        @NotNull LocalDate dateDepot) {
}
