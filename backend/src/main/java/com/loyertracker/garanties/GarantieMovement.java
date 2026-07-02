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

/**
 * Écriture append-only du ledger de garantie (ADR-14/D-GAR-001) : chaque mouvement fige un
 * débit/crédit et le solde résultant. Jamais mutée après création — toute correction passe par un
 * nouveau mouvement (p. ex. {@code ANNULATION}), jamais par une mise à jour de ligne existante.
 */
@Entity
@Table(name = "garantie_movement")
public class GarantieMovement {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "garantie_id", nullable = false, updatable = false)
    private UUID garantieId;

    @Column(name = "date_mouvement", nullable = false, updatable = false)
    private LocalDate dateMouvement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TypeMouvementGarantie type;

    @Column(nullable = false, updatable = false)
    private BigDecimal debit;

    @Column(nullable = false, updatable = false)
    private BigDecimal credit;

    @Column(name = "solde_apres", nullable = false, updatable = false)
    private BigDecimal soldeApres;

    @Column(updatable = false)
    private String motif;

    @Column(nullable = false, updatable = false)
    private String utilisateur;

    @Column(updatable = false)
    private String commentaire;

    @Column(name = "reference_document", updatable = false)
    private String referenceDocument;

    protected GarantieMovement() {
        // requis par JPA
    }

    /** Regroupe l'impact financier d'un mouvement (ADR-14 §1) : débit, crédit et solde résultant. */
    public record MouvementMontants(BigDecimal debit, BigDecimal credit, BigDecimal soldeApres) {
    }

    public GarantieMovement(UUID bailleurId, UUID garantieId, TypeMouvementGarantie type,
            MouvementMontants montants, String motif, String utilisateur) {
        this.id = UUID.randomUUID();
        this.dateMouvement = LocalDate.now();
        this.bailleurId = bailleurId;
        this.garantieId = garantieId;
        this.type = type;
        this.debit = montants.debit();
        this.credit = montants.credit();
        this.soldeApres = montants.soldeApres();
        this.motif = motif;
        this.utilisateur = utilisateur;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getGarantieId() { return garantieId; }
    public LocalDate getDateMouvement() { return dateMouvement; }
    public TypeMouvementGarantie getType() { return type; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
    public BigDecimal getSoldeApres() { return soldeApres; }
    public String getMotif() { return motif; }
    public String getUtilisateur() { return utilisateur; }
    public String getCommentaire() { return commentaire; }
    public String getReferenceDocument() { return referenceDocument; }
}
