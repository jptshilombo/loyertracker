package com.loyertracker.comptes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée quand une invitation existe mais n'est plus consommable : déjà utilisée (usage unique) ou
 * expirée (fenêtre de 72 h dépassée, EF-03). Traduite en {@code 409 Conflict}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvitationNonAcceptableException extends RuntimeException {

    public InvitationNonAcceptableException(String message) {
        super(message);
    }
}
