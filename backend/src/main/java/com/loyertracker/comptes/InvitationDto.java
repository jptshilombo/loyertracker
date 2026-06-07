package com.loyertracker.comptes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vue exposée d'une invitation émise (réponse US-11). Inclut le {@code lien} d'acceptation à
 * transmettre au gestionnaire et la date d'expiration (72 h). N'expose pas le {@code bailleur_id}
 * (donnée de cloisonnement interne).
 */
public record InvitationDto(
        UUID id,
        String email,
        String token,
        String lien,
        StatutInvitation statut,
        OffsetDateTime dateExpiration) {

    static InvitationDto from(Invitation invitation, String lien) {
        return new InvitationDto(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getToken(),
                lien,
                invitation.getStatut(),
                invitation.getDateExpiration());
    }
}
