package com.loyertracker.garanties;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Endpoints de dépôt et de restitution de garantie d'un bail (US-32). Accès réservé au bailleur
 * propriétaire ou au gestionnaire affecté actif du bien (ReBAC, {@code @authz}).
 */
@RestController
@RequestMapping("/api/biens/{bienId}/baux/{bailId}/garanties")
public class GarantieController {

    private final GarantieService garantieService;

    public GarantieController(GarantieService garantieService) {
        this.garantieService = garantieService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public List<GarantieDto> lister(@PathVariable UUID bienId, @PathVariable UUID bailId) {
        return garantieService.lister(bienId, bailId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public GarantieDto creer(@PathVariable UUID bienId, @PathVariable UUID bailId,
            @Valid @RequestBody GarantieRequest requete, Authentication authentication) {
        return garantieService.creer(bienId, bailId, requete, authentication);
    }

    @PostMapping("/{garantieId}/restitution")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public GarantieDto restituer(@PathVariable UUID bienId, @PathVariable UUID bailId,
            @PathVariable UUID garantieId, @Valid @RequestBody RestitutionRequest requete,
            Authentication authentication) {
        return garantieService.restituer(bienId, bailId, garantieId, requete, authentication);
    }
}
