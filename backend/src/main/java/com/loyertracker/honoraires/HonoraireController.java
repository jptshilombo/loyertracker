package com.loyertracker.honoraires;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Consultation et validation des honoraires de gestion (US-40).
 *
 * <p>La consultation par bien est ouverte au bailleur propriétaire comme au gestionnaire affecté
 * actif (ReBAC {@code @authz}). La transition de statut (notamment la validation/paiement) est
 * réservée au <strong>bailleur</strong> (EF-52) : le gestionnaire reçoit un 403. L'accès à
 * l'honoraire ciblé est borné par la RLS au tenant du bailleur appelant.</p>
 */
@RestController
public class HonoraireController {

    private final HonoraireService honoraireService;

    public HonoraireController(HonoraireService honoraireService) {
        this.honoraireService = honoraireService;
    }

    @GetMapping("/api/biens/{bienId}/honoraires")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public List<HonoraireDto> lister(@PathVariable UUID bienId) {
        return honoraireService.lister(bienId);
    }

    @PatchMapping("/api/honoraires/{id}/statut")
    @PreAuthorize("hasRole('BAILLEUR')")
    public HonoraireDto changerStatut(@PathVariable UUID id, @Valid @RequestBody StatutRequest requete,
            Authentication authentication) {
        return honoraireService.changerStatut(id, requete.statut(), authentication);
    }
}
