package com.loyertracker.comptes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès persistance des {@link Gestionnaire}.
 *
 * <p>Aucune RLS sur cette table (acteur global multi-bailleur, ADR-01) : les lectures par
 * {@code keycloakId} sont fiables et servent à la réutilisation idempotente du compte (EF-05).</p>
 */
public interface GestionnaireRepository extends JpaRepository<Gestionnaire, UUID> {

    Optional<Gestionnaire> findByKeycloakId(String keycloakId);

    /**
     * Gestionnaires ayant (ou ayant eu) une relation d'affectation avec ce bailleur (EP-15,
     * EF-102) — périmètre de recherche/liste, cohérent avec {@code peutAccederGestionnaire}.
     */
    @Query("""
            SELECT DISTINCT g FROM Gestionnaire g
            WHERE g.id IN (SELECT a.gestionnaireId FROM Affectation a WHERE a.bailleurId = :bailleurId)
            ORDER BY g.nom, g.prenom
            """)
    List<Gestionnaire> findEnRelationAvecBailleur(@Param("bailleurId") UUID bailleurId);
}
