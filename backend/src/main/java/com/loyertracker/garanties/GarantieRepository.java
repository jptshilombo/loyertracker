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
     * Montant total déposé sur un bail (ADR-14 §8) : somme de {@code montant} des garanties
     * rattachées — équivalent, tant que {@code COMPLEMENT} n'est pas exposé métier (Sprint 10),
     * à la somme des mouvements {@code DEPOT_INITIAL} du ledger. Sert de valeur dérivée pour
     * {@code BailDto.depotGarantie}, `bail.depot_garantie` étant supprimée en V20.
     */
    @Query("select coalesce(sum(g.montant), 0) from Garantie g where g.bailId = :bailId")
    BigDecimal sommeMontantDeposeParBail(@Param("bailId") UUID bailId);
}
