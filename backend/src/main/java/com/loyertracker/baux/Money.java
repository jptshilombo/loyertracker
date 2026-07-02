package com.loyertracker.baux;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Value Object monétaire (ADR-13, D-DEV-001). Associe un montant à une devise au moment de la
 * présentation ; ne duplique jamais la devise en base (celle-ci reste portée exclusivement par
 * {@link Bail#getDevise()}). Immuable, sans dépendance à une librairie tierce (3 devises fixes,
 * cf. ADR-13 §Alternatives écartées).
 */
public record Money(BigDecimal montant, Devise devise) {

    // Espace insécable U+00A0 (ADR-13, décision PO kickoff 2026-07-02) : échappement explicite
    // plutôt qu'un caractère invisible en source, forcé plutôt que de dépendre du séparateur de
    // regroupement par défaut de la locale FR du JDK (U+202F, différent).
    private static final char NBSP = '\u00A0';

    public Money {
        Objects.requireNonNull(montant, "montant");
        Objects.requireNonNull(devise, "devise");
    }

    public static Money of(BigDecimal montant, Devise devise) {
        return new Money(montant, devise);
    }

    /** Formatage propre à chaque devise (décision PO, ADR-13, kickoff 2026-07-02). */
    public String formate() {
        return switch (devise) {
            case EUR -> formaterFr(montant) + NBSP + "€";
            case USD -> NumberFormat.getCurrencyInstance(Locale.US).format(montant);
            case CDF -> formaterFr(montant) + NBSP + "CDF";
        };
    }

    private static String formaterFr(BigDecimal montant) {
        DecimalFormatSymbols symboles = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symboles.setGroupingSeparator(NBSP);
        DecimalFormat format = new DecimalFormat("#,##0.00", symboles);
        return format.format(montant);
    }
}
