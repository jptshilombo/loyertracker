package com.loyertracker.notifications.provider;

import org.springframework.stereotype.Component;

/**
 * Implémentation sandbox par défaut (US-121) : n'effectue strictement aucun appel réseau, toujours
 * un succès simulé sans {@code providerMessageId} réel. Seule implémentation disponible tant que
 * {@code TwilioNotificationProvider} n'existe pas (Sprint N+1) — garantit un démarrage sûr sans
 * configuration Twilio (démarrage sans secret Twilio ⇒ aucune erreur bloquante, in-app inchangé).
 */
@Component
public class NoopNotificationProvider implements NotificationProvider {

    @Override
    public ResultatEnvoi envoyer(DemandeEnvoi demande) {
        return new ResultatEnvoi(true, null, null);
    }
}
