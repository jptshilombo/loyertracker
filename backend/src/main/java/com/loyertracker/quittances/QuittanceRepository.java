package com.loyertracker.quittances;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuittanceRepository extends JpaRepository<Quittance, UUID> {

    /** Exemplaire officiel courant d'un loyer (au plus un — index partiel V22). */
    Optional<Quittance> findByPaiementIdAndStatut(UUID paiementId, StatutQuittance statut);

    /** Historique complet des versions d'un loyer, plus récente d'abord. */
    List<Quittance> findByPaiementIdOrderByVersionDesc(UUID paiementId);

    /** Toutes les quittances du tenant courant (RLS), plus récentes d'abord — export RGPD. */
    List<Quittance> findByOrderByEmiseLeDesc();
}
