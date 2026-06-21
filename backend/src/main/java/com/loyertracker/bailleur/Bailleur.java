package com.loyertracker.bailleur;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Bailleur — racine de tenant de l'application (US-10). Chaque bailleur correspond à un compte
 * Keycloak (claim {@code sub} → {@link #keycloakId}) et porte l'identifiant {@link #id} utilisé
 * comme discriminant d'isolation par la Row-Level Security PostgreSQL ({@code app.current_bailleur_id},
 * ADR-01).
 *
 * <p>L'identifiant est <strong>assigné par l'application</strong> (et non généré par la base) : à
 * l'inscription, le contexte RLS doit être positionné sur cet id <em>avant</em> l'INSERT pour
 * satisfaire la clause {@code WITH CHECK} de la policy (cf. {@link InscriptionService}).</p>
 */
@Entity
@Table(name = "bailleur")
public class Bailleur {

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

    @Column(name = "date_creation", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime dateCreation;

    /** Adresse postale — mention obligatoire de la quittance de loyer (V11). Renseignée a posteriori. */
    @Column(length = 500)
    private String adresse;

    protected Bailleur() {
        // requis par JPA
    }

    public Bailleur(UUID id, String keycloakId, String email, String nom, String prenom) {
        this.id = id;
        this.keycloakId = keycloakId;
        this.email = email;
        this.nom = nom;
        this.prenom = prenom;
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

    public OffsetDateTime getDateCreation() {
        return dateCreation;
    }

    public String getAdresse() {
        return adresse;
    }

    /** Met à jour l'adresse postale (profil bailleur, V11). */
    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }
}
