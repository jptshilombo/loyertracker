package com.loyertracker.comptes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loyertracker.securite.TenantContext;

/**
 * Génération d'invitations de délégation (US-11, EF-02/03).
 *
 * <p>Un bailleur authentifié invite un gestionnaire par e-mail. Le service produit une capacité
 * tokenisée (UUID v4) à usage unique, valable {@value #VALIDITE_HEURES} h, et renvoie le lien
 * d'acceptation. L'écriture est scopée au tenant via le contexte RLS (ADR-01/ADR-09) : le bailleur
 * émetteur est résolu depuis le JWT, jamais depuis la requête.</p>
 */
@Service
public class InvitationService {

    static final long VALIDITE_HEURES = 72;

    private final InvitationRepository invitations;
    private final TenantContext tenant;
    private final String baseUrl;

    public InvitationService(InvitationRepository invitations, TenantContext tenant,
            @Value("${app.invitation.base-url:https://localhost}") String baseUrl) {
        this.invitations = invitations;
        this.tenant = tenant;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public InvitationDto inviter(Jwt jwt, String email) {
        UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());

        OffsetDateTime expiration = OffsetDateTime.now(ZoneOffset.UTC)
                .plus(Duration.ofHours(VALIDITE_HEURES));
        Invitation invitation = new Invitation(
                UUID.randomUUID(), bailleurId, email, UUID.randomUUID().toString(), expiration);

        Invitation enregistree = invitations.saveAndFlush(invitation);
        return InvitationDto.from(enregistree, lienAcceptation(enregistree.getToken()));
    }

    private String lienAcceptation(String token) {
        return baseUrl + "/invitations/" + token;
    }
}
