package com.loyertracker.comptes;

/**
 * Port (hexagonal) vers le fournisseur d'identité gérant les comptes gestionnaire (ADR-10).
 *
 * <p>Isole le domaine de l'IdP concret (Keycloak) : l'implémentation de production
 * ({@code KeycloakGestionnaireIdentityProvider}) appelle l'Admin API ; les tests injectent un
 * double en mémoire.</p>
 */
public interface GestionnaireIdentityProvider {

    /**
     * Crée le compte gestionnaire dans l'IdP, ou le récupère s'il existe déjà (réutilisation
     * multi-bailleur, EF-05). Idempotent sur l'e-mail.
     *
     * @param motDePasse mot de passe initial (ignoré si le compte préexiste)
     * @return l'identité IdP (avec le {@code keycloakId}) et l'indication créé/réutilisé
     */
    GestionnaireIdentity creerOuRecuperer(String email, String nom, String prenom, String motDePasse);

    /**
     * Active ou désactive le compte dans l'IdP (EP-15, ADR-16 D1) : {@code SUSPENDU}/{@code
     * ARCHIVE} désactivent la connexion (statut GLOBAL, tous bailleurs), {@code RESTAURER}/
     * {@code REACTIVER} la rétablissent.
     *
     * <p>Méthode {@code default} pour ne pas casser les doubles de test existants (lambdas à une
     * seule méthode abstraite) qui n'ont pas besoin de ce comportement.</p>
     */
    default void definirActivation(String keycloakId, boolean actif) {
        // no-op par défaut ; l'adaptateur Keycloak et les tests dédiés au cycle de vie l'implémentent.
    }
}
