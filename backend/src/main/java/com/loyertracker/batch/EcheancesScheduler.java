package com.loyertracker.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclencheur quotidien de la génération des échéances de loyers (US-30). En MVP mono-conteneur,
 * un {@code @Scheduled} in-process suffit (cf. ADR DevSecOps) ; le déclenchement manuel reste
 * possible via {@link BatchController} pour l'exploitation et les tests.
 */
@Component
public class EcheancesScheduler {

    private static final Logger log = LoggerFactory.getLogger(EcheancesScheduler.class);

    private final GenerationEcheancesService generation;

    public EcheancesScheduler(GenerationEcheancesService generation) {
        this.generation = generation;
    }

    @Scheduled(cron = "${app.batch.echeances.cron:0 30 6 * * *}", zone = "${app.batch.zone:Europe/Paris}")
    public void genererEcheancesQuotidiennes() {
        int crees = generation.genererEcheances();
        int enRetard = generation.marquerEnRetard();
        log.info("Batch échéances loyers : {} échéance(s) créée(s), {} loyer(s) passé(s) EN_RETARD.",
                crees, enRetard);
    }
}
