package com.loyertracker.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Mise en page des documents locatifs (composant pur). Valide le libellé, la ventilation, le
 * formatage monétaire français et l'échappement XML des saisies utilisateur.
 */
class DocumentHtmlBuilderTest {

    private final DocumentHtmlBuilder builder = new DocumentHtmlBuilder();

    private DonneesDocument donnees(TypeDocument type, String locataire) {
        return new DonneesDocument(type, "Alice Durand", "10 rue du Bailleur, 75001 Paris",
                locataire, "5 avenue du Bien, 69002 Lyon", "janvier 2026",
                new BigDecimal("800.00"), new BigDecimal("50.00"), new BigDecimal("850.00"),
                new BigDecimal("850.00"), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1));
    }

    @Test
    void quittanceContientLesMentionsEtLaVentilation() {
        String html = builder.construire(donnees(TypeDocument.QUITTANCE, "Bob Martin"));

        assertThat(html).startsWith("<?xml");
        assertThat(html).contains("Quittance de loyer");
        assertThat(html).contains("donne quittance");
        assertThat(html).contains("Alice Durand");
        assertThat(html).contains("Bob Martin");
        assertThat(html).contains("janvier 2026");
        // Ventilation + total, format monétaire FR.
        assertThat(html).contains("800,00 €").contains("50,00 €").contains("850,00 €");
    }

    @Test
    void avisEcheanceMentionneExigibiliteEtSommeDue() {
        String html = builder.construire(donnees(TypeDocument.AVIS_ECHEANCE, "Bob Martin"));

        assertThat(html).contains("Avis d'échéance");
        assertThat(html).contains("exigible");
        assertThat(html).contains("1 février 2026");
    }

    @Test
    void lesSaisiesUtilisateurSontEchappees() {
        String html = builder.construire(donnees(TypeDocument.QUITTANCE, "<script>x</script>"));

        assertThat(html).doesNotContain("<script>x</script>");
        assertThat(html).contains("&lt;script&gt;x&lt;/script&gt;");
    }
}
