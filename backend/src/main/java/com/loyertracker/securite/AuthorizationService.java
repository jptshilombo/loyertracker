package com.loyertracker.securite;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Autorisation fine applicative (ReBAC) — ADR-02, 2ᵉ couche de la défense en profondeur (ADR-01).
 *
 * <p>Keycloak ne porte que le RBAC grossier ({@code BAILLEUR} / {@code GESTIONNAIRE}). L'accès à
 * un bien précis dépend de la propriété (bailleur) ou d'une affectation <em>ACTIVE</em>
 * (gestionnaire) ; il est évalué ici, puis renforcé en base par la Row-Level Security PostgreSQL.</p>
 *
 * <p>Exposé sous le nom {@code authz} pour être appelé depuis les annotations, p. ex. :
 * {@code @PreAuthorize("@authz.peutAccederBien(#bienId, authentication)")}.</p>
 *
 * <p><strong>Squelette (étape 04)</strong> : les implémentations réelles interrogeront la base
 * (étape 06 — schéma + RLS). Par défaut <em>fail-closed</em> (refus).</p>
 */
@Service("authz")
public class AuthorizationService {

    /** Vrai si le bailleur est propriétaire du bien. TODO étape 06 : requête {@code bien}. */
    public boolean estBailleurProprietaire(UUID bienId, UUID bailleurId) {
        return false;
    }

    /** Vrai s'il existe une affectation ACTIVE (bien, gestionnaire). TODO étape 06. */
    public boolean estGestionnaireAffecteActif(UUID bienId, UUID gestionnaireId) {
        return false;
    }

    /** Combine rôle et propriété/affectation. TODO étape 06 ; <em>fail-closed</em> pour l'instant. */
    public boolean peutAccederBien(UUID bienId, Authentication authentication) {
        return false;
    }
}
