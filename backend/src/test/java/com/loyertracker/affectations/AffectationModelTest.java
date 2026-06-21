package com.loyertracker.affectations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AffectationModelTest {

    @Test
    void affectationPatrimoineExposeUnPatrimoineIdSansBienId() {
        UUID bailleurId = UUID.randomUUID();
        UUID patrimoineId = UUID.randomUUID();
        UUID gestionnaireId = UUID.randomUUID();

        Affectation affectation = Affectation.surPatrimoine(UUID.randomUUID(), bailleurId, patrimoineId,
                gestionnaireId, TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);

        assertThat(affectation.getBienId()).isNull();
        assertThat(affectation.getPatrimoineId()).isEqualTo(patrimoineId);
        assertThat(AffectationDto.from(affectation).patrimoineId()).isEqualTo(patrimoineId);
    }

    @Test
    void affectationRequestDetecteExactementUnPerimetre() {
        UUID bienId = UUID.randomUUID();
        UUID patrimoineId = UUID.randomUUID();
        UUID gestionnaireId = UUID.randomUUID();

        AffectationRequest bien = new AffectationRequest(bienId, null, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);
        AffectationRequest patrimoine = new AffectationRequest(null, patrimoineId, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);
        AffectationRequest aucun = new AffectationRequest(null, null, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);
        AffectationRequest doublePerimetre = new AffectationRequest(bienId, patrimoineId, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);

        assertThat(bien.aExactementUnPerimetre()).isTrue();
        assertThat(patrimoine.aExactementUnPerimetre()).isTrue();
        assertThat(aucun.aExactementUnPerimetre()).isFalse();
        assertThat(doublePerimetre.aExactementUnPerimetre()).isFalse();
    }
}
