package com.loyertracker.honoraires;

import jakarta.validation.constraints.NotNull;

/** Corps de la transition de statut d'un honoraire (PATCH). */
public record StatutRequest(@NotNull StatutHonoraire statut) {
}
