package com.loyertracker.patrimoine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Référentiel administrable de la typologie des biens (US-81/RM-93/BF-91) — global, sans RLS. */
@Entity
@Table(name = "type_bien")
public class TypeBien {

    @Id
    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private boolean actif;

    protected TypeBien() {
        // requis par JPA
    }

    public String getCode() {
        return code;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean isActif() {
        return actif;
    }
}
