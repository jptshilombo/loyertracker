package com.loyertracker.comptes;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Invitation d'un gestionnaire par un bailleur (US-11/US-12, EF-02/03).
 *
 * <p>Capacité tokenisée à usage unique, valable 72 h : un bailleur génère le lien, le destinataire
 * l'accepte pour créer/rattacher son compte gestionnaire. Le {@code token} (UUID v4) porte à lui
 * seul l'autorisation d'acceptation ; l'unicité et l'isolation tenant sont garanties en base
 * (contrainte {@code UNIQUE(token)} + RLS sur {@code bailleur_id}, ADR-01).</p>
 */
@Entity
@Table(name = "invitation")
public class Invitation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true, updatable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutInvitation statut;

    @Column(name = "date_expiration", nullable = false, updatable = false)
    private OffsetDateTime dateExpiration;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    protected Invitation() {
        // requis par JPA
    }

    public Invitation(UUID id, UUID bailleurId, String email, String token,
            OffsetDateTime dateExpiration) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.email = email;
        this.token = token;
        this.dateExpiration = dateExpiration;
        this.statut = StatutInvitation.PENDING;
    }

    /** Vraie si la fenêtre de validité est dépassée à l'instant de référence (EF-03). */
    public boolean estExpiree(OffsetDateTime reference) {
        return reference.isAfter(dateExpiration);
    }

    /** Vraie si l'invitation n'est plus consommable (déjà acceptée ou périmée). */
    public boolean estConsommee() {
        return statut != StatutInvitation.PENDING;
    }

    /** Transition vers {@link StatutInvitation#ACCEPTED} (usage unique — US-12). */
    public void marquerAcceptee() {
        this.statut = StatutInvitation.ACCEPTED;
    }

    /** Transition vers {@link StatutInvitation#EXPIRED} (péremption — US-12/batch). */
    public void marquerExpiree() {
        this.statut = StatutInvitation.EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBailleurId() {
        return bailleurId;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public StatutInvitation getStatut() {
        return statut;
    }

    public OffsetDateTime getDateExpiration() {
        return dateExpiration;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }
}
