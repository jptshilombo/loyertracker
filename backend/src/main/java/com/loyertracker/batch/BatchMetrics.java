package com.loyertracker.batch;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;

/**
 * Expose, par job planifié, l'horodatage (epoch secondes) de la dernière exécution réussie sous la
 * métrique Prometheus {@code loyertracker_batch_last_success_epoch{job="..."}}. Permet d'alerter sur
 * un job planifié bloqué (OBS-02/03) : {@code time() - loyertracker_batch_last_success_epoch > seuil}.
 *
 * <p>Les jauges sont pré-enregistrées au démarrage avec l'instant courant : un déploiement frais ne
 * déclenche donc pas d'alerte « job non exécuté » tant que le seuil de fraîcheur (24 h + marge) n'est
 * pas dépassé, le temps que le premier passage planifié du jour ait lieu.
 */
@Component
public class BatchMetrics {

    /** Identifiants de job (= label {@code job} de la métrique). */
    public static final String JOB_LOYERS = "loyers";
    public static final String JOB_ALERTES = "alertes";

    private static final String METRIC = "loyertracker.batch.last.success.epoch";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public BatchMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void registerKnownJobs() {
        gauge(JOB_LOYERS);
        gauge(JOB_ALERTES);
    }

    /** Enregistre l'instant courant comme dernière exécution réussie du {@code job}. */
    public void markSuccess(String job) {
        gauge(job).set(Instant.now().getEpochSecond());
    }

    private AtomicLong gauge(String job) {
        return lastSuccess.computeIfAbsent(job, j -> {
            AtomicLong holder = new AtomicLong(Instant.now().getEpochSecond());
            registry.gauge(METRIC, Tags.of("job", j), holder);
            return holder;
        });
    }
}
