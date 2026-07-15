package com.loyertracker.locataires;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * CRUD et cycle de vie du Locataire (EP-15, ADR-16 D2). Réservé au rôle {@code BAILLEUR} ; la
 * RLS (`bailleur_isolation`, V24) garantit le cloisonnement, sans prédicat ReBAC supplémentaire
 * nécessaire (contrairement au Gestionnaire, global et hors RLS).
 */
@RestController
@RequestMapping("/api/locataires")
@PreAuthorize("hasRole('BAILLEUR')")
public class LocataireController {

    private final LocataireService locataires;

    public LocataireController(LocataireService locataires) {
        this.locataires = locataires;
    }

    @GetMapping
    public List<LocataireDto> rechercher(@RequestParam(required = false) String q,
            Authentication authentication) {
        return locataires.rechercher(authentication, q);
    }

    @GetMapping("/verification-doublon")
    public List<LocataireDto> verificationDoublon(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String numeroPieceIdentite,
            Authentication authentication) {
        return locataires.detecterDoublons(authentication, email, telephone, numeroPieceIdentite);
    }

    @GetMapping("/{id}")
    public LocataireDto consulter(@PathVariable UUID id, Authentication authentication) {
        return locataires.consulter(id, authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocataireDto creer(@Valid @RequestBody LocataireRequest requete, Authentication authentication) {
        return locataires.creer(requete, authentication);
    }

    @PutMapping("/{id}")
    public LocataireDto modifier(@PathVariable UUID id, @Valid @RequestBody LocataireRequest requete,
            Authentication authentication) {
        return locataires.modifier(id, requete, authentication);
    }

    @DeleteMapping("/{id}")
    public LocataireDto archiver(@PathVariable UUID id, Authentication authentication) {
        return locataires.archiver(id, authentication);
    }

    @PostMapping("/{id}/restauration")
    public LocataireDto restaurer(@PathVariable UUID id, Authentication authentication) {
        return locataires.restaurer(id, authentication);
    }

    @GetMapping("/{id}/historique")
    public LocataireHistoriqueDto historique(@PathVariable UUID id, Authentication authentication) {
        return locataires.historique(id, authentication);
    }
}
