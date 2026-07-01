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

    @Column(nullable = false)
    private String adresse;

    @Column
    private String ville;

    @Column
    private String commune;

    @Column
    private String quartier;

    @Column(name = "province_etat")
    private String provinceEtat;

    @Column
    private String pays;

    @Column
    private String description;

    @Column(name = "reference_interne")
    private String referenceInterne;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutPatrimoine statut;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    protected Patrimoine() {
        // requis par JPA
    }

    public Patrimoine(UUID id, UUID bailleurId, String nom, String adresse) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.nom = nom;
        this.adresse = adresse;
        this.statut = StatutPatrimoine.ACTIF;
    }

    public void modifier(PatrimoineRequest requete) {
        this.nom = requete.nom();
        this.adresse = requete.adresse();
        this.ville = requete.ville();
        this.commune = requete.commune();
        this.quartier = requete.quartier();
        this.provinceEtat = requete.provinceEtat();
        this.pays = requete.pays();
        this.description = requete.description();
        this.referenceInterne = requete.referenceInterne();
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

    public String getAdresse() {
        return adresse;
    }

    public String getVille() {
        return ville;
    }

    public String getCommune() {
        return commune;
    }

    public String getQuartier() {
        return quartier;
    }

    public String getProvinceEtat() {
        return provinceEtat;
    }

    public String getPays() {
        return pays;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceInterne() {
        return referenceInterne;
    }

    public StatutPatrimoine getStatut() {
        return statut;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }
}
