package com.loyertracker.batch;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Déclenchement manuel des jobs batch (exploitation / tests). La génération des échéances étant
 * idempotente et multi-bailleur, l'accès est restreint au rôle {@code BAILLEUR}.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private final GenerationEcheancesService generation;

    public BatchController(GenerationEcheancesService generation) {
        this.generation = generation;
    }

    public record DeclenchementDto(int echeancesCreees) {
    }

    @PostMapping("/echeances")
    @PreAuthorize("hasRole('BAILLEUR')")
    public DeclenchementDto genererEcheances() {
        return new DeclenchementDto(generation.genererEcheances());
    }
}
