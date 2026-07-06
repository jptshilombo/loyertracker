package com.loyertracker.quittances;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API publique de vérification et de téléchargement des quittances certifiées (US-102, ADR-15 D5).
 * <strong>Non authentifiée</strong> (liste blanche {@code SecurityConfig}) : la seule preuve
 * d'autorisation est le token HMAC porté par le QR. Toutes les réponses d'échec sont
 * indifférenciées (aucun oracle) — voir {@link VerificationQuittanceService}.
 *
 * <p>L'identifiant est reçu en {@code String} et parsé ici : un identifiant mal formé produit la
 * même réponse invalide qu'un identifiant inconnu (un {@code @PathVariable UUID} lèverait un 400
 * distinctif, exploitable comme oracle).</p>
 */
@RestController
@RequestMapping("/api/public/receipts")
public class PublicQuittanceController {

    private final VerificationQuittanceService verification;

    public PublicQuittanceController(VerificationQuittanceService verification) {
        this.verification = verification;
    }

    @GetMapping("/{id}")
    public PublicVerificationResponse verifier(@PathVariable String id,
            @RequestParam(required = false) String token) {
        return parseUuid(id)
                .flatMap(uuid -> verification.verifier(uuid, token))
                .map(PublicVerificationResponse::valide)
                .orElseGet(PublicVerificationResponse::invalide);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> telecharger(@PathVariable String id,
            @RequestParam(required = false) String token) {
        return parseUuid(id)
                .flatMap(uuid -> verification.telecharger(uuid, token))
                .map(PublicQuittanceController::pdf)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Optional<UUID> parseUuid(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<byte[]> pdf(byte[] contenu) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("quittance-certifiee.pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(contenu);
    }
}
