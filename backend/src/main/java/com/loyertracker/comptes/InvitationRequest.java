package com.loyertracker.comptes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Corps de la demande d'invitation (US-11) : l'e-mail du gestionnaire à inviter. Le bailleur
 * émetteur n'est jamais fourni par le client — il provient du JWT (cf. {@code InvitationController}).
 */
public record InvitationRequest(
        @NotBlank @Email String email) {
}
