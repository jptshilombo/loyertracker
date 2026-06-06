package com.loyertracker.bailleur;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint d'inscription bailleur (US-10).
 */
@RestController
@RequestMapping("/api/bailleurs")
public class BailleurController {

    private final InscriptionService inscriptionService;

    public BailleurController(InscriptionService inscriptionService) {
        this.inscriptionService = inscriptionService;
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BAILLEUR')")
    public BailleurDto inscrire(@AuthenticationPrincipal Jwt jwt) {
        return BailleurDto.from(inscriptionService.inscrire(jwt));
    }
}
