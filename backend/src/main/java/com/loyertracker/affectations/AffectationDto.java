package com.loyertracker.affectations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AffectationDto(UUID id, UUID bienId, UUID patrimoineId, UUID gestionnaireId,
        String typeHonoraires, BigDecimal montantHonoraires, LocalDate dateDebut, LocalDate dateFin,
        String statut, OffsetDateTime dateRevocation, String typeException) {

    public static AffectationDto from(Affectation affectation) {
        return new AffectationDto(affectation.getId(), affectation.getBienId(), affectation.getPatrimoineId(),
                affectation.getGestionnaireId(), affectation.getTypeHonoraires().name(),
                affectation.getMontantHonoraires(), affectation.getDateDebut(),
                affectation.getDateFin(), affectation.getStatut().name(),
                affectation.getDateRevocation(),
                affectation.getTypeException() == null ? null : affectation.getTypeException().name());
    }
}
