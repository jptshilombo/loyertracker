package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dépôt de garantie d'un bail, isolé par RLS sur {@code bailleur_id} (EF-40/41/42, Annexe A.5). */
@Entity
@Table(name = "garantie")
public class Garantie {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "bail_id", nullable = false, updatable = false)
    private UUID bailId;

    @Column(nullable = false, updatable = false)
    private BigDecimal montant;

    @Column(name = "type_garantie", nullable = false, updatable = false)
    private String typeGarantie;

    @Column(name = "date_depot", nullable = false, updatable = false)
    private LocalDate dateDepot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutGarantie statut;

    @Column(name = "montant_retenu", nullable = false)
    private BigDecimal montantRetenu;

    @Column(name = "motif_retenue")
    private String motifRetenue;

    protected Garantie() {
        // requis par JPA
    }

    public Garantie(UUID id, UUID bailleurId, UUID bailId, BigDecimal montant, String typeGarantie,
            LocalDate dateDepot) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.bailId = bailId;
        this.montant = montant;
        this.typeGarantie = typeGarantie;
        this.dateDepot = dateDepot;
        this.statut = StatutGarantie.DETENU;
        this.montantRetenu = BigDecimal.ZERO;
    }

    /** Restitution intégrale (A.5) : {@code DETENU} ou {@code RESTITUE_PARTIEL} → {@code RESTITUE_TOTAL}. */
    public void restituerTotal() {
        this.statut = StatutGarantie.RESTITUE_TOTAL;
    }

    /** Restitution partielle avec retenue + motif (EF-42) : {@code DETENU} → {@code RESTITUE_PARTIEL}. */
    public void restituerPartiel(BigDecimal montantRetenu, String motifRetenue) {
        this.statut = StatutGarantie.RESTITUE_PARTIEL;
        this.montantRetenu = montantRetenu;
        this.motifRetenue = motifRetenue;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getBailId() { return bailId; }
    public BigDecimal getMontant() { return montant; }
    public String getTypeGarantie() { return typeGarantie; }
    public LocalDate getDateDepot() { return dateDepot; }
    public StatutGarantie getStatut() { return statut; }
    public BigDecimal getMontantRetenu() { return montantRetenu; }
    public String getMotifRetenue() { return motifRetenue; }
}
