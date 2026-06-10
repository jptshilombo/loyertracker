package com.loyertracker.alertes;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation et marquage des alertes de pilotage (US-50/51/52).
 *
 * <p>Le bailleur voit toutes les alertes de son tenant ; le gestionnaire uniquement celles des biens
 * dont il a une affectation ACTIVE (scoping appliqué dans {@link AlerteService}). Le marquage « lue »
 * est ouvert aux deux rôles, dans leur périmètre respectif.</p>
 */
@RestController
@RequestMapping("/api/alertes")
public class AlerteController {

    private final AlerteService alerteService;

    public AlerteController(AlerteService alerteService) {
        this.alerteService = alerteService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE')")
    public List<AlerteDto> lister(Authentication authentication) {
        return alerteService.lister(authentication);
    }

    @PatchMapping("/{id}/lecture")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE')")
    public AlerteDto marquerLue(@PathVariable UUID id, Authentication authentication) {
        return alerteService.marquerLue(id, authentication);
    }
}
