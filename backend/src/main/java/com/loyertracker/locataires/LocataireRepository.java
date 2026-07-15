package com.loyertracker.locataires;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS `bailleur_isolation` (V24) : toute lecture est déjà scopée au bailleur courant. */
public interface LocataireRepository extends JpaRepository<Locataire, UUID> {

    List<Locataire> findByBailleurIdOrderByNomAscPrenomAsc(UUID bailleurId);
}
