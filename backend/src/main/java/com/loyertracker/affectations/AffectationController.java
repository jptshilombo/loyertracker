package com.loyertracker.affectations;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AffectationController {

    private final AffectationService affectationService;

    public AffectationController(AffectationService affectationService) {
        this.affectationService = affectationService;
    }

    @PostMapping("/affectations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederBien(#requete.bienId(), authentication)")
    public AffectationDto creer(@Valid @RequestBody AffectationRequest requete,
            @AuthenticationPrincipal Jwt jwt) {
        return affectationService.creer(jwt, requete);
    }

    @PostMapping("/affectations/{id}/revocation")
    @PreAuthorize("hasRole('BAILLEUR')")
    public AffectationDto revoquer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return affectationService.revoquer(id, jwt);
    }

    @GetMapping("/biens/{bienId}/affectations")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederBien(#bienId, authentication)")
    public List<AffectationDto> historique(@PathVariable UUID bienId,
            @AuthenticationPrincipal Jwt jwt) {
        return affectationService.historique(bienId, jwt);
    }
}
