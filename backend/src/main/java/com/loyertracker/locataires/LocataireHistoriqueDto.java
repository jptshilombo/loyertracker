package com.loyertracker.locataires;

import java.util.List;

import com.loyertracker.audit.AuditDto;

/**
 * Historique d'un Locataire (EP-15, EF-105). Le Locataire n'est pas encore référencé par
 * `Bail` dans ce sprint (bascule V25, Sprint C) : baux/paiements/garanties/quittances rejoignent
 * cet historique une fois le rattachement `bail.locataire_id` en place, sans changement de ce
 * contrat — seul l'audit est disponible pour l'instant, naturellement scopé par RLS.
 */
public record LocataireHistoriqueDto(LocataireDto locataire, List<AuditDto> audit) {
}
