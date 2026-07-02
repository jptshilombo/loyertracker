package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GarantieDto(UUID id, UUID bailId, BigDecimal montant, String typeGarantie,
        LocalDate dateDepot, String statut, BigDecimal montantRetenu, String motifRetenue,
        BigDecimal soldeActuel) {

    public static GarantieDto from(Garantie g) {
        return new GarantieDto(g.getId(), g.getBailId(), g.getMontant(), g.getTypeGarantie(),
                g.getDateDepot(), g.getStatut().name(), g.getMontantRetenu(), g.getMotifRetenue(),
                g.getSoldeActuel());
    }
}
