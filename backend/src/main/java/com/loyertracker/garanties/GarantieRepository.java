package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GarantieRepository extends JpaRepository<Garantie, UUID> {

    List<Garantie> findByBailIdOrderByDateDepotDesc(UUID bailId);

    /**
     * Montant total déposé sur un bail (ADR-14 §8, recalculée au Sprint 10) : somme des crédits
     * {@code DEPOT_INITIAL}/{@code COMPLEMENT} du ledger de toutes les garanties rattachées au
     * bail — reflète désormais les réapprovisionnements (US-96), pas seulement le dépôt initial.
     * Sert de valeur dérivée pour {@code BailDto.depotGarantie}, `bail.depot_garantie` étant
     * supprimée en V20.
     */
    @Query(value = """
            SELECT COALESCE(SUM(gm.credit), 0)
            FROM garantie_movement gm
            JOIN garantie g ON g.id = gm.garantie_id
            WHERE g.bail_id = :bailId
              AND gm.type IN ('DEPOT_INITIAL', 'COMPLEMENT')
            """, nativeQuery = true)
    BigDecimal sommeMontantDeposeParBail(@Param("bailId") UUID bailId);
}
