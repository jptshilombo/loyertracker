package com.loyertracker.baux;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Verrou de l'invariant de ventilation (V11) : le « charges comprises » est toujours la somme
 * exacte du loyer hors charges et de la provision de charges — jamais saisi, toujours dérivé.
 * C'est cette cohérence qui alimente fidèlement la quittance et satisfait la contrainte base
 * {@code chk_loyer_cc_coherent}.
 */
class BailVentilationTest {

    private Bail bail(BigDecimal loyerHc, BigDecimal provisionCharges) {
        return new Bail(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Locataire Test", "loc@test.local", loyerHc, provisionCharges,
                LocalDate.of(2026, 1, 1), null, Devise.EUR);
    }

    @Test
    void loyerCcEstLaSommeDesDeuxPostes() {
        Bail b = bail(new BigDecimal("750.00"), new BigDecimal("100.00"));

        assertThat(b.getLoyerHc()).isEqualByComparingTo("750.00");
        assertThat(b.getProvisionCharges()).isEqualByComparingTo("100.00");
        assertThat(b.getLoyerCc()).isEqualByComparingTo("850.00");
    }

    @Test
    void provisionNulleDonneLoyerCcEgalAuHorsCharges() {
        Bail b = bail(new BigDecimal("850.00"), BigDecimal.ZERO);

        assertThat(b.getLoyerCc()).isEqualByComparingTo("850.00");
        assertThat(b.getLoyerCc()).isEqualByComparingTo(b.getLoyerHc());
    }
}
