package com.loyertracker.notifications;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Crée et met à jour les {@link NotificationDelivery} (US-123, ADR-18 §Ledger). La création
 * ({@link #creer}) se fait en JPA, dans le même contexte tenant que {@link NotificationDispatcher}
 * (bailleur déjà positionné, RLS pleinement appliquée). La mise à jour par callback
 * ({@link #appliquerCallback}) délègue entièrement à la fonction {@code SECURITY DEFINER
 * notification_delivery_appliquer_statut} (V28) : le callback Twilio est un endpoint public non
 * authentifié, sans contexte tenant — même patron que {@code lire_quittance_publique} (V22).
 */
@Service
public class NotificationDeliveryService {

    /** Correspondance statut Twilio (Message Status Callback) → statut interne. */
    private static final Map<String, StatutDelivery> STATUTS_TWILIO = Map.of(
            "queued", StatutDelivery.QUEUED,
            "accepted", StatutDelivery.ACCEPTED,
            "sent", StatutDelivery.SENT,
            "delivered", StatutDelivery.DELIVERED,
            "read", StatutDelivery.READ,
            "failed", StatutDelivery.FAILED,
            "undelivered", StatutDelivery.UNDELIVERED);

    private final NotificationDeliveryRepository deliveries;
    private final EntityManager em;

    public NotificationDeliveryService(NotificationDeliveryRepository deliveries, EntityManager em) {
        this.deliveries = deliveries;
        this.em = em;
    }

    /** Créée après acceptation immédiate par le fournisseur (jamais pour un envoi refusé). */
    @Transactional
    public NotificationDelivery creer(UUID bailleurId, UUID eventId, UUID recipientId,
            CanalNotification channel, String providerMessageId) {
        return deliveries.save(new NotificationDelivery(bailleurId, eventId, recipientId, channel,
                providerMessageId));
    }

    /**
     * Applique un callback de statut Twilio. Un {@code MessageStatus} inconnu (ex. {@code
     * "sending"}, état transitoire non retenu par ce modèle) est ignoré avant même d'atteindre la
     * fonction SQL — ni ce cas ni un SID introuvable ni un callback dupliqué ne sont distingués
     * côté appelant (réponse toujours indifférenciée, {@code TwilioCallbackController}).
     *
     * @return {@code true} si une transition a effectivement été appliquée
     */
    @Transactional
    public boolean appliquerCallback(String providerMessageId, String messageStatus, String errorCode) {
        StatutDelivery nouveauStatut = STATUTS_TWILIO.get(messageStatus == null ? "" : messageStatus.toLowerCase());
        if (nouveauStatut == null) {
            return false;
        }
        String errorCategory = errorCode == null ? null : CategorieErreurNotification.PERMANENT.name();
        return (Boolean) em.createNativeQuery(
                        "SELECT notification_delivery_appliquer_statut(:sid, :statut, :err, :cat)")
                .setParameter("sid", providerMessageId)
                .setParameter("statut", nouveauStatut.name())
                .setParameter("err", errorCode)
                .setParameter("cat", errorCategory)
                .getSingleResult();
    }
}
