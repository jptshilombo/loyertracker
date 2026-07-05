package com.loyertracker.quittances;

import java.time.LocalDate;

import com.loyertracker.baux.Money;

/**
 * Données métier certifiées d'une quittance (ADR-15 D2) — l'exact périmètre couvert par le
 * payload canonique et donc par {@code content_hash}. Découplé des entités JPA (testable sans
 * base), même approche que {@code DonneesDocument}.
 *
 * @param numero           numéro permanent {@code QT-YYYY-NNNNNN} (kickoff K1)
 * @param version          version de l'exemplaire (1, 2, … — régénération US-99)
 * @param bailleurNom      identité complète du bailleur
 * @param bailleurAdresse  adresse postale du bailleur
 * @param locataireNom     nom du locataire
 * @param patrimoineNom    patrimoine de rattachement du bien
 * @param bienAdresse      adresse du logement loué
 * @param periode          période technique {@code YYYY-MM}
 * @param periodeLibelle   période lisible (« juillet 2026 »)
 * @param loyerHc          loyer hors charges (devise du bail, ADR-13)
 * @param provisionCharges provision de charges
 * @param loyerCc          loyer charges comprises
 * @param montantRecu      somme effectivement reçue, objet de la quittance
 * @param modePaiement     libellé du mode de règlement (dérivé : le modèle ne trace pas le moyen
 *                         de paiement — « Retenue sur dépôt de garantie » si le loyer a été
 *                         couvert par la garantie (V21), « Non renseigné » sinon)
 * @param garantieRetenue  montant retenu sur la garantie pour couvrir ce loyer (US-95), ou
 *                         {@code null} si le loyer n'a pas mobilisé la garantie
 * @param dateEmission     date d'émission de cette version
 */
public record DonneesQuittanceCertifiee(
        String numero,
        int version,
        String bailleurNom,
        String bailleurAdresse,
        String locataireNom,
        String patrimoineNom,
        String bienAdresse,
        String periode,
        String periodeLibelle,
        Money loyerHc,
        Money provisionCharges,
        Money loyerCc,
        Money montantRecu,
        String modePaiement,
        Money garantieRetenue,
        LocalDate dateEmission) {
}
