package com.loyertracker.affectations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.loyertracker.affectations.Affectation.HonorairesAffectation;

import org.junit.jupiter.api.Test;

class AffectationModelTest {

    @Test
    void affectationPatrimoineExposeUnPatrimoineIdSansBienId() {
        UUID bailleurId = UUID.randomUUID();
        UUID patrimoineId = UUID.randomUUID();
        UUID gestionnaireId = UUID.randomUUID();

        HonorairesAffectation honoraires = new HonorairesAffectation(TypeHonoraires.POURCENTAGE,
                BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);
        Affectation affectation = Affectation.surPatrimoine(UUID.randomUUID(), bailleurId, patrimoineId,
                gestionnaireId, honoraires);

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
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null, null);
        AffectationRequest patrimoine = new AffectationRequest(null, patrimoineId, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null, null);
        AffectationRequest aucun = new AffectationRequest(null, null, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null, null);
        AffectationRequest doublePerimetre = new AffectationRequest(bienId, patrimoineId, gestionnaireId,
                TypeHonoraires.POURCENTAGE, BigDecimal.TEN, LocalDate.parse("2026-06-01"), null, null);

        assertThat(bien.aExactementUnPerimetre()).isTrue();
        assertThat(patrimoine.aExactementUnPerimetre()).isTrue();
        assertThat(aucun.aExactementUnPerimetre()).isFalse();
        assertThat(doublePerimetre.aExactementUnPerimetre()).isFalse();
    }

    @Test
    void exceptionSansBienEstDetectee() {
        UUID patrimoineId = UUID.randomUUID();
        UUID gestionnaireId = UUID.randomUUID();

        AffectationRequest patrimoineAvecException = new AffectationRequest(null, patrimoineId,
                gestionnaireId, TypeHonoraires.POURCENTAGE, BigDecimal.TEN,
                LocalDate.parse("2026-06-01"), null, TypeException.EXCLUSION);
        AffectationRequest patrimoineSansException = new AffectationRequest(null, patrimoineId,
                gestionnaireId, TypeHonoraires.POURCENTAGE, BigDecimal.TEN,
                LocalDate.parse("2026-06-01"), null, null);

        assertThat(patrimoineAvecException.exceptionSansBien()).isTrue();
        assertThat(patrimoineSansException.exceptionSansBien()).isFalse();
    }

    @Test
    void affectationSurBienSansExceptionDefautInclusion() {
        UUID bailleurId = UUID.randomUUID();
        UUID bienId = UUID.randomUUID();
        UUID gestionnaireId = UUID.randomUUID();
        HonorairesAffectation honoraires = new HonorairesAffectation(TypeHonoraires.POURCENTAGE,
                BigDecimal.TEN, LocalDate.parse("2026-06-01"), null);

        Affectation affectation = Affectation.surBien(UUID.randomUUID(), bailleurId, bienId,
                gestionnaireId, null, honoraires);

        assertThat(affectation.getTypeException()).isEqualTo(TypeException.INCLUSION);
    }
}
