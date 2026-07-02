package com.loyertracker.documents;

import java.time.LocalDate;

import com.loyertracker.baux.Money;

/**
 * Données assemblées d'un document locatif (quittance ou avis d'échéance), prêtes à être mises en
 * page. Découplé des entités JPA pour rendre la construction du HTML testable sans base.
 *
 * @param type             nature du document
 * @param bailleurNom      identité complète du bailleur (mention obligatoire)
 * @param bailleurAdresse  adresse postale du bailleur (mention obligatoire)
 * @param locataireNom     nom du locataire
 * @param bienAdresse      adresse du logement loué
 * @param periodeLibelle   période couverte, lisible (« janvier 2026 »)
 * @param loyerHc          loyer hors charges, devise du bail (ADR-13, US-92)
 * @param provisionCharges provision de charges, devise du bail
 * @param loyerCc          loyer charges comprises (= loyerHc + provisionCharges), devise du bail
 * @param montant          montant pertinent : reçu (quittance) ou dû (avis d'échéance), devise du bail
 * @param dateEmission     date d'établissement du document
 * @param dateExigibilite  date d'exigibilité (avis d'échéance ; ignorée pour la quittance)
 */
public record DonneesDocument(
        TypeDocument type,
        String bailleurNom,
        String bailleurAdresse,
        String locataireNom,
        String bienAdresse,
        String periodeLibelle,
        Money loyerHc,
        Money provisionCharges,
        Money loyerCc,
        Money montant,
        LocalDate dateEmission,
        LocalDate dateExigibilite) {
}
