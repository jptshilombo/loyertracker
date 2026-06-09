package com.loyertracker.garanties;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GarantieRepository extends JpaRepository<Garantie, UUID> {

    List<Garantie> findByBailIdOrderByDateDepotDesc(UUID bailId);
}
