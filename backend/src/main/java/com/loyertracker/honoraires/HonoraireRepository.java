package com.loyertracker.honoraires;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HonoraireRepository extends JpaRepository<Honoraire, UUID> {

    /**
     * Honoraires d'un bien (via ses affectations), du plus récent au plus ancien. La RLS borne déjà
     * la lecture au tenant courant ; la jointure restreint au bien ciblé.
     */
    @Query("""
            SELECT h FROM Honoraire h
            WHERE h.affectationId IN (
                SELECT a.id FROM Affectation a WHERE a.bienId = :bienId)
            ORDER BY h.periode DESC
            """)
    List<Honoraire> findByBien(@Param("bienId") UUID bienId);
}
