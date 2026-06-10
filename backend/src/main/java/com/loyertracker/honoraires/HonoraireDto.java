package com.loyertracker.honoraires;

import java.math.BigDecimal;
import java.util.UUID;

public record HonoraireDto(UUID id, UUID affectationId, String periode, BigDecimal montant,
        String statut) {

    public static HonoraireDto from(Honoraire h) {
        return new HonoraireDto(h.getId(), h.getAffectationId(), h.getPeriode(), h.getMontant(),
                h.getStatut().name());
    }
}
