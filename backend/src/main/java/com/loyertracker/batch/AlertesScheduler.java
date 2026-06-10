package com.loyertracker.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loyertracker.alertes.AlerteService;

/**
 * Déclencheur quotidien (07:00) de la génération des alertes de pilotage (US-50/51). Idempotent et
 * multi-bailleur ; le déclenchement manuel reste possible via {@link BatchController}. Programmé
 * après le job des loyers (06:30) afin que les bascules EN_RETARD du jour soient prises en compte.
 */
@Component
public class AlertesScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertesScheduler.class);

    private final AlerteService alertes;

    public AlertesScheduler(AlerteService alertes) {
        this.alertes = alertes;
    }

    @Scheduled(cron = "${app.batch.alertes.cron:0 0 7 * * *}", zone = "${app.batch.zone:Europe/Paris}")
    public void genererAlertesQuotidiennes() {
        int crees = alertes.genererBatch();
        log.info("Batch alertes de pilotage : {} alerte(s) créée(s).", crees);
    }
}
