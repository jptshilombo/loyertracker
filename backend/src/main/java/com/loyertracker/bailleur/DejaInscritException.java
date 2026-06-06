package com.loyertracker.bailleur;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée lorsqu'un compte Keycloak déjà inscrit retente une inscription (violation de l'unicité
 * {@code keycloak_id} / {@code email}). Traduite en {@code 409 Conflict}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DejaInscritException extends RuntimeException {

    public DejaInscritException(String message) {
        super(message);
    }
}
