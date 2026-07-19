package com.loyertracker.notifications.provider;

import java.util.Map;

import com.loyertracker.notifications.CanalNotification;

/**
 * Abstraction du transport externe (WhatsApp/SMS, US-121, ADR-18 §5). Le domaine métier
 * (paiements, garanties, alertes, quittances) ne connaît jamais le SDK Twilio, les credentials, les
 * numéros expéditeurs ni les Content SID — uniquement cette interface. Permet de remplacer Twilio,
 * ou d'ajouter un second fournisseur, sans réécrire les règles métier.
 */
public interface NotificationProvider {

    /** Tente l'envoi d'une notification externe et retourne le résultat immédiat du fournisseur. */
    ResultatEnvoi envoyer(DemandeEnvoi demande);

    /** Requête d'envoi minimale — jamais de texte codé en dur, toujours un template résolu en amont. */
    record DemandeEnvoi(String phoneE164, CanalNotification canal, String templateCode,
            Map<String, String> variables) {
    }

    /** Résultat immédiat (avant callback asynchrone de statut, cf. Sprint N+1 US-123). */
    record ResultatEnvoi(boolean accepte, String providerMessageId, String errorCode) {
    }
}
