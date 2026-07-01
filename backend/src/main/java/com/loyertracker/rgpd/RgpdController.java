package com.loyertracker.rgpd;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints RGPD (US-70 / ENF-04 / ADR-03).
 * Réservés au rôle BAILLEUR : un gestionnaire ne peut ni exporter ni effacer.
 */
@RestController
@RequestMapping("/api")
public class RgpdController {

    private final RgpdService rgpdService;

    public RgpdController(RgpdService rgpdService) {
        this.rgpdService = rgpdService;
    }

    /** Export JSON complet scopé par bailleur (droits RGPD — droit d'accès). */
    @GetMapping("/bailleurs/export")
    @PreAuthorize("hasRole('BAILLEUR')")
    public ExportBailleurDto exporter(Authentication authentication) {
        return rgpdService.exporter(authentication);
    }

    /** Anonymisation des données personnelles du locataire sur un bail (droits RGPD — droit à l'effacement). */
    @DeleteMapping("/biens/{bienId}/baux/{bailId}/locataire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BAILLEUR')")
    public void anonymiserLocataire(@PathVariable UUID bienId, @PathVariable UUID bailId,
            Authentication authentication) {
        rgpdService.anonymiserLocataire(bienId, bailId, authentication);
    }
}
