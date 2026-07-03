package com.loyertracker.rgpd;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.loyertracker.affectations.AffectationDto;
import com.loyertracker.baux.BailDto;
import com.loyertracker.biens.BienDto;
import com.loyertracker.garanties.GarantieDto;
import com.loyertracker.garanties.GarantieMovementDto;
import com.loyertracker.paiements.PaiementDto;

/**
 * Export RGPD complet d'un bailleur (US-70 / ENF-04 / ADR-03).
 * Scopé par RLS — seules les données du bailleur courant sont incluses.
 */
public record ExportBailleurDto(
        UUID bailleurId,
        OffsetDateTime dateExport,
        List<BienExportDto> biens) {

    public record BienExportDto(
            BienDto bien,
            List<BailExportDto> baux,
            List<PaiementDto> paiements,
            List<AffectationDto> affectations) {
    }

    public record BailExportDto(
            BailDto bail,
            List<GarantieExportDto> garanties) {
    }

    /** Garantie + historique complet de ses mouvements (Sprint 10, US-97/RGPD). */
    public record GarantieExportDto(
            GarantieDto garantie,
            List<GarantieMovementDto> mouvements) {
    }
}
