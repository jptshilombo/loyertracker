package com.loyertracker.comptes;

import java.util.List;

import com.loyertracker.affectations.AffectationDto;
import com.loyertracker.audit.AuditDto;

/**
 * Historique d'un Gestionnaire (EP-15, EF-104) : profil courant, affectations et entrées d'audit
 * — les deux dernières listes sont naturellement scopées par RLS au bailleur courant (aucune
 * fuite d'informations sur les relations avec d'autres bailleurs, cf. ADR-16 RSV-EP15-01).
 */
public record GestionnaireHistoriqueDto(GestionnaireDto gestionnaire, List<AffectationDto> affectations,
        List<AuditDto> audit) {
}
