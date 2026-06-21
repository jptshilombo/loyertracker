package com.loyertracker.bailleur;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Profil du bailleur courant (V11) : consultation et mise à jour de l'adresse postale, requise pour
 * les mentions de la quittance de loyer. Réservé au rôle BAILLEUR ; l'identité provient du jeton.
 */
@RestController
@RequestMapping("/api/bailleurs/profil")
public class ProfilController {

    private final ProfilService profilService;

    public ProfilController(ProfilService profilService) {
        this.profilService = profilService;
    }

    @GetMapping
    @PreAuthorize("hasRole('BAILLEUR')")
    public BailleurDto consulter(@AuthenticationPrincipal Jwt jwt) {
        return profilService.consulter(jwt.getSubject());
    }

    @PutMapping
    @PreAuthorize("hasRole('BAILLEUR')")
    public BailleurDto mettreAJour(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfilRequest requete) {
        return profilService.mettreAJour(jwt.getSubject(), requete);
    }
}
