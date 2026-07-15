package com.loyertracker.comptes;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Administration du profil et du cycle de vie des Gestionnaires (EP-15, ADR-16). Réservé au
 * rôle {@code BAILLEUR} ayant (ou ayant eu) une relation d'affectation avec le gestionnaire visé
 * (RM-107 : un Gestionnaire n'administre jamais un autre Gestionnaire).
 *
 * <p>« Créer » un Gestionnaire (K1) désigne ici l'enrichissement du profil d'un compte déjà créé
 * par invitation ({@link InvitationController}) — aucun nouveau flux de création de compte.</p>
 */
@RestController
@RequestMapping("/api/gestionnaires")
public class GestionnaireController {

    private final GestionnaireService gestionnaires;

    public GestionnaireController(GestionnaireService gestionnaires) {
        this.gestionnaires = gestionnaires;
    }

    @GetMapping
    @PreAuthorize("hasRole('BAILLEUR')")
    public List<GestionnaireDto> rechercher(@RequestParam(required = false) String q,
            Authentication authentication) {
        return gestionnaires.rechercher(authentication, q);
    }

    @GetMapping("/verification-doublon")
    @PreAuthorize("hasRole('BAILLEUR')")
    public List<GestionnaireDto> verificationDoublon(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String telephone,
            Authentication authentication) {
        return gestionnaires.detecterDoublons(authentication, email, telephone);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireDto consulter(@PathVariable UUID id, Authentication authentication) {
        return gestionnaires.consulter(id, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireDto modifierProfil(@PathVariable UUID id,
            @Valid @RequestBody GestionnaireProfilRequest requete, Authentication authentication) {
        return gestionnaires.modifierProfil(id, requete, authentication);
    }

    @PostMapping("/{id}/suspension")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireDto suspendre(@PathVariable UUID id, Authentication authentication) {
        return gestionnaires.suspendre(id, authentication);
    }

    @PostMapping("/{id}/reactivation")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireDto reactiver(@PathVariable UUID id, Authentication authentication) {
        return gestionnaires.reactiver(id, authentication);
    }

    @PostMapping("/{id}/archivage")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireDto archiver(@PathVariable UUID id, Authentication authentication) {
        return gestionnaires.archiver(id, authentication);
    }

    @PostMapping("/{id}/restauration")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireDto restaurer(@PathVariable UUID id, Authentication authentication) {
        return gestionnaires.restaurer(id, authentication);
    }

    @GetMapping("/{id}/historique")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederGestionnaire(#id, authentication)")
    public GestionnaireHistoriqueDto historique(@PathVariable UUID id, Authentication authentication) {
        return gestionnaires.historique(id, authentication);
    }
}
