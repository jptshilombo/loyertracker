package com.loyertracker.biens;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bien locatif rattaché à un bailleur, isolé par RLS sur {@code bailleur_id}. */
@Entity
@Table(name = "bien")
public class Bien {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(nullable = false)
    private String adresse;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutBien statut;

    protected Bien() {
        // requis par JPA
    }

    public Bien(UUID id, UUID bailleurId, String adresse, String type, StatutBien statut) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.adresse = adresse;
        this.type = type;
        this.statut = statut;
    }

    public void modifier(String adresse, String type, StatutBien statut) {
        this.adresse = adresse;
        this.type = type;
        this.statut = statut;
    }

    public void archiver() {
        this.statut = StatutBien.ARCHIVE;
    }

    public void louer() {
        this.statut = StatutBien.LOUE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBailleurId() {
        return bailleurId;
    }

    public String getAdresse() {
        return adresse;
    }

    public String getType() {
        return type;
    }

    public StatutBien getStatut() {
        return statut;
    }
}
