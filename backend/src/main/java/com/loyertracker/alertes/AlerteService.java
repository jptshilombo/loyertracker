package com.loyertracker.alertes;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.securite.TenantContext;

import jakarta.persistence.EntityManager;

/**
 * Génération, consultation et marquage des alertes de pilotage (US-50/51/52, EF-60/61/63/64/65).
 *
 * <p>La génération est déléguée à {@code generer_alertes()} (V9), {@code SECURITY DEFINER}
 * multi-bailleur et idempotente. La lecture est cloisonnée : le <strong>bailleur</strong> voit
 * toutes les alertes de son tenant (RLS) ; le <strong>gestionnaire</strong> — qui n'a pas de tenant
 * propre — accède via les fonctions {@code SECURITY DEFINER} bornées à ses affectations ACTIVES.</p>
 */
@Service
public class AlerteService {

    private final AlerteRepository alertes;
    private final TenantContext tenant;
    private final EntityManager em;

    public AlerteService(AlerteRepository alertes, TenantContext tenant, EntityManager em) {
        this.alertes = alertes;
        this.tenant = tenant;
        this.em = em;
    }

    /** Génération batch multi-bailleur (idempotente). @return nombre d'alertes créées. */
    @Transactional
    public int genererBatch() {
        return ((Number) em.createNativeQuery("SELECT generer_alertes()").getSingleResult())
                .intValue();
    }

    @Transactional(readOnly = true)
    public List<AlerteDto> lister(Authentication authentication) {
        if (estGestionnaire(authentication)) {
            UUID gestionnaireId = resoudreGestionnaire(sujet(authentication));
            if (gestionnaireId == null) {
                return List.of();
            }
            @SuppressWarnings("unchecked")
            List<Alerte> resultats = em
                    .createNativeQuery("SELECT * FROM alertes_gestionnaire(CAST(:g AS uuid))",
                            Alerte.class)
                    .setParameter("g", gestionnaireId.toString())
                    .getResultList();
            return resultats.stream()
                    .sorted(Comparator.comparing(Alerte::getDateCreation).reversed())
                    .map(AlerteDto::from)
                    .toList();
        }
        tenant.activerDepuisKeycloak(sujet(authentication));
        return alertes.findByOrderByDateCreationDesc().stream().map(AlerteDto::from).toList();
    }

    @Transactional
    public AlerteDto marquerLue(UUID alerteId, Authentication authentication) {
        if (estGestionnaire(authentication)) {
            UUID gestionnaireId = resoudreGestionnaire(sujet(authentication));
            UUID bailleurId = gestionnaireId == null ? null
                    : (UUID) em
                            .createNativeQuery(
                                    "SELECT alerte_bailleur_pour_gestionnaire(CAST(:a AS uuid), CAST(:g AS uuid))")
                            .setParameter("a", alerteId.toString())
                            .setParameter("g", gestionnaireId.toString())
                            .getSingleResult();
            if (bailleurId == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerte introuvable.");
            }
            tenant.positionner(bailleurId);
            return marquer(alerteId, bailleurId);
        }
        UUID bailleurId = tenant.activerDepuisKeycloak(sujet(authentication));
        return marquer(alerteId, bailleurId);
    }

    /** Charge l'alerte sous RLS, vérifie l'appartenance au tenant (défense en profondeur) et marque lue. */
    private AlerteDto marquer(UUID alerteId, UUID bailleurId) {
        Alerte alerte = alertes.findById(alerteId)
                .filter(a -> a.getBailleurId().equals(bailleurId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Alerte introuvable."));
        alerte.marquerLue();
        return AlerteDto.from(alertes.save(alerte));
    }

    private UUID resoudreGestionnaire(String keycloakId) {
        // Table gestionnaire hors RLS (acteur global) : lecture directe fiable.
        List<?> resultats = em.createNativeQuery("SELECT id FROM gestionnaire WHERE keycloak_id = :s")
                .setParameter("s", keycloakId)
                .getResultList();
        return resultats.isEmpty() ? null : (UUID) resultats.get(0);
    }

    private static String sujet(Authentication authentication) {
        return ((Jwt) authentication.getPrincipal()).getSubject();
    }

    private static boolean estGestionnaire(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_GESTIONNAIRE".equals(a.getAuthority()));
    }
}
