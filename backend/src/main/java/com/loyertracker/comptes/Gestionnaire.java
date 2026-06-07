package com.loyertracker.comptes;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Gestionnaire — acteur de délégation (US-12). Contrairement au {@code bailleur}, c'est un acteur
 * <strong>global, multi-bailleur</strong> : aucune colonne {@code bailleur_id}, aucune RLS. Son
 * périmètre d'accès est porté dynamiquement par les {@code Affectation} ACTIVE (ADR-01/ADR-02).
 *
 * <p>Un même compte (clé {@link #keycloakId}) peut être réutilisé par plusieurs bailleurs (EF-05) :
 * l'unicité {@code keycloak_id}/{@code email} garantit l'idempotence du rattachement.</p>
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

    protected Gestionnaire() {
        // requis par JPA
    }

    public Gestionnaire(UUID id, String keycloakId, String email, String nom, String prenom) {
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
}
