package com.loyertracker.garanties;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GarantieMovementRepository extends JpaRepository<GarantieMovement, UUID> {

    /**
     * Ordre chronologique stable (RSV-S10-01) : {@code date_mouvement} est un DATE, deux
     * mouvements d'un même jour se départagent par {@code cree_le} (timestamp d'insertion,
     * DEFAULT now() Postgres) puis par id en ultime recours.
     */
    List<GarantieMovement> findByGarantieIdOrderByDateMouvementAscCreeLeAscIdAsc(UUID garantieId);

    /** Chargement par lot (export RGPD) : évite une requête par garantie. Même ordre stable. */
    List<GarantieMovement> findByGarantieIdInOrderByDateMouvementAscCreeLeAscIdAsc(
            List<UUID> garantieIds);
}
