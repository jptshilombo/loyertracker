package com.loyertracker.securite;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Autorisation fine applicative (ReBAC) — ADR-02, 2ᵉ couche de la défense en profondeur (ADR-01).
 *
 * <p>Keycloak ne porte que le RBAC grossier ({@code BAILLEUR} / {@code GESTIONNAIRE}). L'accès à un
 * bien précis dépend de la propriété (bailleur) ou d'une affectation <em>ACTIVE</em> (gestionnaire) ;
 * il est évalué ici, puis renforcé en base par la RLS PostgreSQL.</p>
 *
 * <p>Exposé sous le nom {@code authz} pour les annotations, p. ex. :
 * {@code @PreAuthorize("@authz.peutAccederBien(#bienId, authentication)")}. L'évaluation a lieu
 * <strong>avant</strong> la transaction de service (aucun contexte tenant positionné) : les
 * prédicats s'appuient donc sur des fonctions {@code SECURITY DEFINER} (V3) qui contournent la RLS
 * de façon étroite et auditable (même patron qu'ADR-09).</p>
 */
@Service("authz")
public class AuthorizationService {

    private final EntityManager em;

    public AuthorizationService(EntityManager em) {
        this.em = em;
    }

    /** Combine rôle et propriété/affectation ; <em>fail-closed</em> en cas d'ambiguïté. */
    @Transactional(readOnly = true)
    public boolean peutAccederBien(UUID bienId, Authentication authentication) {
        if (bienId == null || authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        if (aRole(authentication, "ROLE_BAILLEUR")) {
            UUID bailleurId = resoudreBailleur(jwt.getSubject());
            return bailleurId != null && estBailleurProprietaire(bienId, bailleurId);
        }
        if (aRole(authentication, "ROLE_GESTIONNAIRE")) {
            UUID gestionnaireId = resoudreGestionnaire(jwt.getSubject());
            return gestionnaireId != null && estGestionnaireAffecteActif(bienId, gestionnaireId);
        }
        return false;
    }

    /** Vrai si le bailleur est propriétaire du bien (prédicat V3, contourne la RLS). */
    public boolean estBailleurProprietaire(UUID bienId, UUID bailleurId) {
        return (Boolean) em.createNativeQuery(
                        "SELECT bien_appartient_au_bailleur(CAST(:b AS uuid), CAST(:o AS uuid))")
                .setParameter("b", bienId.toString())
                .setParameter("o", bailleurId.toString())
                .getSingleResult();
    }

    /** Vrai s'il existe une affectation ACTIVE (bien, gestionnaire) — prédicat V3. */
    public boolean estGestionnaireAffecteActif(UUID bienId, UUID gestionnaireId) {
        return (Boolean) em.createNativeQuery(
                        "SELECT gestionnaire_affecte_actif(CAST(:b AS uuid), CAST(:g AS uuid))")
                .setParameter("b", bienId.toString())
                .setParameter("g", gestionnaireId.toString())
                .getSingleResult();
    }

    private UUID resoudreBailleur(String keycloakId) {
        return (UUID) em.createNativeQuery("SELECT resolve_bailleur_id(:s)")
                .setParameter("s", keycloakId)
                .getSingleResult();
    }

    private UUID resoudreGestionnaire(String keycloakId) {
        // La table gestionnaire n'est pas sous RLS (acteur global) : lecture directe fiable.
        List<?> resultats = em.createNativeQuery(
                        "SELECT id FROM gestionnaire WHERE keycloak_id = :s")
                .setParameter("s", keycloakId)
                .getResultList();
        return resultats.isEmpty() ? null : (UUID) resultats.get(0);
    }

    private static boolean aRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(autorite -> role.equals(autorite.getAuthority()));
    }
}
