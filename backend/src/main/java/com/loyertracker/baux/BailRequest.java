package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BailRequest(
        @NotBlank @Size(max = 255) String locataireNom,
        @Email @Size(max = 320) String locataireEmail,
        @NotNull @DecimalMin("0.00") BigDecimal loyerCc,
        @NotNull @DecimalMin("0.00") BigDecimal depotGarantie,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin) {
}
