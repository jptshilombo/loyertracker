package com.loyertracker.biens;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BienRepository extends JpaRepository<Bien, UUID> {

    List<Bien> findByBailleurIdOrderByAdresseAsc(UUID bailleurId);
}
