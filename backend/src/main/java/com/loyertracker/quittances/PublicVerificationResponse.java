package com.loyertracker.quittances;

/**
 * Réponse publique de vérification (ADR-15 D5, US-102). Volontairement <strong>indifférenciée</strong>
 * en cas d'échec : quelle que soit la cause (identifiant inconnu, token forgé/tronqué, token d'une
 * autre quittance, version décalée), la réponse est la même — {@code resultat = "INVALIDE"},
 * {@code quittance = null}, même statut HTTP 200 — pour ne fournir aucun oracle à un attaquant.
 *
 * @param resultat  {@code "VALIDE"} ou {@code "INVALIDE"}
 * @param quittance projection publique K2 si {@code VALIDE}, sinon {@code null}
 */
public record PublicVerificationResponse(String resultat, PublicReceiptDto quittance) {

    static PublicVerificationResponse invalide() {
        return new PublicVerificationResponse("INVALIDE", null);
    }

    static PublicVerificationResponse valide(PublicReceiptDto quittance) {
        return new PublicVerificationResponse("VALIDE", quittance);
    }
}
