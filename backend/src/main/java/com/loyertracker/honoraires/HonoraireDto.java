package com.loyertracker.honoraires;

import java.math.BigDecimal;
import java.util.UUID;

import com.loyertracker.baux.Devise;

public record HonoraireDto(UUID id, UUID affectationId, String periode, BigDecimal montant,
        String statut, String devise) {

    /**
     * Devise résolue via le bail le plus récent du bien concerné (US-93, ADR-13) — approximation
     * documentée : {@code Honoraire} n'a pas de lien direct vers un bail (seulement
     * {@code affectationId}, parfois mutualisé sur tout un patrimoine multi-devises). Repli EUR si
     * non résolue.
     */
    public static HonoraireDto from(Honoraire h, Devise devise) {
        return new HonoraireDto(h.getId(), h.getAffectationId(), h.getPeriode(), h.getMontant(),
                h.getStatut().name(), devise != null ? devise.name() : Devise.EUR.name());
    }
}
