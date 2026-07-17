package com.loyertracker.biens;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loyertracker.baux.BailService;
import com.loyertracker.locataires.LocataireDto;

/**
 * Lecture seule des locataires du bailleur propriétaire d'un bien (EP-15 Sprint C). Contrairement
 * à {@code /api/locataires} (réservé BAILLEUR), cet endpoint est ouvert au GESTIONNAIRE affecté
 * au bien : sans lui, la création de bail côté tableau de bord Gestionnaire (déjà en Production)
 * casserait dès que {@code BailRequest.locataireId} devient obligatoire. Jamais de création ici.
 */
@RestController
@RequestMapping("/api/biens/{bienId}/locataires")
public class BienLocataireController {

    private final BailService bailService;

    public BienLocataireController(BailService bailService) {
        this.bailService = bailService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public List<LocataireDto> lister(@PathVariable UUID bienId) {
        return bailService.locatairesDuBien(bienId);
    }
}
