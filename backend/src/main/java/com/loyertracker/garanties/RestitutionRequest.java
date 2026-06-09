package com.loyertracker.garanties;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Restitution d'une garantie (US-32/EF-41/42, Annexe A.5).
 * Pour une restitution {@code PARTIELLE}, {@code montantRetenu} (&gt; 0) et {@code motifRetenue}
 * sont requis (validés côté service).
 */
public record RestitutionRequest(
        @NotNull TypeRestitution type,
        @DecimalMin("0.00") BigDecimal montantRetenu,
        String motifRetenue) {
}
