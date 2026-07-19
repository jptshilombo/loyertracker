package com.loyertracker.notifications;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Préférences, coordonnées et consentement d'un destinataire de notification externe (US-119,
 * ADR-18 §Consentement). Recueil du consentement tranché par le PO le 2026-07-19 (K3) : formulaire
 * natif LoyerTracker, opt-in explicite — la seule présence d'un numéro n'est jamais un consentement.
 * Isolée par RLS sur {@code bailleur_id} même quand le destinataire réel est un gestionnaire ou un
 * locataire de ce bailleur (patron polymorphe {@code Alerte.destinataireId}/{@code AuditLog.acteurId}).
 */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, updatable = false, length = 20)
    private TypeDestinataire recipientType;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(name = "phone_e164")
    private String phoneE164;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private CanalNotification preferredChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_channel", length = 20)
    private CanalNotification fallbackChannel;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;

    @Column(name = "sms_opt_in", nullable = false)
    private boolean smsOptIn;

    @Column(name = "consent_at")
    private OffsetDateTime consentAt;

    @Column(name = "consent_source")
    private String consentSource;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_desactivation")
    private OffsetDateTime dateDesactivation;

    protected NotificationPreference() {
        // requis par JPA
    }

    public NotificationPreference(UUID bailleurId, TypeDestinataire recipientType, UUID recipientId) {
        this.id = UUID.randomUUID();
        this.bailleurId = bailleurId;
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.preferredChannel = CanalNotification.IN_APP;
        this.language = "fr";
        this.enabled = true;
    }

    /**
     * Recueil du consentement (K3, formulaire LoyerTracker) : (re)définit coordonnées, canaux et
     * opt-in en un seul appel, horodaté. Remplacement complet des champs mutables (même patron PUT
     * que {@code Locataire.modifier}).
     */
    public void definir(String phoneE164, CanalNotification preferredChannel,
            CanalNotification fallbackChannel, boolean whatsappOptIn, boolean smsOptIn,
            String consentSource, String language) {
        if (fallbackChannel != null && fallbackChannel != CanalNotification.SMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seul SMS peut être un canal de secours (K5).");
        }
        this.phoneE164 = phoneE164;
        this.preferredChannel = preferredChannel;
        this.fallbackChannel = fallbackChannel;
        this.whatsappOptIn = whatsappOptIn;
        this.smsOptIn = smsOptIn;
        this.consentSource = consentSource;
        this.language = language != null ? language : "fr";
        this.consentAt = OffsetDateTime.now();
    }

    /** Désinscription immédiate (US-119) : aucun envoi externe n'est plus tenté pour ce destinataire. */
    public void desinscrire() {
        if (!this.enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Déjà désinscrit.");
        }
        this.enabled = false;
        this.dateDesactivation = OffsetDateTime.now();
    }

    public void reactiver() {
        if (this.enabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Déjà actif.");
        }
        this.enabled = true;
        this.dateDesactivation = null;
    }

    /** Éligibilité d'un envoi externe (ADR-18 §Consentement) : actif et opt-in pour ce canal précis. */
    public boolean estEligiblePour(CanalNotification canal) {
        if (!enabled || canal == CanalNotification.IN_APP) {
            return false;
        }
        return switch (canal) {
            case WHATSAPP -> whatsappOptIn;
            case SMS -> smsOptIn;
            case IN_APP -> false;
        };
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public TypeDestinataire getRecipientType() { return recipientType; }
    public UUID getRecipientId() { return recipientId; }
    public String getPhoneE164() { return phoneE164; }
    public CanalNotification getPreferredChannel() { return preferredChannel; }
    public CanalNotification getFallbackChannel() { return fallbackChannel; }
    public boolean isWhatsappOptIn() { return whatsappOptIn; }
    public boolean isSmsOptIn() { return smsOptIn; }
    public OffsetDateTime getConsentAt() { return consentAt; }
    public String getConsentSource() { return consentSource; }
    public String getLanguage() { return language; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getDateCreation() { return dateCreation; }
    public OffsetDateTime getDateDesactivation() { return dateDesactivation; }
}
