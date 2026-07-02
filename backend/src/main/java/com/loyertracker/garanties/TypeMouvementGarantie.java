package com.loyertracker.garanties;

/** Nature d'un mouvement du ledger de garantie (ADR-14/D-GAR-001). */
public enum TypeMouvementGarantie {
    DEPOT_INITIAL,
    COMPLEMENT,
    RETENUE_LOYER,
    RETENUE_CHARGES,
    RETENUE_REPARATION,
    RESTITUTION,
    AJUSTEMENT,
    ANNULATION
}
