package com.loyertracker.comptes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de l'acceptation d'une invitation (US-12) : l'identité que le destinataire choisit pour son
 * compte gestionnaire. L'e-mail n'est PAS fourni ici — il provient de l'invitation (le token est la
 * capacité). Le mot de passe est ignoré si le compte préexiste (réutilisation multi-bailleur).
 */
public record AcceptationRequest(
        @NotBlank String nom,
        @NotBlank String prenom,
        @NotBlank @Size(min = 12, message = "Le mot de passe doit comporter au moins 12 caractères.")
        String motDePasse) {
}
