package com.loyertracker.paiements;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Loyer mensuel attendu pour un couple {@code (bien, periode)}, isolé par RLS sur
 * {@code bailleur_id}. Les lignes sont générées à terme échu par le batch (US-30, V6) puis
 * pointées par un acteur autorisé (US-31).
 */
@Entity
@Table(name = "paiement")
public class Paiement {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "bail_id", nullable = false, updatable = false)
    private UUID bailId;

    @Column(name = "bien_id", nullable = false, updatable = false)
    private UUID bienId;

    // Colonne CHAR(7) 'YYYY-MM' (V1) : typage JDBC explicite, sinon Hibernate attend un varchar.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 7)
    private String periode;

    @Column(name = "montant_attendu", nullable = false, updatable = false)
    private BigDecimal montantAttendu;

    @Column(name = "montant_recu", nullable = false)
    private BigDecimal montantRecu;

    @Column(name = "date_exigibilite", nullable = false, updatable = false)
    private LocalDate dateExigibilite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPaiement statut;

    protected Paiement() {
        // requis par JPA
    }

    /** Enregistre un pointage (EF-30/32) : montant reçu et statut décidé par l'acteur. */
    public void pointer(BigDecimal montantRecu, StatutPaiement statut) {
        this.montantRecu = montantRecu;
        this.statut = statut;
    }

    /** Reste dû = max(0, attendu - reçu) (EF-32). */
    public BigDecimal getResteDu() {
        BigDecimal reste = montantAttendu.subtract(montantRecu);
        return reste.signum() < 0 ? BigDecimal.ZERO : reste;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getBailId() { return bailId; }
    public UUID getBienId() { return bienId; }
    public String getPeriode() { return periode; }
    public BigDecimal getMontantAttendu() { return montantAttendu; }
    public BigDecimal getMontantRecu() { return montantRecu; }
    public LocalDate getDateExigibilite() { return dateExigibilite; }
    public StatutPaiement getStatut() { return statut; }
}
