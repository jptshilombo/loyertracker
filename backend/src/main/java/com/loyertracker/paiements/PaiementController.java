package com.loyertracker.paiements;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Endpoints de pointage et de consultation des loyers d'un bien (US-31).
 * Accès réservé au bailleur propriétaire ou au gestionnaire affecté actif (ReBAC, {@code @authz}).
 */
@RestController
@RequestMapping("/api/biens/{bienId}/paiements")
public class PaiementController {

    private final PaiementService paiementService;

    public PaiementController(PaiementService paiementService) {
        this.paiementService = paiementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public List<PaiementDto> historique(@PathVariable UUID bienId) {
        return paiementService.historique(bienId);
    }

    @PatchMapping("/{periode}/pointage")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public PaiementDto pointer(@PathVariable UUID bienId, @PathVariable String periode,
            @Valid @RequestBody PointageRequest requete, Authentication authentication) {
        return paiementService.pointer(bienId, periode, requete, authentication);
    }
}
