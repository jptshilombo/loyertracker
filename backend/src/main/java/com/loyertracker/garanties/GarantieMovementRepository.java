package com.loyertracker.garanties;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GarantieMovementRepository extends JpaRepository<GarantieMovement, UUID> {

    List<GarantieMovement> findByGarantieIdOrderByDateMouvementAscIdAsc(UUID garantieId);
}
