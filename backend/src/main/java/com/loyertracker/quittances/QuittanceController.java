package com.loyertracker.quittances;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cycle de vie des quittances certifiées (US-99). L'émission passe par le contrat existant
 * {@code GET /api/biens/{bienId}/paiements/{periode}/quittance} ({@code DocumentController}) ;
 * l'annulation est réservée au bailleur propriétaire (la RLS masque toute quittance d'un autre
 * tenant — 404 indifférencié).
 */
@RestController
@RequestMapping("/api/quittances")
public class QuittanceController {

    private final QuittanceCertifieeService quittances;

    public QuittanceController(QuittanceCertifieeService quittances) {
        this.quittances = quittances;
    }

    @PostMapping("/{quittanceId}/annulation")
    @PreAuthorize("hasRole('BAILLEUR')")
    public ResponseEntity<Void> annuler(@PathVariable UUID quittanceId,
            Authentication authentication) {
        quittances.annuler(quittanceId, authentication);
        return ResponseEntity.noContent().build();
    }
}
