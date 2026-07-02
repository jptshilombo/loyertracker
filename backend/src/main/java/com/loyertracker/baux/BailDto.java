package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code depotGarantie} est une valeur dérivée (ADR-14 §8, V20) : plus jamais stockée sur
 * {@code bail}, calculée par l'appelant (somme des garanties rattachées) et transmise à
 * {@link #from(Bail, BigDecimal)}.
 */
public record BailDto(UUID id, UUID bienId, String locataireNom, String locataireEmail,
        BigDecimal loyerHc, BigDecimal provisionCharges, BigDecimal loyerCc,
        BigDecimal depotGarantie, LocalDate dateDebut, LocalDate dateFin, String statut, String devise) {

    public static BailDto from(Bail bail, BigDecimal montantDepose) {
        return new BailDto(bail.getId(), bail.getBienId(), bail.getLocataireNom(),
                bail.getLocataireEmail(), bail.getLoyerHc(), bail.getProvisionCharges(),
                bail.getLoyerCc(), montantDepose,
                bail.getDateDebut(), bail.getDateFin(), bail.getStatut().name(),
                bail.getDevise() != null ? bail.getDevise().name() : Devise.EUR.name());
    }
}
