package com.loyertracker.comptes;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès persistance des {@link Invitation}.
 *
 * <p>⚠️ La RLS s'applique : {@link #findByToken(String)} ne renvoie l'invitation que si le contexte
 * tenant ({@code app.current_bailleur_id}) a été positionné au préalable — lors de l'acceptation
 * non authentifiée (US-12), via {@code TenantContext.activerDepuisInvitation(token)} qui résout le
 * tenant porteur du token (ADR-09). Sans contexte : <em>fail-closed</em> (0 ligne).</p>
 */
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);
}
