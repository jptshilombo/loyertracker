package com.loyertracker.audit;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation du journal d'audit (US-62, EF-73 / ENF-05). Réservée au <strong>bailleur</strong> :
 * un gestionnaire reçoit un 403. Le tenant courant (RLS) borne la lecture aux lignes du bailleur.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('BAILLEUR')")
    public List<AuditDto> consulter(Authentication authentication) {
        return auditService.consulter(authentication);
    }
}
