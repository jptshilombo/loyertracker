package com.loyertracker.paiements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.loyertracker.baux.Devise;

public record PaiementDto(UUID id, UUID bienId, UUID bailId, String periode,
        BigDecimal montantAttendu, BigDecimal montantRecu, BigDecimal resteDu,
        LocalDate dateExigibilite, String statut, String devise) {

    /** Devise résolue via le bail parent (US-93, ADR-13) ; repli EUR si non résolue. */
    public static PaiementDto from(Paiement p, Devise devise) {
        return new PaiementDto(p.getId(), p.getBienId(), p.getBailId(), p.getPeriode(),
                p.getMontantAttendu(), p.getMontantRecu(), p.getResteDu(),
                p.getDateExigibilite(), p.getStatut().name(),
                devise != null ? devise.name() : Devise.EUR.name());
    }
}
