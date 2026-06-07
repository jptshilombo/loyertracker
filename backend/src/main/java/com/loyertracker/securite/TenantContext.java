package com.loyertracker.securite;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;

/**
 * Injection du contexte tenant pour la Row-Level Security (ADR-01 / ADR-09).
 *
 * <p>La RLS {@code FORCE} masque toute ligne dont {@code bailleur_id ≠ app.current_bailleur_id}.
 * Avant toute lecture/écriture métier, il faut donc positionner ce GUC. L'identité du JWT étant le
 * {@code sub} Keycloak (et non l'{@code id} applicatif), la résolution passe par les fonctions
 * {@code SECURITY DEFINER} de la migration V2, qui contournent la RLS de façon étroite et auditable.</p>
 *
 * <p><strong>À appeler à l'intérieur d'une méthode {@code @Transactional}</strong> : le
 * {@code set_config(..., is_local := true)} est lié à la transaction et doit s'exécuter sur la même
 * connexion que les requêtes JPA qui suivent (cf. {@code InscriptionService}).</p>
 */
@Component
public class TenantContext {

    private final EntityManager em;

    public TenantContext(EntityManager em) {
        this.em = em;
    }

    /**
     * Résout le bailleur depuis l'identité Keycloak du jeton et positionne le contexte RLS.
     *
     * @return l'{@code id} applicatif du bailleur courant
     * @throws ResponseStatusException 403 si aucun compte bailleur n'est rattaché à cette identité
     */
    public UUID activerDepuisKeycloak(String keycloakId) {
        UUID bailleurId = (UUID) em.createNativeQuery("SELECT resolve_bailleur_id(:arg)")
                .setParameter("arg", keycloakId)
                .getSingleResult();
        if (bailleurId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Aucun compte bailleur n'est rattaché à cette identité.");
        }
        positionner(bailleurId);
        return bailleurId;
    }

    /**
     * Résout le tenant porteur d'une invitation (acceptation non authentifiée, US-12) et positionne
     * le contexte RLS de sorte que l'invitation soit ensuite lisible via JPA.
     *
     * @return l'{@code id} du bailleur émetteur, ou {@code null} si le token est inconnu
     */
    public UUID activerDepuisInvitation(String token) {
        UUID bailleurId = (UUID) em.createNativeQuery("SELECT resolve_invitation_bailleur(:arg)")
                .setParameter("arg", token)
                .getSingleResult();
        if (bailleurId != null) {
            positionner(bailleurId);
        }
        return bailleurId;
    }

    /** Positionne {@code app.current_bailleur_id} sur la transaction courante (RLS). */
    public void positionner(UUID bailleurId) {
        em.createNativeQuery("SELECT set_config('app.current_bailleur_id', :id, true)")
                .setParameter("id", bailleurId.toString())
                .getSingleResult();
    }
}
