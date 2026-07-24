package com.loyertracker.notifications.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implémentation sandbox par défaut (US-121) : n'effectue strictement aucun appel réseau, toujours
 * un succès simulé sans {@code providerMessageId} réel. Bean actif tant que {@code
 * app.notifications.whatsapp.enabled} (K8) n'est pas explicitement passé à {@code true} — garantit
 * un démarrage sûr sans configuration Twilio, socle désactivé par défaut dans tous les
 * environnements tant qu'une activation explicite n'est pas décidée par le PO (même flag que
 * {@link TwilioNotificationProvider}, en exclusion mutuelle : un seul bean {@link
 * NotificationProvider} candidat à tout instant).
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "whatsapp.enabled",
        havingValue = "false", matchIfMissing = true)
public class NoopNotificationProvider implements NotificationProvider {

    @Override
    public ResultatEnvoi envoyer(DemandeEnvoi demande) {
        return new ResultatEnvoi(true, null, null);
    }
}
