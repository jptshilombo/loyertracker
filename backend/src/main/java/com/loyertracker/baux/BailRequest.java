package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Création d'un bail. Depuis V11, le loyer est ventilé : {@code loyerHc} (hors charges) et
 * {@code provisionCharges}. Le « charges comprises » est dérivé ({@code loyerHc + provisionCharges})
 * côté entité, jamais saisi, pour garantir la cohérence (et alimenter la quittance).
 */
public record BailRequest(
        @NotBlank @Size(max = 255) String locataireNom,
        @Email @Size(max = 320) String locataireEmail,
        @NotNull @DecimalMin("0.00") BigDecimal loyerHc,
        @NotNull @DecimalMin("0.00") BigDecimal provisionCharges,
        @NotNull @DecimalMin("0.00") BigDecimal depotGarantie,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin,
        Devise devise) {
}
