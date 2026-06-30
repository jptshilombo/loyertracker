package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bail")
public class Bail {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "bien_id", nullable = false, updatable = false)
    private UUID bienId;

    @Column(name = "locataire_nom", nullable = false)
    private String locataireNom;

    @Column(name = "locataire_email")
    private String locataireEmail;

    @Column(name = "loyer_hc", nullable = false)
    private BigDecimal loyerHc;

    @Column(name = "provision_charges", nullable = false)
    private BigDecimal provisionCharges;

    /** Loyer charges comprises = loyer_hc + provision_charges (cohérence imposée en base, V11). */
    @Column(name = "loyer_cc", nullable = false)
    private BigDecimal loyerCc;

    @Column(name = "depot_garantie", nullable = false)
    private BigDecimal depotGarantie;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutBail statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Devise devise;

    protected Bail() {
        // requis par JPA
    }

    public Bail(UUID id, UUID bailleurId, UUID bienId, String locataireNom, String locataireEmail,
            BigDecimal loyerHc, BigDecimal provisionCharges, BigDecimal depotGarantie,
            LocalDate dateDebut, LocalDate dateFin, Devise devise) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.bienId = bienId;
        this.locataireNom = locataireNom;
        this.locataireEmail = locataireEmail;
        this.loyerHc = loyerHc;
        this.provisionCharges = provisionCharges;
        // Source de vérité unique : le « charges comprises » est dérivé, jamais saisi (cohérence V11).
        this.loyerCc = loyerHc.add(provisionCharges);
        this.depotGarantie = depotGarantie;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = StatutBail.ACTIF;
        this.devise = devise;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getBienId() { return bienId; }
    public String getLocataireNom() { return locataireNom; }
    public String getLocataireEmail() { return locataireEmail; }
    public BigDecimal getLoyerHc() { return loyerHc; }
    public BigDecimal getProvisionCharges() { return provisionCharges; }
    public BigDecimal getLoyerCc() { return loyerCc; }
    public BigDecimal getDepotGarantie() { return depotGarantie; }
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public StatutBail getStatut() { return statut; }
    public Devise getDevise() { return devise; }
}
