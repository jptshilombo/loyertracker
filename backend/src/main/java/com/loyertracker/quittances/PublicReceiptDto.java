package com.loyertracker.quittances;

/**
 * Contrat public strict d'une quittance certifiée (ADR-15 D5, kickoff K2) — l'unique projection
 * exposée sans authentification sur la page de vérification.
 *
 * <p><strong>Non-fuite (US-102)</strong> : ce record ne porte volontairement <em>ni</em> le mode
 * de paiement (« {@code paiement.mode} » du payload canonique) <em>ni</em> le montant de garantie
 * retenue (« {@code garantie_retenue} ») — des informations présentes dans {@code quittance.contenu}
 * mais interdites en public. Les valeurs sont extraites du <em>contenu certifié</em>
 * ({@code quittance.contenu}, figé à l'émission), jamais recalculées depuis les données vivantes,
 * afin que ce qui est affiché soit exactement ce qui a été haché ({@code contentHash}).</p>
 *
 * @param numero            numéro permanent {@code QT-YYYY-NNNNNN}
 * @param version           version de l'exemplaire vérifié
 * @param statut            statut courant (lu sur la ligne, mutable) : {@code EMISE} / {@code ANNULEE}
 *                          / {@code REMPLACEE}
 * @param bailleurNom       identité du bailleur (certifiée)
 * @param bailleurAdresse   adresse du bailleur (certifiée)
 * @param locataireNom      nom du locataire (certifié)
 * @param patrimoineNom     patrimoine de rattachement
 * @param bienAdresse       adresse du logement loué
 * @param periode           période technique {@code YYYY-MM}
 * @param periodeLibelle    période lisible (« juillet 2026 »)
 * @param devise            devise des montants (ADR-13)
 * @param loyerHc           loyer hors charges
 * @param provisionCharges  provision de charges
 * @param loyerCc           loyer charges comprises
 * @param montantRecu       somme reçue, objet de la quittance
 * @param dateEmission      date d'émission certifiée ({@code YYYY-MM-DD})
 * @param contentHash       empreinte SHA-256 du contenu certifié
 * @param remplacanteNumero numéro de la version remplaçante si {@code statut = REMPLACEE}, sinon
 *                          {@code null}
 * @param remplacanteVersion version remplaçante, sinon {@code null}
 */
public record PublicReceiptDto(
        String numero,
        int version,
        String statut,
        String bailleurNom,
        String bailleurAdresse,
        String locataireNom,
        String patrimoineNom,
        String bienAdresse,
        String periode,
        String periodeLibelle,
        String devise,
        String loyerHc,
        String provisionCharges,
        String loyerCc,
        String montantRecu,
        String dateEmission,
        String contentHash,
        String remplacanteNumero,
        Integer remplacanteVersion) {
}
