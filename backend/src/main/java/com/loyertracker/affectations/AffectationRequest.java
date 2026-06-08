package com.loyertracker.affectations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AffectationRequest(
        @NotNull UUID bienId,
        @NotNull UUID gestionnaireId,
        @NotNull TypeHonoraires typeHonoraires,
        @NotNull @DecimalMin("0.00") BigDecimal montantHonoraires,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin) {
}
