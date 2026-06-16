package com.loyertracker.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loyertracker.honoraires.HonoraireService;

/**
 * Déclencheur quotidien des jobs de loyers (US-30/31) et de calcul des honoraires (US-40). En MVP
 * mono-conteneur, un {@code @Scheduled} in-process suffit (cf. ADR DevSecOps) ; le déclenchement
 * manuel reste possible via {@link BatchController} pour l'exploitation et les tests.
 */
@Component
public class EcheancesScheduler {

    private static final Logger log = LoggerFactory.getLogger(EcheancesScheduler.class);

    private final GenerationEcheancesService generation;
    private final HonoraireService honoraires;
    private final BatchMetrics metrics;

    public EcheancesScheduler(GenerationEcheancesService generation, HonoraireService honoraires,
            BatchMetrics metrics) {
        this.generation = generation;
        this.honoraires = honoraires;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${app.batch.echeances.cron:0 30 6 * * *}", zone = "${app.batch.zone:Europe/Paris}")
    public void genererEcheancesQuotidiennes() {
        int crees = generation.genererEcheances();
        int enRetard = generation.marquerEnRetard();
        // Filet de sécurité : le recalcul des honoraires est aussi déclenché à chaque pointage (hook).
        int honorairesCalcules = honoraires.recalculerBatch();
        metrics.markSuccess(BatchMetrics.JOB_LOYERS);
        log.info("Batch loyers : {} échéance(s) créée(s), {} passée(s) EN_RETARD ; {} honoraire(s) calculé(s).",
                crees, enRetard, honorairesCalcules);
    }
}
