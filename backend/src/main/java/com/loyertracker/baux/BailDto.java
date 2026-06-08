package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BailDto(UUID id, UUID bienId, String locataireNom, String locataireEmail,
        BigDecimal loyerCc, BigDecimal depotGarantie, LocalDate dateDebut, LocalDate dateFin,
        String statut) {

    public static BailDto from(Bail bail) {
        return new BailDto(bail.getId(), bail.getBienId(), bail.getLocataireNom(),
                bail.getLocataireEmail(), bail.getLoyerCc(), bail.getDepotGarantie(),
                bail.getDateDebut(), bail.getDateFin(), bail.getStatut().name());
    }
}
