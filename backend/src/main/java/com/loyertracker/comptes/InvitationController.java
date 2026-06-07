package com.loyertracker.comptes;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Endpoints d'invitation de délégation.
 *
 * <ul>
 *   <li><b>US-11</b> — émission ({@code POST /api/invitations}) : réservée au rôle {@code BAILLEUR}.</li>
 *   <li><b>US-12</b> — acceptation ({@code POST /api/invitations/{token}/acceptation}) : non
 *       authentifiée (whitelist {@code SecurityConfig}), le token portant la capacité.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;
    private final AcceptationService acceptationService;

    public InvitationController(InvitationService invitationService,
            AcceptationService acceptationService) {
        this.invitationService = invitationService;
        this.acceptationService = acceptationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BAILLEUR')")
    public InvitationDto inviter(@Valid @RequestBody InvitationRequest requete,
            @AuthenticationPrincipal Jwt jwt) {
        return invitationService.inviter(jwt, requete.email());
    }

    @PostMapping("/{token}/acceptation")
    @ResponseStatus(HttpStatus.CREATED)
    public AcceptationDto accepter(@PathVariable String token,
            @Valid @RequestBody AcceptationRequest requete) {
        return acceptationService.accepter(token, requete);
    }
}
