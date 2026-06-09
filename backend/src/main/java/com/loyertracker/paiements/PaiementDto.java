package com.loyertracker.paiements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaiementDto(UUID id, UUID bienId, UUID bailId, String periode,
        BigDecimal montantAttendu, BigDecimal montantRecu, BigDecimal resteDu,
        LocalDate dateExigibilite, String statut) {

    public static PaiementDto from(Paiement p) {
        return new PaiementDto(p.getId(), p.getBienId(), p.getBailId(), p.getPeriode(),
                p.getMontantAttendu(), p.getMontantRecu(), p.getResteDu(),
                p.getDateExigibilite(), p.getStatut().name());
    }
}
