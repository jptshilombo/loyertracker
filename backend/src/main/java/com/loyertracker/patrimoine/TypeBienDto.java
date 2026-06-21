package com.loyertracker.patrimoine;

/** Vue API d'un type de bien administrable. */
public record TypeBienDto(String code, String libelle, boolean actif) {

    public static TypeBienDto from(TypeBien typeBien) {
        return new TypeBienDto(typeBien.getCode(), typeBien.getLibelle(), typeBien.isActif());
    }
}
