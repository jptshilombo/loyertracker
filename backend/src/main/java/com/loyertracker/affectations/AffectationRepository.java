package com.loyertracker.affectations;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AffectationRepository extends JpaRepository<Affectation, UUID> {

    List<Affectation> findByBienIdOrderByDateDebutDesc(UUID bienId);
}
