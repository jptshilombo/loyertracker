package com.loyertracker.documents;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loyertracker.quittances.QuittanceCertifieeService;

/**
 * Documents locatifs d'un loyer. L'avis d'échéance reste généré à la volée (arbitrage C) ; la
 * quittance est depuis EP-14 un document certifié persistant (ADR-15) — le contrat HTTP est
 * inchangé, la réponse est l'exemplaire officiel PDF. Accès réservé au bailleur propriétaire ou
 * au gestionnaire affecté actif (ReBAC, {@code @authz}), comme l'accès aux paiements.
 */
@RestController
@RequestMapping("/api/biens/{bienId}/paiements/{periode}")
public class DocumentController {

    private final QuittanceService quittanceService;
    private final QuittanceCertifieeService quittanceCertifieeService;

    public DocumentController(QuittanceService quittanceService,
            QuittanceCertifieeService quittanceCertifieeService) {
        this.quittanceService = quittanceService;
        this.quittanceCertifieeService = quittanceCertifieeService;
    }

    @GetMapping("/quittance")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public ResponseEntity<byte[]> quittance(@PathVariable UUID bienId, @PathVariable String periode,
            Authentication authentication) {
        return pdf(quittanceCertifieeService.emettre(bienId, periode, authentication),
                "quittance", periode);
    }

    @GetMapping("/avis-echeance")
    @PreAuthorize("hasAnyRole('BAILLEUR', 'GESTIONNAIRE') and @authz.peutAccederBien(#bienId, authentication)")
    public ResponseEntity<byte[]> avisEcheance(@PathVariable UUID bienId,
            @PathVariable String periode) {
        return pdf(quittanceService.avisEcheance(bienId, periode), "avis-echeance", periode);
    }

    private static ResponseEntity<byte[]> pdf(byte[] contenu, String prefixe, String periode) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(prefixe + "-" + periode + ".pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(contenu);
    }
}
