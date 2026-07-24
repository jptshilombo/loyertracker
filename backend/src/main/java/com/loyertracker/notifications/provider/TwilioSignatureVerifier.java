package com.loyertracker.notifications.provider;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Vérification de la signature {@code X-Twilio-Signature} des callbacks de statut (US-123,
 * ADR-18 §Sécurité) : {@code base64(HMAC-SHA1(authToken, url + paramètres triés par clé,
 * concaténés clé+valeur))}, comparaison en temps constant. L'URL utilisée est celle configurée
 * ({@code TWILIO_STATUS_CALLBACK_BASE_URL}), jamais reconstruite depuis la requête entrante (un
 * en-tête {@code X-Forwarded-*} falsifié ne doit jamais influencer le calcul).
 */
@Component
public class TwilioSignatureVerifier {

    private final String authToken;

    public TwilioSignatureVerifier(@Value("${twilio.auth-token:}") String authToken) {
        this.authToken = authToken;
    }

    /** {@code true} seulement si la signature reçue correspond exactement à celle calculée. */
    public boolean estValide(String signatureRecue, String urlComplete, Map<String, String> parametres) {
        if (signatureRecue == null || signatureRecue.isBlank() || authToken == null || authToken.isBlank()) {
            return false;
        }
        StringBuilder donnees = new StringBuilder(urlComplete);
        parametres.keySet().stream().sorted()
                .forEach(cle -> donnees.append(cle).append(parametres.get(cle)));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] calcule = mac.doFinal(donnees.toString().getBytes(StandardCharsets.UTF_8));
            String calculeB64 = Base64.getEncoder().encodeToString(calcule);
            return MessageDigest.isEqual(calculeB64.getBytes(StandardCharsets.UTF_8),
                    signatureRecue.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Impossible de calculer la signature Twilio.", e);
        }
    }
}
