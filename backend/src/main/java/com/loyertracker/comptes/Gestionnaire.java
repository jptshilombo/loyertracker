package com.loyertracker.comptes;

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
 * Gestionnaire — acteur de délégation (US-12). Contrairement au {@code bailleur}, c'est un acteur
 * <strong>global, multi-bailleur</strong> : aucune colonne {@code bailleur_id}, aucune RLS. Son
 * périmètre d'accès est porté dynamiquement par les {@code Affectation} ACTIVE (ADR-01/ADR-02).
 *
 * <p>Un même compte (clé {@link #keycloakId}) peut être réutilisé par plusieurs bailleurs (EF-05) :
 * l'unicité {@code keycloak_id}/{@code email} garantit l'idempotence du rattachement.</p>
 *
 * <p><strong>Statut de cycle de vie (EP-15, ADR-16 D1)</strong> : {@link #statut} est
 * <strong>global</strong>, partagé par tous les bailleurs employant ce gestionnaire. {@code
 * SUSPENDU} n'a aucune pré-condition et reste immédiatement restaurable ; {@code ARCHIVE} exige
 * l'absence de toute {@code Affectation} {@code ACTIVE}, tous bailleurs confondus (vérifié en
 * amont par le service, fonction {@code gestionnaire_a_affectation_active}). Aucune suppression
 * physique.</p>
 */
@Entity
@Table(name = "gestionnaire")
public class Gestionnaire {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, updatable = false)
    private String keycloakId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutGestionnaire statut;

    @Column
    private String telephone;

    @Column
    private byte[] photo;

    @Column
    private String observations;

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    @Column(name = "date_suspension")
    private OffsetDateTime dateSuspension;

    @Column(name = "date_archivage")
    private OffsetDateTime dateArchivage;

    protected Gestionnaire() {
        // requis par JPA
    }

    public Gestionnaire(UUID id, String keycloakId, String email, String nom, String prenom) {
        this.id = id;
        this.keycloakId = keycloakId;
        this.email = email;
        this.nom = nom;
        this.prenom = prenom;
        this.statut = StatutGestionnaire.ACTIVE;
    }

    /** Complète/modifie le profil métier (K1, ADR-16 : jamais la création du compte technique). */
    public void modifierProfil(String telephone, byte[] photo, String observations) {
        this.telephone = telephone;
        this.photo = photo;
        this.observations = observations;
    }

    /** Immédiat, sans pré-condition (règle métier PO) ; bloque la connexion (Keycloak désactivé). */
    public void suspendre() {
        if (this.statut == StatutGestionnaire.ARCHIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un gestionnaire archivé doit être restauré avant toute suspension.");
        }
        this.statut = StatutGestionnaire.SUSPENDU;
        this.dateSuspension = OffsetDateTime.now();
    }

    /** Réactive un compte {@code SUSPENDU} (Keycloak réactivé). */
    public void reactiver() {
        if (this.statut != StatutGestionnaire.SUSPENDU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un gestionnaire suspendu peut être réactivé.");
        }
        this.statut = StatutGestionnaire.ACTIVE;
        this.dateSuspension = null;
    }

    /** Le garde d'absence d'affectation active (tous bailleurs) est vérifié en amont par le service. */
    public void archiver() {
        if (this.statut == StatutGestionnaire.ARCHIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce gestionnaire est déjà archivé.");
        }
        this.statut = StatutGestionnaire.ARCHIVE;
        this.dateArchivage = OffsetDateTime.now();
    }

    /** Restaure un compte {@code ARCHIVE} ; aucune affectation n'est recréée automatiquement. */
    public void restaurer() {
        if (this.statut != StatutGestionnaire.ARCHIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un gestionnaire archivé peut être restauré.");
        }
        this.statut = StatutGestionnaire.ACTIVE;
        this.dateArchivage = null;
    }

    public UUID getId() {
        return id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public String getEmail() {
        return email;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public StatutGestionnaire getStatut() {
        return statut;
    }

    public String getTelephone() {
        return telephone;
    }

    public byte[] getPhoto() {
        return photo;
    }

    public String getObservations() {
        return observations;
    }

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }

    public OffsetDateTime getDateSuspension() {
        return dateSuspension;
    }

    public OffsetDateTime getDateArchivage() {
        return dateArchivage;
    }
}
