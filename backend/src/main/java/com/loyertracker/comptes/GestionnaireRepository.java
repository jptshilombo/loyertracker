package com.loyertracker.comptes;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès persistance des {@link Gestionnaire}.
 *
 * <p>Aucune RLS sur cette table (acteur global multi-bailleur, ADR-01) : les lectures par
 * {@code keycloakId} sont fiables et servent à la réutilisation idempotente du compte (EF-05).</p>
 */
public interface GestionnaireRepository extends JpaRepository<Gestionnaire, UUID> {

    Optional<Gestionnaire> findByKeycloakId(String keycloakId);
}
