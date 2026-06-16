package com.loyertracker.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Vérifie l'instrumentation des jobs planifiés (OBS-03) : la jauge
 * {@code loyertracker.batch.last.success.epoch} est pré-enregistrée par job et mise à jour à chaque
 * succès. C'est le signal exploité par l'alerte Prometheus {@code BatchJobStale}.
 */
class BatchMetricsTest {

    @Test
    void preEnregistreLesJobsConnusAvecUnHorodatageNonNul() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        long avant = Instant.now().getEpochSecond();

        BatchMetrics metrics = new BatchMetrics(registry);
        metrics.registerKnownJobs();

        assertThat(gauge(registry, BatchMetrics.JOB_LOYERS)).isGreaterThanOrEqualTo((double) avant);
        assertThat(gauge(registry, BatchMetrics.JOB_ALERTES)).isGreaterThanOrEqualTo((double) avant);
    }

    @Test
    void markSuccessAvanceLHorodatageDuJob() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BatchMetrics metrics = new BatchMetrics(registry);
        metrics.registerKnownJobs();

        long apres = Instant.now().getEpochSecond();
        metrics.markSuccess(BatchMetrics.JOB_LOYERS);

        assertThat(gauge(registry, BatchMetrics.JOB_LOYERS)).isGreaterThanOrEqualTo((double) apres);
    }

    private static double gauge(SimpleMeterRegistry registry, String job) {
        Gauge g = registry.find("loyertracker.batch.last.success.epoch").tag("job", job).gauge();
        assertThat(g).as("jauge pour le job %s", job).isNotNull();
        return g.value();
    }
}
