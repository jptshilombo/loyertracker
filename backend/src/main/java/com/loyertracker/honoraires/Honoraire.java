package com.loyertracker.honoraires;

import java.math.BigDecimal;
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
 * Honoraire de gestion dû pour un couple {@code (affectation, periode)}, isolé par RLS sur
 * {@code bailleur_id}. Le montant est (re)calculé à terme échu par la fonction SQL
 * {@code calculer_honoraires()} (US-40, V8) — synchrone au pointage d'un loyer ou via le batch —
 * puis figé lorsque le bailleur le valide (statut {@code PAYE}, EF-52).
 */
@Entity
@Table(name = "honoraire")
public class Honoraire {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "affectation_id", nullable = false, updatable = false)
    private UUID affectationId;

    // Colonne CHAR(7) 'YYYY-MM' (V1) : typage JDBC explicite, sinon Hibernate attend un varchar.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, updatable = false, length = 7)
    private String periode;

    @Column(nullable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutHonoraire statut;

    protected Honoraire() {
        // requis par JPA
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getAffectationId() { return affectationId; }
    public String getPeriode() { return periode; }
    public BigDecimal getMontant() { return montant; }
    public StatutHonoraire getStatut() { return statut; }

    /** Applique une transition de statut décidée par le bailleur (EF-52). */
    public void changerStatut(StatutHonoraire nouveau) {
        this.statut = nouveau;
    }
}
