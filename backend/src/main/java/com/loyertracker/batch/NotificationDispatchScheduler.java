package com.loyertracker.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loyertracker.notifications.NotificationDispatcher;

/**
 * Déclencheur périodique du traitement de l'Outbox de notifications (US-123, EP-16 Sprint N+1).
 * Contrairement aux jobs quotidiens (alertes, échéances), l'Outbox transactionnelle appelle un
 * sondage rapproché (patron « polling worker »), pas un cron journalier. Sans effet tant que
 * l'Outbox reste vide (socle désactivé par défaut, K8) : chaque passage ne fait qu'un aller-retour
 * SQL de découverte ({@code notification_bailleurs_en_attente()}) si rien n'est dû.
 */
@Component
public class NotificationDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

    private final NotificationDispatcher dispatcher;
    private final BatchMetrics metrics;
    private final int limiteParBailleur;

    public NotificationDispatchScheduler(NotificationDispatcher dispatcher, BatchMetrics metrics,
            @Value("${app.notifications.dispatch.limite-par-bailleur:50}") int limiteParBailleur) {
        this.dispatcher = dispatcher;
        this.metrics = metrics;
        this.limiteParBailleur = limiteParBailleur;
    }

    @Scheduled(fixedDelayString = "${app.notifications.dispatch.interval-ms:15000}")
    public void traiterOutbox() {
        int traitees = dispatcher.traiterLot(limiteParBailleur);
        metrics.markSuccess(BatchMetrics.JOB_NOTIFICATIONS);
        if (traitees > 0) {
            log.info("Dispatch notifications : {} ligne(s) d'Outbox traitée(s).", traitees);
        }
    }
}
