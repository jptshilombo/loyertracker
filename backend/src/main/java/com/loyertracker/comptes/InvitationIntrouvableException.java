package com.loyertracker.comptes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée quand un token d'invitation est inconnu (aucune invitation correspondante). Traduite en
 * {@code 404 Not Found} — on ne distingue pas « jamais émis » de « purgé » (pas de fuite d'info).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvitationIntrouvableException extends RuntimeException {

    public InvitationIntrouvableException(String message) {
        super(message);
    }
}
