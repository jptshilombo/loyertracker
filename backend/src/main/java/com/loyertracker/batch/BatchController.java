package com.loyertracker.batch;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loyertracker.alertes.AlerteService;
import com.loyertracker.honoraires.HonoraireService;

/**
 * Déclenchement manuel des jobs batch (exploitation / tests). Les jobs étant idempotents et
 * multi-bailleur, l'accès est restreint au rôle {@code BAILLEUR}.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private final GenerationEcheancesService generation;
    private final HonoraireService honoraires;
    private final AlerteService alertes;

    public BatchController(GenerationEcheancesService generation, HonoraireService honoraires,
            AlerteService alertes) {
        this.generation = generation;
        this.honoraires = honoraires;
        this.alertes = alertes;
    }

    public record DeclenchementDto(int echeancesCreees, int loyersEnRetard) {
    }

    public record HonorairesDto(int honorairesCalcules) {
    }

    public record AlertesDto(int alertesCreees) {
    }

    @PostMapping("/echeances")
    @PreAuthorize("hasRole('BAILLEUR')")
    public DeclenchementDto genererEcheances() {
        int crees = generation.genererEcheances();
        int enRetard = generation.marquerEnRetard();
        return new DeclenchementDto(crees, enRetard);
    }

    @PostMapping("/honoraires")
    @PreAuthorize("hasRole('BAILLEUR')")
    public HonorairesDto calculerHonoraires() {
        return new HonorairesDto(honoraires.recalculerBatch());
    }

    @PostMapping("/alertes")
    @PreAuthorize("hasRole('BAILLEUR')")
    public AlertesDto genererAlertes() {
        return new AlertesDto(alertes.genererBatch());
    }
}
