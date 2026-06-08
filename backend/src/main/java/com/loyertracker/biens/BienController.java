package com.loyertracker.biens;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/biens")
public class BienController {

    private final BienService bienService;

    public BienController(BienService bienService) {
        this.bienService = bienService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE')")
    public List<BienDto> lister(Authentication authentication) {
        return bienService.lister(authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BAILLEUR')")
    public BienDto creer(@Valid @RequestBody BienRequest requete, @AuthenticationPrincipal Jwt jwt) {
        return bienService.creer(jwt, requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederBien(#id, authentication)")
    public BienDto modifier(@PathVariable UUID id, @Valid @RequestBody BienRequest requete,
            @AuthenticationPrincipal Jwt jwt) {
        return bienService.modifier(id, jwt, requete);
    }

    @PatchMapping("/{id}/archivage")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederBien(#id, authentication)")
    public BienDto archiver(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return bienService.archiver(id, jwt);
    }
}
