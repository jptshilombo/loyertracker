package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Création d'un bail. Depuis V11, le loyer est ventilé : {@code loyerHc} (hors charges) et
 * {@code provisionCharges}. Le « charges comprises » est dérivé ({@code loyerHc + provisionCharges})
 * côté entité, jamais saisi, pour garantir la cohérence (et alimenter la quittance).
 *
 * <p>Depuis V20 (ADR-14 §8), le dépôt de garantie ne se saisit plus à la création du bail : aucune
 * {@code Garantie} n'existe encore à cet instant, donc la valeur serait nécessairement incohérente
 * avec le ledger. Il se déclare via le flux « Ajouter garantie » existant
 * ({@code POST .../garanties}), après la création du bail.</p>
 *
 * <p>Depuis V26 (EP-15 Sprint C, US-113), le locataire n'est plus du texte libre :
 * {@code locataireId} doit référencer un {@code Locataire} existant, appartenant au même
 * bailleur et non archivé (404/409 sinon) — rupture de contrat HTTP intentionnelle, cf. ADR-16
 * §Conséquences. La lecture (`BailDto`) reste, elle, inchangée.</p>
 */
public record BailRequest(
        @NotNull UUID locataireId,
        @NotNull @DecimalMin("0.00") BigDecimal loyerHc,
        @NotNull @DecimalMin("0.00") BigDecimal provisionCharges,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin,
        Devise devise) {
}
