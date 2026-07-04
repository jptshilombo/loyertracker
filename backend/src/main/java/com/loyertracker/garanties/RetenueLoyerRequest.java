package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Retenue explicite sur un loyer impayé (US-95, ADR-14 §5) — jamais un prélèvement automatique. */
public record RetenueLoyerRequest(
        @NotNull UUID paiementId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal montant) {
}
