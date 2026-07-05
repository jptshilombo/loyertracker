package com.loyertracker.quittances;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Token de vérification d'une quittance certifiée (ADR-15 D3) :
 * {@code base64url(HMAC-SHA256(secret, id "." version "." contentHash))}.
 *
 * <p>Choix de conception : HMAC dédié plutôt que JWT (aucun claim à transporter, token court
 * pour un QR peu dense, pas de surface de confusion d'algorithme) ; token <em>non stocké</em>
 * (une fuite de la base ne donne aucun token) et <em>non expirant</em> (une quittance papier
 * doit rester vérifiable des années — la révocation passe par le statut en base).
 * {@code token_kid} permet une rotation de secret sans invalider les QR imprimés.</p>
 */
@Service
public class TokenQuittanceService {

    private static final Logger log = LoggerFactory.getLogger(TokenQuittanceService.class);

    private final byte[] secret;
    private final short kid;

    public TokenQuittanceService(
            @Value("${quittances.hmac-secret:}") String secretConfigure,
            @Value("${quittances.token-kid:1}") short kid) {
        if (secretConfigure == null || secretConfigure.isBlank()) {
            // Fail-safe sans secret par défaut en dur : secret éphémère aléatoire — les tokens ne
            // survivent pas au redémarrage. En Production, QUITTANCE_HMAC_SECRET est exigé au
            // préflight (checklist Gate Production, ADR-15 D3).
            byte[] ephemere = new byte[32];
            new SecureRandom().nextBytes(ephemere);
            this.secret = ephemere;
            log.warn("QUITTANCE_HMAC_SECRET absent : secret HMAC éphémère généré — les QR émis ne "
                    + "resteront pas vérifiables après redémarrage. Configuration requise en "
                    + "Staging/Production.");
        } else {
            this.secret = secretConfigure.getBytes(StandardCharsets.UTF_8);
        }
        this.kid = kid;
    }

    /** Kid de la clé courante, persisté avec la quittance à l'émission. */
    public short kid() {
        return kid;
    }

    /** Token de vérification, lié au triplet identifiant + version + contenu certifié. */
    public String generer(UUID quittanceId, int version, String contentHash) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                hmac(quittanceId + "." + version + "." + contentHash));
    }

    /**
     * Vérifie un token présenté, en temps constant — un token forgé, tronqué, d'une autre
     * quittance ou d'une autre version est rejeté sans indice exploitable.
     */
    public boolean verifier(String tokenPresente, UUID quittanceId, int version, String contentHash) {
        if (tokenPresente == null || tokenPresente.isBlank()) {
            return false;
        }
        byte[] attendu = hmac(quittanceId + "." + version + "." + contentHash);
        byte[] presente;
        try {
            presente = Base64.getUrlDecoder().decode(tokenPresente);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(attendu, presente);
    }

    private byte[] hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Échec du calcul HMAC du token de quittance.", e);
        }
    }
}
