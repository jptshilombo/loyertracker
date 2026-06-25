package com.loyertracker.affectations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AffectationRequest(
        UUID bienId,
        UUID patrimoineId,
        @NotNull UUID gestionnaireId,
        @NotNull TypeHonoraires typeHonoraires,
        @NotNull @DecimalMin("0.00") BigDecimal montantHonoraires,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin,
        TypeException typeException) {

    public boolean aExactementUnPerimetre() {
        return (bienId != null) ^ (patrimoineId != null);
    }

    /** US-85/RS-04 : une exception n'a de sens qu'en présence d'un {@code bienId}. */
    public boolean exceptionSansBien() {
        return bienId == null && typeException != null;
    }
}
