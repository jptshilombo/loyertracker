package com.loyertracker.quittances;

import java.util.UUID;

/**
 * Résolution du thème de rendu d'une quittance pour un bailleur (ADR-15 D6). L'implémentation
 * actuelle renvoie le thème LoyerTracker pour tous ; une personnalisation par bailleur (besoin
 * EP-14 §10) se branchera ici sans modifier le moteur PDF.
 */
public interface ThemeQuittanceProvider {

    ThemeQuittance themePour(UUID bailleurId);
}
