package com.loyertracker.baux;

import java.time.LocalDate;

/** Requête de clôture d'un bail (US-115, ADR-17 K1) : date optionnelle, défaut aujourd'hui. */
public record ClotureRequest(LocalDate dateClotureEffective) {
}
