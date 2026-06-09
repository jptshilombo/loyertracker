package com.loyertracker.paiements;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {

    List<Paiement> findByBienIdOrderByPeriodeDesc(UUID bienId);

    Optional<Paiement> findByBienIdAndPeriode(UUID bienId, String periode);
}
