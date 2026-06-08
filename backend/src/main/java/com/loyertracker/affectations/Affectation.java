package com.loyertracker.affectations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "affectation")
public class Affectation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "bien_id", nullable = false, updatable = false)
    private UUID bienId;

    @Column(name = "gestionnaire_id", nullable = false, updatable = false)
    private UUID gestionnaireId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_honoraires", nullable = false)
    private TypeHonoraires typeHonoraires;

    @Column(name = "montant_honoraires", nullable = false)
    private BigDecimal montantHonoraires;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAffectation statut;

    @Column(name = "date_revocation")
    private OffsetDateTime dateRevocation;

    protected Affectation() {
        // requis par JPA
    }

    public Affectation(UUID id, UUID bailleurId, UUID bienId, UUID gestionnaireId,
            TypeHonoraires typeHonoraires, BigDecimal montantHonoraires, LocalDate dateDebut,
            LocalDate dateFin) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.bienId = bienId;
        this.gestionnaireId = gestionnaireId;
        this.typeHonoraires = typeHonoraires;
        this.montantHonoraires = montantHonoraires;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.statut = StatutAffectation.ACTIVE;
    }

    public void revoquer() {
        this.statut = StatutAffectation.REVOQUEE;
        this.dateRevocation = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getBienId() { return bienId; }
    public UUID getGestionnaireId() { return gestionnaireId; }
    public TypeHonoraires getTypeHonoraires() { return typeHonoraires; }
    public BigDecimal getMontantHonoraires() { return montantHonoraires; }
    public LocalDate getDateDebut() { return dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public StatutAffectation getStatut() { return statut; }
    public OffsetDateTime getDateRevocation() { return dateRevocation; }
}
