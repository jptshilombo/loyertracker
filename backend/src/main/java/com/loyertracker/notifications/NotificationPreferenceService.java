package com.loyertracker.notifications;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Préférences, coordonnées et consentement des destinataires de notification (US-119). Aucune
 * interface HTTP en Sprint N — le contrôleur/UI de préférences et d'historique est un livrable du
 * Sprint N+2 (US-125). Le tenant RLS est supposé déjà positionné par l'appelant (patron déjà
 * pratiqué par {@link com.loyertracker.audit.AuditService}, qui reçoit lui aussi un
 * {@code bailleurId} déjà résolu).
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferences;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    /** Crée ou remplace intégralement la préférence d'un destinataire (recueil du consentement, K3). */
    @Transactional
    public NotificationPreference definir(UUID bailleurId, TypeDestinataire recipientType,
            UUID recipientId, String phoneE164, CanalNotification preferredChannel,
            CanalNotification fallbackChannel, boolean whatsappOptIn, boolean smsOptIn,
            String consentSource, String language) {
        NotificationPreference preference = preferences
                .findByBailleurIdAndRecipientTypeAndRecipientId(bailleurId, recipientType, recipientId)
                .orElseGet(() -> preferences
                        .save(new NotificationPreference(bailleurId, recipientType, recipientId)));
        preference.definir(phoneE164, preferredChannel, fallbackChannel, whatsappOptIn, smsOptIn,
                consentSource, language);
        return preference;
    }

    /** Désinscription immédiate (US-119) : plus aucun envoi externe tenté pour ce destinataire. */
    @Transactional
    public void desinscrire(UUID bailleurId, TypeDestinataire recipientType, UUID recipientId) {
        exigerPreference(bailleurId, recipientType, recipientId).desinscrire();
    }

    @Transactional
    public void reactiver(UUID bailleurId, TypeDestinataire recipientType, UUID recipientId) {
        exigerPreference(bailleurId, recipientType, recipientId).reactiver();
    }

    @Transactional(readOnly = true)
    public Optional<NotificationPreference> trouver(UUID bailleurId, TypeDestinataire recipientType,
            UUID recipientId) {
        return preferences.findByBailleurIdAndRecipientTypeAndRecipientId(bailleurId, recipientType,
                recipientId);
    }

    private NotificationPreference exigerPreference(UUID bailleurId, TypeDestinataire recipientType,
            UUID recipientId) {
        return preferences.findByBailleurIdAndRecipientTypeAndRecipientId(bailleurId, recipientType,
                recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Préférence de notification introuvable."));
    }
}
