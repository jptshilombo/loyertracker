package com.loyertracker.baux;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BailRepository extends JpaRepository<Bail, UUID> {

    boolean existsByBienIdAndStatut(UUID bienId, StatutBail statut);

    List<Bail> findByBienIdOrderByDateDebutDesc(UUID bienId);
}
