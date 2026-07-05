package com.loyertracker.quittances;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Quittance certifiée (ADR-15/D-QC-001) : exemplaire de référence persistant, isolé par RLS sur
 * {@code bailleur_id}. Une ligne par version — la régénération conserve le numéro, incrémente la
 * version et chaîne l'ancienne ligne via {@code remplaceePar}. Le PDF stocké est l'unique
 * exemplaire officiel ; {@code contenu} est le payload canonique exact dont le SHA-256 est
 * {@code contentHash} (jamais recalculé depuis les données vivantes).
 */
@Entity
@Table(name = "quittance")
public class Quittance {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(name = "paiement_id", nullable = false, updatable = false)
    private UUID paiementId;

    @Column(nullable = false, updatable = false, length = 20)
    private String numero;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutQuittance statut;

    @Column(name = "remplacee_par")
    private UUID remplaceePar;

    @Column(nullable = false, updatable = false)
    private String contenu;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Column(name = "pdf_hash", nullable = false, updatable = false, length = 64)
    private String pdfHash;

    @Column(nullable = false, updatable = false)
    private byte[] pdf;

    @Column(name = "empreinte_metier", nullable = false, updatable = false, length = 64)
    private String empreinteMetier;

    @Column(name = "token_kid", nullable = false, updatable = false)
    private short tokenKid;

    // Posée par Postgres (DEFAULT now()), lue seulement — même approche que garantie_movement.cree_le.
    @Column(name = "emise_le", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime emiseLe;

    @Column(name = "nb_telechargements", nullable = false, insertable = false, updatable = false)
    private int nbTelechargements;

    @Column(name = "nb_verifications", nullable = false, insertable = false, updatable = false)
    private int nbVerifications;

    protected Quittance() {
        // requis par JPA
    }

    // S107 : instantané certifié immuable, chaque champ étant figé à l'émission (colonnes non
    // modifiables) — un builder ou des objets intermédiaires n'apporteraient aucune garantie ici.
    @SuppressWarnings("java:S107")
    public Quittance(UUID id, UUID bailleurId, UUID paiementId, String numero, int version,
            String contenu, String contentHash, String pdfHash, byte[] pdf,
            String empreinteMetier, short tokenKid) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.paiementId = paiementId;
        this.numero = numero;
        this.version = version;
        this.statut = StatutQuittance.EMISE;
        this.contenu = contenu;
        this.contentHash = contentHash;
        this.pdfHash = pdfHash;
        this.pdf = pdf;
        this.empreinteMetier = empreinteMetier;
        this.tokenKid = tokenKid;
    }

    /** Marque cette version comme remplacée par {@code remplacante} (régénération, US-99). */
    public void remplacerPar(UUID remplacante) {
        this.statut = StatutQuittance.REMPLACEE;
        this.remplaceePar = remplacante;
    }

    /** Annule la quittance (US-99). Le numéro reste consommé à jamais. */
    public void annuler() {
        this.statut = StatutQuittance.ANNULEE;
    }

    public UUID getId() { return id; }
    public UUID getBailleurId() { return bailleurId; }
    public UUID getPaiementId() { return paiementId; }
    public String getNumero() { return numero; }
    public int getVersion() { return version; }
    public StatutQuittance getStatut() { return statut; }
    public UUID getRemplaceePar() { return remplaceePar; }
    public String getContenu() { return contenu; }
    public String getContentHash() { return contentHash; }
    public String getPdfHash() { return pdfHash; }
    public byte[] getPdf() { return pdf; }
    public String getEmpreinteMetier() { return empreinteMetier; }
    public short getTokenKid() { return tokenKid; }
    public OffsetDateTime getEmiseLe() { return emiseLe; }
    public int getNbTelechargements() { return nbTelechargements; }
    public int getNbVerifications() { return nbVerifications; }
}
