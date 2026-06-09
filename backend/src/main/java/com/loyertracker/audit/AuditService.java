package com.loyertracker.audit;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;

/**
 * Journalisation des actions d'écriture sensibles (BNF-05 / ENF-05).
 *
 * <p>Écrit une ligne {@code audit_log} dans la transaction de l'opération métier appelante : le
 * GUC {@code app.current_bailleur_id} y est déjà positionné (TenantContext), donc l'insertion
 * respecte la RLS (le {@code bailleur_id} journalisé est celui du tenant courant). La consultation
 * de l'audit est livrée séparément (US-62).</p>
 */
@Service
public class AuditService {

    private final EntityManager em;

    public AuditService(EntityManager em) {
        this.em = em;
    }

    /**
     * Enregistre une action d'écriture.
     *
     * @param authentication acteur courant (JWT Keycloak)
     * @param bailleurId tenant courant (déjà résolu par l'opération métier)
     * @param action verbe métier, p. ex. {@code POINTER_PAIEMENT} (BNF-05)
     * @param entityType type d'entité concernée, p. ex. {@code paiement}
     * @param entityId identifiant de l'entité concernée
     */
    public void enregistrer(Authentication authentication, UUID bailleurId, String action,
            String entityType, UUID entityId) {
        String role = aRole(authentication, "ROLE_GESTIONNAIRE") ? "GESTIONNAIRE" : "BAILLEUR";
        UUID acteurId = resoudreActeur(authentication, role, bailleurId);
        em.createNativeQuery("""
                INSERT INTO audit_log (bailleur_id, acteur_id, acteur_role, action, entity_type, entity_id)
                VALUES (CAST(:b AS uuid), CAST(:a AS uuid), :role, :action, :type, CAST(:eid AS uuid))
                """)
                .setParameter("b", bailleurId.toString())
                .setParameter("a", acteurId.toString())
                .setParameter("role", role)
                .setParameter("action", action)
                .setParameter("type", entityType)
                .setParameter("eid", entityId == null ? null : entityId.toString())
                .executeUpdate();
    }

    /** Bailleur : acteur = tenant courant. Gestionnaire : id résolu depuis le {@code sub} Keycloak. */
    private UUID resoudreActeur(Authentication authentication, String role, UUID bailleurId) {
        if ("BAILLEUR".equals(role)) {
            return bailleurId;
        }
        String keycloakId = ((Jwt) authentication.getPrincipal()).getSubject();
        return (UUID) em.createNativeQuery("SELECT id FROM gestionnaire WHERE keycloak_id = :s")
                .setParameter("s", keycloakId)
                .getSingleResult();
    }

    private static boolean aRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(autorite -> role.equals(autorite.getAuthority()));
    }
}
