package com.loyertracker.patrimoine;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatrimoineRepository extends JpaRepository<Patrimoine, UUID> {

    List<Patrimoine> findByBailleurIdOrderByNomAsc(UUID bailleurId);
}
