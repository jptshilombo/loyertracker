package com.loyertracker.quittances;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Thème par défaut : identité LoyerTracker (logo du projet chargé une fois depuis le classpath,
 * embarqué en data-URI dans chaque PDF).
 */
@Component
public class ThemeLoyerTrackerDefaut implements ThemeQuittanceProvider {

    private final ThemeQuittance theme;

    public ThemeLoyerTrackerDefaut() {
        this.theme = new ThemeQuittance("LoyerTracker", chargerLogo(), "#0f4c81", "#1a1a1a", null);
    }

    @Override
    public ThemeQuittance themePour(UUID bailleurId) {
        return theme;
    }

    private static String chargerLogo() {
        try {
            byte[] png = new ClassPathResource("documents/logo-loyertracker.png")
                    .getContentAsByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Logo LoyerTracker introuvable (documents/logo-loyertracker.png).", e);
        }
    }
}
