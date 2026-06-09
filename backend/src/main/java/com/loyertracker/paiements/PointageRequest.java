package com.loyertracker.paiements;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Pointage d'un loyer mensuel (US-31/EF-30/32) : montant encaissé + statut décidé par l'acteur. */
public record PointageRequest(
        @NotNull @DecimalMin("0.00") BigDecimal montantRecu,
        @NotNull StatutPaiement statut) {
}
