package com.loyertracker.bailleur;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de mise à jour du profil bailleur (V11). Limité à l'adresse postale, mention obligatoire de
 * la quittance ; le nom/prénom proviennent de l'identité Keycloak (non modifiables ici).
 */
public record ProfilRequest(@NotBlank @Size(max = 500) String adresse) {
}
