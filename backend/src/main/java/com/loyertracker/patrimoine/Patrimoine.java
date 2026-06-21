package com.loyertracker.patrimoine;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Regroupement de biens d'un bailleur, isolé par RLS sur {@code bailleur_id} (D-PAT-001/ADR-11). */
@Entity
@Table(name = "patrimoine")
public class Patrimoine {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPatrimoine statut;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    protected Patrimoine() {
        // requis par JPA
    }

    public Patrimoine(UUID id, UUID bailleurId, String nom) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.nom = nom;
        this.statut = StatutPatrimoine.ACTIF;
    }

    public void renommer(String nom) {
        this.nom = nom;
    }

    public void archiver() {
        this.statut = StatutPatrimoine.ARCHIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBailleurId() {
        return bailleurId;
    }

    public String getNom() {
        return nom;
    }

    public StatutPatrimoine getStatut() {
        return statut;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }
}
