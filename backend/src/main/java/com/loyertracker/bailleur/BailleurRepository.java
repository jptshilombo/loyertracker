package com.loyertracker.bailleur;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès persistance des {@link Bailleur}.
 *
 * <p>⚠️ La RLS s'applique à ces requêtes : sans contexte tenant positionné
 * ({@code app.current_bailleur_id}), une lecture est <em>fail-closed</em> (0 ligne). La recherche
 * inter-tenant (p. ex. par {@code keycloakId} avant inscription) n'est donc pas fiable sous RLS —
 * l'unicité est garantie par la contrainte {@code UNIQUE(keycloak_id)} en base.</p>
 */
public interface BailleurRepository extends JpaRepository<Bailleur, UUID> {

    Optional<Bailleur> findByKeycloakId(String keycloakId);
}
