package com.loyertracker.notifications;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loyertracker.notifications.provider.TwilioSignatureVerifier;

/**
 * Callback de statut Twilio (US-123, ADR-18 §Sécurité). <strong>Non authentifié</strong> (liste
 * blanche {@code SecurityConfig}, patron {@code PublicQuittanceController}) : la seule preuve
 * d'origine est la signature {@code X-Twilio-Signature}, vérifiée avant tout traitement. Déjà
 * couvert par le rate-limit Nginx existant du préfixe {@code /api/public/} (aucune configuration
 * dédiée nécessaire, {@code infra/nginx/nginx.conf}). Réponse toujours indifférenciée (204/403) —
 * aucun détail (SID inconnu, callback dupliqué, statut ignoré) n'est exposé à l'appelant.
 */
@RestController
@RequestMapping("/api/public/notifications")
public class TwilioCallbackController {

    private final NotificationDeliveryService deliveries;
    private final TwilioSignatureVerifier signature;
    private final String callbackUrl;

    public TwilioCallbackController(NotificationDeliveryService deliveries,
            TwilioSignatureVerifier signature,
            @Value("${twilio.status-callback-base-url:https://loyertracker.loyerpro.org}") String statusCallbackBaseUrl) {
        this.deliveries = deliveries;
        this.signature = signature;
        this.callbackUrl = statusCallbackBaseUrl + "/api/public/notifications/callback";
    }

    @PostMapping(path = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> recevoirCallback(
            @RequestHeader(name = "X-Twilio-Signature", required = false) String signatureRecue,
            @RequestParam MultiValueMap<String, String> formulaire) {
        Map<String, String> parametres = formulaire.toSingleValueMap();
        if (!signature.estValide(signatureRecue, callbackUrl, parametres)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String sid = parametres.get("MessageSid");
        String statut = parametres.get("MessageStatus");
        if (sid == null || statut == null) {
            return ResponseEntity.badRequest().build();
        }
        deliveries.appliquerCallback(sid, statut, parametres.get("ErrorCode"));
        return ResponseEntity.noContent().build();
    }
}
