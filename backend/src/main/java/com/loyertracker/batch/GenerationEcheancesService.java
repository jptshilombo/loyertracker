package com.loyertracker.batch;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Génération des échéances de loyers à terme échu (US-30, EF-33, Annexe A.3).
 *
 * <p>Délègue à la fonction SQL {@code generer_echeances_loyers()} (V6), {@code SECURITY DEFINER}
 * propriété du rôle {@code loyertracker_batch} (BYPASSRLS) : un seul ordre ensembliste, idempotent,
 * couvrant tous les bailleurs. Aucun second datasource n'est requis.</p>
 */
@Service
public class GenerationEcheancesService {

    private final EntityManager em;

    public GenerationEcheancesService(EntityManager em) {
        this.em = em;
    }

    /** @return le nombre d'échéances créées (0 si tout est déjà à jour — idempotence). */
    @Transactional
    public int genererEcheances() {
        return ((Number) em.createNativeQuery("SELECT generer_echeances_loyers()")
                .getSingleResult()).intValue();
    }
}
