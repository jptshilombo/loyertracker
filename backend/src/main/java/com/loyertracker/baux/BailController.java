package com.loyertracker.baux;

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

@RestController
@RequestMapping("/api/biens/{bienId}/baux")
public class BailController {

    private final BailService bailService;

    public BailController(BailService bailService) {
        this.bailService = bailService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public BailDto creer(@PathVariable UUID bienId, @Valid @RequestBody BailRequest requete) {
        return bailService.creer(bienId, requete);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public List<BailDto> historique(@PathVariable UUID bienId) {
        return bailService.historique(bienId);
    }

    @PostMapping("/{bailId}/cloture")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public ClotureBailDto cloturer(@PathVariable UUID bienId, @PathVariable UUID bailId,
            @RequestBody(required = false) ClotureRequest requete, Authentication authentication) {
        return bailService.cloturer(bienId, bailId, requete, authentication);
    }

    @PostMapping("/{bailId}/reouverture")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public BailDto rouvrir(@PathVariable UUID bienId, @PathVariable UUID bailId,
            Authentication authentication) {
        return bailService.rouvrir(bienId, bailId, authentication);
    }
}
