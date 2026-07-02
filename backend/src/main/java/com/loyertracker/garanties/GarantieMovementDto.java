package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GarantieMovementDto(UUID id, UUID garantieId, LocalDate dateMouvement, String type,
        BigDecimal debit, BigDecimal credit, BigDecimal soldeApres, String motif,
        String utilisateur) {

    public static GarantieMovementDto from(GarantieMovement m) {
        return new GarantieMovementDto(m.getId(), m.getGarantieId(), m.getDateMouvement(),
                m.getType().name(), m.getDebit(), m.getCredit(), m.getSoldeApres(), m.getMotif(),
                m.getUtilisateur());
    }
}
