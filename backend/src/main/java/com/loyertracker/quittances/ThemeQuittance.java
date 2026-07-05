package com.loyertracker.quittances;

/**
 * Thème visuel d'une quittance certifiée (ADR-15 D6). La personnalisation future par bailleur
 * (logo, couleurs, signature, tampon — besoin EP-14 §10) ne touchera que le
 * {@link ThemeQuittanceProvider}, jamais le moteur de rendu.
 *
 * @param nomMarque        nom affiché dans l'en-tête et le cachet
 * @param logoDataUri      logo en data-URI (le PDF est autosuffisant), ou {@code null} sans logo
 * @param couleurAccent    couleur des titres et éléments de structure (hex CSS)
 * @param couleurEncre     couleur du texte courant (hex CSS)
 * @param signatureDataUri image de signature/tampon du bailleur, ou {@code null} (défaut actuel)
 */
public record ThemeQuittance(
        String nomMarque,
        String logoDataUri,
        String couleurAccent,
        String couleurEncre,
        String signatureDataUri) {
}
