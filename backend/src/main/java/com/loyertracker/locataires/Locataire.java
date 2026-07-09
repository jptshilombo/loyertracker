package com.loyertracker.locataires;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Locataire — entité de domaine indépendante du {@code Bail} (EP-15, ADR-16 D2). Intrinsèquement
 * lié à un seul bailleur : {@code bailleur_id} + RLS (ADR-01), contrairement au Gestionnaire
 * (global, multi-bailleur). <strong>Ne devient pas un compte utilisateur</strong> : aucune
 * identité Keycloak, aucun rôle RBAC, aucune connexion — reste un sujet de données (RGPD, ADR-03).
 *
 * <p>Statuts {@code ACTIVE}/{@code ARCHIVE} : un Locataire archivé ne peut plus être sélectionné
 * pour un nouveau bail (contrôle applicatif au niveau du service `Bail`, Sprint C) mais reste
 * consultable avec tout son historique. Aucune suppression physique.</p>
 */
@Entity
@Table(name = "locataire")
public class Locataire {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bailleur_id", nullable = false, updatable = false)
    private UUID bailleurId;

    @Column(nullable = false)
    private String nom;

    @Column
    private String prenom;

    @Column
    private String telephone;

    @Column
    private String email;

    @Column
    private String profession;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "type_piece_identite")
    private String typePieceIdentite;

    @Column(name = "numero_piece_identite")
    private String numeroPieceIdentite;

    @Column
    private byte[] photo;

    @Column(name = "contact_urgence")
    private String contactUrgence;

    @Column
    private String observations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutLocataire statut;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_archivage")
    private OffsetDateTime dateArchivage;

    protected Locataire() {
        // requis par JPA
    }

    public Locataire(UUID id, UUID bailleurId, LocataireRequest requete) {
        this.id = id;
        this.bailleurId = bailleurId;
        this.statut = StatutLocataire.ACTIVE;
        modifier(requete);
    }

    /** Remplacement complet des champs mutables (pattern PUT déjà utilisé pour Patrimoine). */
    public void modifier(LocataireRequest requete) {
        this.nom = requete.nom();
        this.prenom = requete.prenom();
        this.telephone = requete.telephone();
        this.email = requete.email();
        this.profession = requete.profession();
        this.dateNaissance = requete.dateNaissance();
        this.typePieceIdentite = requete.typePieceIdentite();
        this.numeroPieceIdentite = requete.numeroPieceIdentite();
        this.contactUrgence = requete.contactUrgence();
        this.observations = requete.observations();
        if (requete.photoBase64() != null) {
            this.photo = requete.photoBase64().isBlank()
                    ? null
                    : java.util.Base64.getDecoder().decode(requete.photoBase64());
        }
    }

    /** Aucune pré-condition métier exigée par le besoin PO (contrairement au Gestionnaire). */
    public void archiver() {
        if (this.statut == StatutLocataire.ARCHIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce locataire est déjà archivé.");
        }
        this.statut = StatutLocataire.ARCHIVE;
        this.dateArchivage = OffsetDateTime.now();
    }

    public void restaurer() {
        if (this.statut != StatutLocataire.ARCHIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un locataire archivé peut être restauré.");
        }
        this.statut = StatutLocataire.ACTIVE;
        this.dateArchivage = null;
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

    public String getPrenom() {
        return prenom;
    }

    public String getTelephone() {
        return telephone;
    }

    public String getEmail() {
        return email;
    }

    public String getProfession() {
        return profession;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public String getTypePieceIdentite() {
        return typePieceIdentite;
    }

    public String getNumeroPieceIdentite() {
        return numeroPieceIdentite;
    }

    public byte[] getPhoto() {
        return photo;
    }

    public String getContactUrgence() {
        return contactUrgence;
    }

    public String getObservations() {
        return observations;
    }

    public StatutLocataire getStatut() {
        return statut;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }

    public OffsetDateTime getDateArchivage() {
        return dateArchivage;
    }
}
