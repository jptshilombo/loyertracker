package com.loyertracker.baux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Verrou du format d'affichage par devise (ADR-13, D-DEV-001, décision PO kickoff 2026-07-02) :
 * EUR/CDF en style FR (virgule décimale, espace insécable en séparateur de milliers), USD via le
 * format natif JDK. Ces formats sont contractuels (US-92) : toute modification doit être une
 * décision PO explicite, pas une dérive silencieuse d'implémentation.
 */
class MoneyTest {

    private static final String NBSP = "\u00A0";

    @Test
    void formateEuroSansRegroupement() {
        assertThat(Money.of(new BigDecimal("800.00"), Devise.EUR).formate())
                .isEqualTo("800,00" + NBSP + "€");
    }

    @Test
    void formateEuroAvecRegroupementDeMilliers() {
        assertThat(Money.of(new BigDecimal("1200.00"), Devise.EUR).formate())
                .isEqualTo("1" + NBSP + "200,00" + NBSP + "€");
    }

    @Test
    void formateDollarAmericain() {
        assertThat(Money.of(new BigDecimal("800.00"), Devise.USD).formate()).isEqualTo("$800.00");
        assertThat(Money.of(new BigDecimal("1000.00"), Devise.USD).formate())
                .isEqualTo("$1,000.00");
    }

    @Test
    void formateFrancCongolaisAvecRegroupementDeMilliers() {
        assertThat(Money.of(new BigDecimal("1000.00"), Devise.CDF).formate())
                .isEqualTo("1" + NBSP + "000,00" + NBSP + "CDF");
    }

    @Test
    void refuseMontantNul() {
        assertThatThrownBy(() -> Money.of(null, Devise.EUR)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void refuseDeviseNulle() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, null)).isInstanceOf(NullPointerException.class);
    }
}
