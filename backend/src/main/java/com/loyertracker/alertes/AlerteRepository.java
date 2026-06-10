package com.loyertracker.alertes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlerteRepository extends JpaRepository<Alerte, UUID> {

    /** Alertes du tenant courant (RLS), de la plus récente à la plus ancienne — vue bailleur. */
    List<Alerte> findByOrderByDateCreationDesc();
}
