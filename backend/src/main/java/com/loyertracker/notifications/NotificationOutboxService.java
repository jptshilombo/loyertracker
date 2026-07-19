package com.loyertracker.notifications;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

/**
 * Émission d'un {@link NotificationEvent} et fan-out vers {@link NotificationOutbox} (US-120,
 * ADR-18 §2/§3/§4). Appelée dans la même transaction que l'opération métier — voie A
 * ({@code generer_alertes()}, SQL natif) ou voie B (écriture inline dans les services métier,
 * même patron que {@link com.loyertracker.audit.AuditService#enregistrer}) : jamais d'appel réseau
 * ici, uniquement des écritures.
 *
 * <p>Le fan-out ne crée une ligne Outbox que pour un destinataire dont la {@link NotificationPreference}
 * est active et opt-in pour son canal préféré (jamais {@code IN_APP}, qui ne passe pas par
 * l'Outbox) — l'absence de préférence équivaut à une absence de consentement (K3), jamais à un
 * envoi par défaut.</p>
 */
@Service
public class NotificationOutboxService {

    private final EntityManager em;
    private final NotificationOutboxRepository outbox;
    private final NotificationPreferenceRepository preferences;
    private final ObjectMapper json;

    public NotificationOutboxService(EntityManager em, NotificationOutboxRepository outbox,
            NotificationPreferenceRepository preferences, ObjectMapper json) {
        this.em = em;
        this.outbox = outbox;
        this.preferences = preferences;
        this.json = json;
    }

    /**
     * Persiste l'événement puis fait le fan-out vers l'Outbox pour les destinataires éligibles.
     *
     * @return l'identifiant de l'événement créé (traçabilité, non exposé par une API en Sprint N)
     */
    @Transactional
    public UUID emettre(UUID bailleurId, TypeEvenementNotification type,
            TypeAgregatNotification aggregateType, UUID aggregateId,
            Map<String, Object> payloadMinimal, List<Destinataire> destinataires) {
        UUID eventId = creerEvenement(bailleurId, type, aggregateType, aggregateId, payloadMinimal);
        for (Destinataire destinataire : destinataires) {
            preferences.findByBailleurIdAndRecipientTypeAndRecipientId(bailleurId,
                            destinataire.type(), destinataire.id())
                    .filter(pref -> pref.estEligiblePour(pref.getPreferredChannel()))
                    .ifPresent(pref -> outbox.save(new NotificationOutbox(bailleurId, eventId,
                            destinataire.id(), type, pref.getPreferredChannel())));
        }
        return eventId;
    }

    private UUID creerEvenement(UUID bailleurId, TypeEvenementNotification type,
            TypeAgregatNotification aggregateType, UUID aggregateId, Map<String, Object> payloadMinimal) {
        String payloadJson;
        try {
            payloadJson = json.writeValueAsString(payloadMinimal == null ? Map.of() : payloadMinimal);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload de notification non sérialisable.", e);
        }
        return (UUID) em.createNativeQuery("""
                INSERT INTO notification_event
                    (bailleur_id, event_type, aggregate_type, aggregate_id, payload_minimal)
                VALUES (CAST(:b AS uuid), :type, :aggType, CAST(:aggId AS uuid), CAST(:payload AS jsonb))
                RETURNING id
                """)
                .setParameter("b", bailleurId.toString())
                .setParameter("type", type.name())
                .setParameter("aggType", aggregateType.name())
                .setParameter("aggId", aggregateId.toString())
                .setParameter("payload", payloadJson)
                .getSingleResult();
    }

    /**
     * Réclame par lot les lignes {@code PENDING}/{@code RETRY} dues, verrouillage non bloquant
     * (RSV-EP16-02) : preuve de concurrence pour ce sprint — le traitement effectif (appel
     * {@code NotificationProvider}) reste un livrable du {@code NotificationDispatcher} (Sprint N+1).
     *
     * @return les identifiants réclamés, passés en {@code PROCESSING}
     */
    @Transactional
    public List<UUID> reclamerLot(int limite) {
        List<?> ids = em.createNativeQuery("""
                UPDATE notification_outbox
                SET statut = 'PROCESSING', locked_at = now()
                WHERE id IN (
                    SELECT id FROM notification_outbox
                    WHERE statut IN ('PENDING', 'RETRY') AND next_attempt_at <= now()
                    ORDER BY next_attempt_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limite
                )
                RETURNING id
                """)
                .setParameter("limite", limite)
                .getResultList();
        return ids.stream().map(id -> (UUID) id).toList();
    }
}
