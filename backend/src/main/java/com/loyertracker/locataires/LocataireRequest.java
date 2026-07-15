package com.loyertracker.locataires;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocataireRequest(
        @NotBlank @Size(max = 255) String nom,
        @Size(max = 255) String prenom,
        @Size(max = 50) String telephone,
        @Email @Size(max = 320) String email,
        @Size(max = 255) String profession,
        LocalDate dateNaissance,
        @Size(max = 50) String typePieceIdentite,
        @Size(max = 100) String numeroPieceIdentite,
        String photoBase64,
        @Size(max = 255) String contactUrgence,
        @Size(max = 2000) String observations) {
}
