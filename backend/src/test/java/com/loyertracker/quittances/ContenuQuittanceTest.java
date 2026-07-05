package com.loyertracker.quittances;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.loyertracker.baux.Devise;
import com.loyertracker.baux.Money;

/**
 * Payload canonique certifié (ADR-15 D2). Le format est verrouillé octet à octet : la chaîne
 * attendue est écrite en dur — toute évolution du format casse ce test et impose d'incrémenter
 * {@code schema} (les contenus déjà certifiés ne sont jamais re-sérialisés).
 */
class ContenuQuittanceTest {

    private static DonneesQuittanceCertifiee donnees(String numero, int version, String locataire,
            String montantRecu, Money garantieRetenue, LocalDate emission) {
        return new DonneesQuittanceCertifiee(numero, version,
                "Alice Durand", "10 rue du Bailleur, 75001 Paris",
                locataire, "Patrimoine Centre", "5 avenue du Bien, 69002 Lyon",
                "2026-01", "janvier 2026",
                Money.of(new BigDecimal("800.00"), Devise.EUR),
                Money.of(new BigDecimal("50.00"), Devise.EUR),
                Money.of(new BigDecimal("850.00"), Devise.EUR),
                Money.of(new BigDecimal(montantRecu), Devise.EUR),
                "Non renseigné", garantieRetenue, emission);
    }

    @Test
    void canoniqueEstDeterministeOctetAOctet() {
        String contenu = ContenuQuittance.canonique(donnees("QT-2026-000001", 1, "Bob Martin",
                "850.00", null, LocalDate.of(2026, 2, 1)));

        assertThat(contenu).isEqualTo("{\"schema\":1"
                + ",\"numero\":\"QT-2026-000001\""
                + ",\"version\":1"
                + ",\"emise_le\":\"2026-02-01\""
                + ",\"bailleur\":{\"nom\":\"Alice Durand\""
                + ",\"adresse\":\"10 rue du Bailleur, 75001 Paris\"}"
                + ",\"locataire\":{\"nom\":\"Bob Martin\"}"
                + ",\"patrimoine\":{\"nom\":\"Patrimoine Centre\"}"
                + ",\"bien\":{\"adresse\":\"5 avenue du Bien, 69002 Lyon\"}"
                + ",\"periode\":{\"code\":\"2026-01\",\"libelle\":\"janvier 2026\"}"
                + ",\"montants\":{\"devise\":\"EUR\",\"loyer_hc\":\"800.00\""
                + ",\"provision_charges\":\"50.00\",\"loyer_cc\":\"850.00\""
                + ",\"montant_recu\":\"850.00\"}"
                + ",\"paiement\":{\"mode\":\"Non renseigné\",\"garantie_retenue\":null}}");
    }

    @Test
    void canoniquePorteLaGarantieRetenueQuandLeLoyerAMobiliseLaGarantie() {
        String contenu = ContenuQuittance.canonique(donnees("QT-2026-000001", 1, "Bob Martin",
                "850.00", Money.of(new BigDecimal("200.00"), Devise.EUR), LocalDate.of(2026, 2, 1)));

        assertThat(contenu).contains("\"garantie_retenue\":\"200.00\"");
    }

    @Test
    void canoniqueEchappeLesSaisiesUtilisateur() {
        String contenu = ContenuQuittance.canonique(donnees("QT-2026-000001", 1,
                "Bob \"Le\\Marseillais\"\nMartin", "850.00", null, LocalDate.of(2026, 2, 1)));

        assertThat(contenu)
                .contains("\"locataire\":{\"nom\":\"Bob \\\"Le\\\\Marseillais\\\"\\nMartin\"}")
                .doesNotContain("\nMartin");
    }

    @Test
    void empreinteMetierIgnoreNumeroVersionEtDateDEmission() {
        String v1 = ContenuQuittance.empreinteMetier(donnees("QT-2026-000001", 1, "Bob Martin",
                "850.00", null, LocalDate.of(2026, 2, 1)));
        String v2 = ContenuQuittance.empreinteMetier(donnees("QT-2026-000042", 7, "Bob Martin",
                "850.00", null, LocalDate.of(2027, 12, 31)));

        // La ré-émission est idempotente tant que les données métier sont inchangées (US-99).
        assertThat(v2).isEqualTo(v1);
    }

    @Test
    void empreinteMetierChangeAvecToutChampMetier() {
        DonneesQuittanceCertifiee reference = donnees("QT-2026-000001", 1, "Bob Martin",
                "850.00", null, LocalDate.of(2026, 2, 1));

        String empreinte = ContenuQuittance.empreinteMetier(reference);
        String autreMontant = ContenuQuittance.empreinteMetier(donnees("QT-2026-000001", 1,
                "Bob Martin", "800.00", null, LocalDate.of(2026, 2, 1)));
        String autreLocataire = ContenuQuittance.empreinteMetier(donnees("QT-2026-000001", 1,
                "Carla Petit", "850.00", null, LocalDate.of(2026, 2, 1)));
        String avecGarantie = ContenuQuittance.empreinteMetier(donnees("QT-2026-000001", 1,
                "Bob Martin", "850.00", Money.of(new BigDecimal("200.00"), Devise.EUR),
                LocalDate.of(2026, 2, 1)));

        assertThat(autreMontant).isNotEqualTo(empreinte);
        assertThat(autreLocataire).isNotEqualTo(empreinte);
        assertThat(avecGarantie).isNotEqualTo(empreinte);
    }

    @Test
    void sha256HexProduitLesVecteursConnus() {
        assertThat(ContenuQuittance.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ContenuQuittance.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
