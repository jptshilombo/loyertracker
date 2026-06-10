package com.loyertracker.alertes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Alerte de pilotage générée à terme échu par {@code generer_alertes()} (US-50/51, V9), isolée par
 * RLS sur {@code bailleur_id}. Le {@code destinataire_id} porte le bailleur propriétaire ; l'accès
 * d'un gestionnaire est borné en lecture à ses affectations ACTIVES (US-52).
 */
@Entity
@Table(name = "alerte")
public class Alerte {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "destinataire_id", nullable = false, updatable = false)
    private UUID destinataireId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TypeAlerte type;

    @Column(name = "bien_id", updatable = false)
    private UUID bienId;

    @Column(name = "bail_id", updatable = false)
    private UUID bailId;

    // Colonne CHAR(7) 'YYYY-MM' (V1), nullable : typage JDBC explicite.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7, updatable = false)
    private String periode;

    @Column(nullable = false, updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAlerte statut;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_lecture")
    private OffsetDateTime dateLecture;

    protected Alerte() {
        // requis par JPA
    }

    /** Marque l'alerte comme lue (idempotent) — EF-64. */
    public void marquerLue() {
        if (this.statut != StatutAlerte.LUE) {
            this.statut = StatutAlerte.LUE;
            this.dateLecture = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getDestinataireId() { return destinataireId; }
    public TypeAlerte getType() { return type; }
    public UUID getBienId() { return bienId; }
    public UUID getBailId() { return bailId; }
    public String getPeriode() { return periode; }
    public String getMessage() { return message; }
    public StatutAlerte getStatut() { return statut; }
    public OffsetDateTime getDateCreation() { return dateCreation; }
    public OffsetDateTime getDateLecture() { return dateLecture; }
}
