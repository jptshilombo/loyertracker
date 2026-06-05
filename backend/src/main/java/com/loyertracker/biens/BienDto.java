package com.loyertracker.biens;

import java.util.UUID;

/**
 * Vue minimale d'un bien (squelette — étape 04). Enrichie avec la persistance à l'étape 06.
 */
public record BienDto(UUID id, String adresse, String type, String statut) {
}
