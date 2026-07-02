package com.loyertracker.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.loyertracker.baux.Devise;
import com.loyertracker.baux.Money;

/**
 * Mise en page des documents locatifs (composant pur). Valide le libellé, la ventilation, le
 * formatage monétaire par devise (US-92, ADR-13) et l'échappement XML des saisies utilisateur.
 */
class DocumentHtmlBuilderTest {

    private final DocumentHtmlBuilder builder = new DocumentHtmlBuilder();

    private DonneesDocument donnees(TypeDocument type, String locataire, Devise devise) {
        return new DonneesDocument(type, "Alice Durand", "10 rue du Bailleur, 75001 Paris",
                locataire, "5 avenue du Bien, 69002 Lyon", "janvier 2026",
                Money.of(new BigDecimal("800.00"), devise), Money.of(new BigDecimal("50.00"), devise),
                Money.of(new BigDecimal("850.00"), devise), Money.of(new BigDecimal("850.00"), devise),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ventilationsAttendues")
    void quittanceContientLesMentionsEtLaVentilation(Devise devise, String hc, String provision,
            String total) {
        String html = builder.construire(donnees(TypeDocument.QUITTANCE, "Bob Martin", devise));

        assertThat(html)
                .startsWith("<?xml")
                .contains("Quittance de loyer")
                .contains("donne quittance")
                .contains("Alice Durand")
                .contains("Bob Martin")
                .contains("janvier 2026")
                // Ventilation + total, formatage devise-aware (US-92).
                .contains(hc, provision, total);
    }

    static Stream<Arguments> ventilationsAttendues() {
        String nbsp = "\u00A0";
        return Stream.of(
                Arguments.of(Devise.EUR, "800,00" + nbsp + "€", "50,00" + nbsp + "€",
                        "850,00" + nbsp + "€"),
                Arguments.of(Devise.USD, "$800.00", "$50.00", "$850.00"),
                Arguments.of(Devise.CDF, "800,00" + nbsp + "CDF", "50,00" + nbsp + "CDF",
                        "850,00" + nbsp + "CDF"));
    }

    @Test
    void avisEcheanceMentionneExigibiliteEtSommeDue() {
        String html = builder.construire(donnees(TypeDocument.AVIS_ECHEANCE, "Bob Martin", Devise.EUR));

        assertThat(html)
                .contains("Avis d'échéance")
                .contains("exigible")
                .contains("1 février 2026");
    }

    @Test
    void lesSaisiesUtilisateurSontEchappees() {
        String html = builder.construire(
                donnees(TypeDocument.QUITTANCE, "<script>x</script>", Devise.EUR));

        assertThat(html)
                .doesNotContain("<script>x</script>")
                .contains("&lt;script&gt;x&lt;/script&gt;");
    }
}
