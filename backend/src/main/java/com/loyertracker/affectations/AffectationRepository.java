package com.loyertracker.affectations;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AffectationRepository extends JpaRepository<Affectation, UUID> {

    List<Affectation> findByBienIdOrderByDateDebutDesc(UUID bienId);

    List<Affectation> findByPatrimoineIdOrderByDateDebutDesc(UUID patrimoineId);

    boolean existsByPatrimoineIdAndStatut(UUID patrimoineId, StatutAffectation statut);

    /** RS-04 : une affectation bien EXCLUSION exige une affectation patrimoine ACTIVE de ce gestionnaire. */
    boolean existsByPatrimoineIdAndGestionnaireIdAndStatut(UUID patrimoineId, UUID gestionnaireId,
            StatutAffectation statut);
}
