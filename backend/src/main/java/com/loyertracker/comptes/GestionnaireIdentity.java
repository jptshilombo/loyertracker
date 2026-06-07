package com.loyertracker.comptes;

/**
 * Identité d'un gestionnaire telle que retournée par l'IdP (ADR-10).
 *
 * @param keycloakId identifiant Keycloak (claim {@code sub} des futurs jetons)
 * @param cree {@code true} si le compte vient d'être créé, {@code false} s'il a été réutilisé (EF-05)
 */
public record GestionnaireIdentity(String keycloakId, boolean cree) {
}
