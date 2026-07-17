package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    @Column(name = "locataire_id", nullable = false, updatable = false)
    private UUID locataireId;

    @Column(name = "loyer_hc", nullable = false)
    private BigDecimal loyerHc;

    @Column(name = "provision_charges", nullable = false)
    private BigDecimal provisionCharges;

    /** Loyer charges comprises = loyer_hc + provision_charges (cohérence imposée en base, V11). */
    @Column(name = "loyer_cc", nullable = false)
    private BigDecimal loyerCc;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "date_cloture_effective")
    private LocalDate dateClotureEffective;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutBail statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Devise devise;

    protected Bail() {
        // requis par JPA
    }

    // S107 : entité de domaine, chaque champ constitutif du bail à la création — même pattern
    // que Quittance (instantané immuable), aucun builder ou objet intermédiaire n'apporterait de
    // garantie supplémentaire ici.
    @SuppressWarnings("java:S107")
    public Bail(UUID id, UUID bailleurId, UUID bienId, UUID locataireId,
            BigDecimal loyerHc, BigDecimal provisionCharges,
            LocalDate dateDebut, LocalDate dateFin, Devise devise) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.bienId = bienId;
        this.locataireId = locataireId;
        this.loyerHc = loyerHc;
        this.provisionCharges = provisionCharges;
        // Source de vérité unique : le « charges comprises » est dérivé, jamais saisi (cohérence V11).
        this.loyerCc = loyerHc.add(provisionCharges);
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = StatutBail.ACTIF;
        this.devise = devise;
    }

    /** Clôture manuelle (ACTIF -> CLOS, US-115, ADR-17 K1/K2). dateFin (contractuelle) inchangée. */
    public void cloturer(LocalDate dateClotureEffective) {
        if (this.statut == StatutBail.CLOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce bail est déjà clos.");
        }
        this.statut = StatutBail.CLOS;
        this.dateClotureEffective = dateClotureEffective;
    }

    /** Réouverture d'un bail clos par erreur (US-116, ADR-17 K5). Le contrôle uq_bail_actif est
     *  fait par le service (BailService.rouvrir), pas ici — même séparation que BailService.creer. */
    public void rouvrir() {
        if (this.statut != StatutBail.CLOS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un bail clos peut être rouvert.");
        }
        this.statut = StatutBail.ACTIF;
        this.dateClotureEffective = null;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getBienId() { return bienId; }
    public UUID getLocataireId() { return locataireId; }
    public BigDecimal getLoyerHc() { return loyerHc; }
    public BigDecimal getProvisionCharges() { return provisionCharges; }
    public BigDecimal getLoyerCc() { return loyerCc; }
    // getDepotGarantie() retiré en V20 (ADR-14 §8) : le dépôt de garantie n'est plus stocké sur
    // `bail`, il est dérivé du ledger de garantie (voir BailDto.from(Bail, BigDecimal, Locataire)).
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public LocalDate getDateClotureEffective() { return dateClotureEffective; }
    public StatutBail getStatut() { return statut; }
    public Devise getDevise() { return devise; }
}
