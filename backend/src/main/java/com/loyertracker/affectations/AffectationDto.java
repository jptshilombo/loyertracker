package com.loyertracker.affectations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AffectationDto(UUID id, UUID bienId, UUID gestionnaireId, String typeHonoraires,
        BigDecimal montantHonoraires, LocalDate dateDebut, LocalDate dateFin, String statut,
        OffsetDateTime dateRevocation) {

    public static AffectationDto from(Affectation affectation) {
        return new AffectationDto(affectation.getId(), affectation.getBienId(),
                affectation.getGestionnaireId(), affectation.getTypeHonoraires().name(),
                affectation.getMontantHonoraires(), affectation.getDateDebut(),
                affectation.getDateFin(), affectation.getStatut().name(),
                affectation.getDateRevocation());
    }
}
