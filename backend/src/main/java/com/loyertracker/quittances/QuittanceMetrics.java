package com.loyertracker.quittances;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

/**
 * Compteurs Prometheus de la vérification publique des quittances (US-104, ADR-15 D7). Exposés à
 * {@code /api/actuator/prometheus} (scrape interne uniquement) :
 *
 * <ul>
 *   <li>{@code quittance_verifications_total{resultat="valide|invalide"}} — tentatives de
 *       vérification par résultat ;</li>
 *   <li>{@code quittance_telechargements_total} — téléchargements servis (PDF re-vérifié) ;</li>
 *   <li>{@code quittance_qr_invalides_total} — scans invalides (agrégat anti-fraude, toutes causes
 *       confondues, cohérent avec l'absence d'oracle côté réponse).</li>
 * </ul>
 *
 * <p>Aucune donnée personnelle ni identifiant de quittance n'est porté par ces métriques (RGPD,
 * ADR-15 D7) — la corrélation par quittance vit dans {@code quittance_verification_log} et les
 * compteurs par quittance ({@code nb_verifications}/{@code nb_telechargements}).</p>
 */
@Component
public class QuittanceMetrics {

    private final Counter verificationsValides;
    private final Counter verificationsInvalides;
    private final Counter telechargements;
    private final Counter qrInvalides;

    public QuittanceMetrics(MeterRegistry registry) {
        this.verificationsValides = Counter.builder("quittance.verifications")
                .tag("resultat", "valide").register(registry);
        this.verificationsInvalides = Counter.builder("quittance.verifications")
                .tag("resultat", "invalide").register(registry);
        this.telechargements = Counter.builder("quittance.telechargements").register(registry);
        this.qrInvalides = Counter.builder("quittance.qr.invalides").register(registry);
    }

    // Force la matérialisation des séries à 0 dès le démarrage (sinon une métrique jamais
    // incrémentée n'apparaît pas au scrape — mêmes attentes que BatchMetrics).
    @PostConstruct
    void amorcer() {
        verificationsValides.increment(0);
        verificationsInvalides.increment(0);
        telechargements.increment(0);
        qrInvalides.increment(0);
    }

    /** Enregistre une tentative de vérification et, si invalide, l'incrément anti-fraude. */
    public void verification(boolean valide) {
        if (valide) {
            verificationsValides.increment();
        } else {
            verificationsInvalides.increment();
            qrInvalides.increment();
        }
    }

    /** Enregistre un téléchargement servi (valide) ou une tentative invalide (anti-fraude). */
    public void telechargement(boolean valide) {
        if (valide) {
            telechargements.increment();
        } else {
            qrInvalides.increment();
        }
    }
}
