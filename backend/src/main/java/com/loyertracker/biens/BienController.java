package com.loyertracker.biens;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint biens — squelette (étape 04).
 *
 * <p>L'accès exige un rôle métier (RBAC Keycloak via {@code @PreAuthorize}). La persistance et le
 * filtrage par périmètre (propriété bailleur / affectation gestionnaire — ADR-01/02) arrivent à
 * l'étape 06 ; pour l'instant la liste est vide.</p>
 */
@RestController
@RequestMapping("/api/biens")
public class BienController {

    @GetMapping
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE')")
    public List<BienDto> lister(Authentication authentication) {
        return List.of();
    }
}
