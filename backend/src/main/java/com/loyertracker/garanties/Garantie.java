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

    /**
     * Cache transactionnel du solde (ADR-14/D-GAR-001, §3) : recalculé de façon synchrone à
     * chaque mouvement du ledger {@code garantie_movement}, jamais en asynchrone — condition
     * nécessaire pour que le batch d'alertes {@code GARANTIE_NON_RESTITUEE} (lecture directe de
     * {@code statut}, inchangé) continue de fonctionner sans modification.
     */
    @Column(name = "solde_actuel", nullable = false)
    private BigDecimal soldeActuel;

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
        // Le mouvement DEPOT_INITIAL (ledger) est créé par GarantieService dans la même
        // transaction ; le cache est initialisé ici en cohérence avec ce mouvement.
        this.soldeActuel = montant;
    }

    /** Restitution intégrale (A.5) : {@code DETENU} ou {@code RESTITUE_PARTIEL} → {@code RESTITUE_TOTAL}. */
    public void restituerTotal() {
        this.statut = StatutGarantie.RESTITUE_TOTAL;
        this.soldeActuel = BigDecimal.ZERO;
    }

    /** Restitution partielle avec retenue + motif (EF-42) : {@code DETENU} → {@code RESTITUE_PARTIEL}. */
    public void restituerPartiel(BigDecimal montantRetenu, String motifRetenue) {
        this.statut = StatutGarantie.RESTITUE_PARTIEL;
        this.montantRetenu = montantRetenu;
        this.motifRetenue = motifRetenue;
        // Depuis le solde courant (Sprint 10) : depuis le Sprint 9, soldeActuel peut déjà diverger
        // de montant (RETENUE_LOYER/COMPLEMENT) avant qu'une restitution n'intervienne — soustraire
        // de montant recalculerait un solde incohérent avec les mouvements déjà enregistrés.
        this.soldeActuel = this.soldeActuel.subtract(montantRetenu);
    }

    /** Retenue sur loyer impayé (US-95, ADR-14 §5) : diminue le solde, décision explicite hors
     * cycle de restitution — le statut ({@code DETENU}/{@code RESTITUE_PARTIEL}) n'est pas affecté. */
    public void retenirSurLoyer(BigDecimal montant) {
        this.soldeActuel = this.soldeActuel.subtract(montant);
    }

    /** Réapprovisionnement (US-96) : augmente le solde disponible, statut inchangé. */
    public void complementer(BigDecimal montant) {
        this.soldeActuel = this.soldeActuel.add(montant);
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
    public BigDecimal getSoldeActuel() { return soldeActuel; }
}
