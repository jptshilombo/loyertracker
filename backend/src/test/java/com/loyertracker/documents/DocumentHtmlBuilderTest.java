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
import com.loyertracker.quittances.DonneesQuittanceCertifiee;
import com.loyertracker.quittances.ThemeQuittance;

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

    // --- Gabarit certifié (EP-14, ADR-15 D6, US-101) ---------------------------------------

    private static final ThemeQuittance THEME = new ThemeQuittance("LoyerTracker",
            "data:image/png;base64,LOGO", "#0f4c81", "#1a1a1a", null);
    private static final String URL_VERIF =
            "https://loyertracker.loyerpro.org/verify/receipt/11111111-2222-3333-4444-555555555555"
                    + "?token=abc&v=1";
    private static final String HASH =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String QR = "data:image/png;base64,QRCODE";

    private DonneesQuittanceCertifiee certifiees(String locataire, Money garantieRetenue) {
        return new DonneesQuittanceCertifiee("QT-2026-000001", 2, "Alice Durand",
                "10 rue du Bailleur, 75001 Paris", locataire, "Patrimoine Centre",
                "5 avenue du Bien, 69002 Lyon", "2026-01", "janvier 2026",
                Money.of(new BigDecimal("800.00"), Devise.EUR),
                Money.of(new BigDecimal("50.00"), Devise.EUR),
                Money.of(new BigDecimal("850.00"), Devise.EUR),
                Money.of(new BigDecimal("850.00"), Devise.EUR),
                "Non renseigné", garantieRetenue, LocalDate.of(2026, 2, 1));
    }

    @Test
    void quittanceCertifieePorteIdentiteQrEmpreinteEtMentions() {
        String html = builder.construireQuittanceCertifiee(certifiees("Bob Martin", null),
                THEME, URL_VERIF, HASH, QR);
        String nbsp = " ";

        assertThat(html)
                .startsWith("<?xml")
                .contains("Quittance de loyer")
                .contains("DOCUMENT CERTIFIÉ")
                .contains("QT-2026-000001")
                .contains("Version <strong>2</strong>")
                .contains("1 février 2026")
                .contains("Alice Durand")
                .contains("Bob Martin")
                .contains("Patrimoine Centre")
                .contains("5 avenue du Bien, 69002 Lyon")
                .contains("janvier 2026")
                .contains("donne quittance")
                .contains("800,00" + nbsp + "€", "50,00" + nbsp + "€", "850,00" + nbsp + "€")
                .contains("Mode de paiement")
                // Vérifiabilité (ADR-15 D4) : QR embarqué + URL + empreinte du contenu certifié.
                .contains(QR)
                // L'URL est injectée via esc() : le & du paramètre token devient &amp; en XHTML.
                .contains(URL_VERIF.replace("&", "&amp;"))
                .contains(HASH)
                // Thème (D6) : logo data-URI et marque.
                .contains(THEME.logoDataUri())
                .contains("LoyerTracker")
                // Cachet électronique et mentions d'intégrité.
                .contains("Toute modification rend ce document invalide")
                .contains("jamais réutilisé")
                // Garantie non mobilisée : pas de ligne dédiée.
                .doesNotContain("Garantie utilisée");
    }

    @Test
    void quittanceCertifieeAfficheLaGarantieUtiliseeLeCasEcheant() {
        String html = builder.construireQuittanceCertifiee(
                certifiees("Bob Martin", Money.of(new BigDecimal("200.00"), Devise.EUR)),
                THEME, URL_VERIF, HASH, QR);

        assertThat(html).contains("Garantie utilisée")
                .contains("200,00 €");
    }

    @Test
    void quittanceCertifieeEchappeLesSaisiesUtilisateur() {
        String html = builder.construireQuittanceCertifiee(
                certifiees("<script>x</script>", null), THEME, URL_VERIF, HASH, QR);

        assertThat(html)
                .doesNotContain("<script>x</script>")
                .contains("&lt;script&gt;x&lt;/script&gt;");
    }
}
