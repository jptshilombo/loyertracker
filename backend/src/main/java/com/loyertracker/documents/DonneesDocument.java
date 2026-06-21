package com.loyertracker.documents;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * @param loyerHc          loyer hors charges
 * @param provisionCharges provision de charges
 * @param loyerCc          loyer charges comprises (= loyerHc + provisionCharges)
 * @param montant          montant pertinent : reçu (quittance) ou dû (avis d'échéance)
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
        BigDecimal loyerHc,
        BigDecimal provisionCharges,
        BigDecimal loyerCc,
        BigDecimal montant,
        LocalDate dateEmission,
        LocalDate dateExigibilite) {
}
