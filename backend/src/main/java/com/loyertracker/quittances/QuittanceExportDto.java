package com.loyertracker.quittances;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Métadonnées d'une quittance certifiée pour l'export RGPD du bailleur (ADR-15 §RGPD) : tout
 * sauf les octets du PDF (téléchargeable par ailleurs) — le {@code contenu} canonique est inclus
 * car il porte les données personnelles certifiées.
 */
public record QuittanceExportDto(
        UUID id,
        UUID paiementId,
        String numero,
        int version,
        StatutQuittance statut,
        UUID remplaceePar,
        String contenu,
        String contentHash,
        String pdfHash,
        OffsetDateTime emiseLe,
        int nbTelechargements,
        int nbVerifications) {

    public static QuittanceExportDto from(Quittance q) {
        return new QuittanceExportDto(q.getId(), q.getPaiementId(), q.getNumero(), q.getVersion(),
                q.getStatut(), q.getRemplaceePar(), q.getContenu(), q.getContentHash(),
                q.getPdfHash(), q.getEmiseLe(), q.getNbTelechargements(), q.getNbVerifications());
    }
}
