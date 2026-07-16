package com.loyertracker.baux;

import java.util.List;

/**
 * Réponse de clôture (US-115, ADR-17 K3/K4) : la clôture réussit toujours (200), même en présence
 * d'une garantie non intégralement restituée et/ou de paiements en cours — signalé uniquement via
 * {@code avertissements}, jamais un blocage 409.
 */
public record ClotureBailDto(BailDto bail, List<String> avertissements) {
}
