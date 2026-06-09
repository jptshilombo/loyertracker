package com.loyertracker.paiements;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditService;
import com.loyertracker.securite.TenantContext;

/**
 * Pointage et consultation des loyers mensuels (US-31, EF-30/31/32). Les échéances sont produites
 * à terme échu par le batch (US-30). Toute opération est cloisonnée par RLS via {@link TenantContext}.
 */
@Service
public class PaiementService {

    private final PaiementRepository paiements;
    private final TenantContext tenant;
    private final AuditService audit;

    public PaiementService(PaiementRepository paiements, TenantContext tenant, AuditService audit) {
        this.paiements = paiements;
        this.tenant = tenant;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PaiementDto> historique(UUID bienId) {
        tenant.activerDepuisBien(bienId);
        return paiements.findByBienIdOrderByPeriodeDesc(bienId).stream().map(PaiementDto::from).toList();
    }

    @Transactional
    public PaiementDto pointer(UUID bienId, String periode, PointageRequest requete,
            Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        Paiement paiement = paiements.findByBienIdAndPeriode(bienId, periode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune échéance pour ce bien et cette période."));
        if (requete.statut() == StatutPaiement.PARTIEL
                && (requete.montantRecu().signum() <= 0
                        || requete.montantRecu().compareTo(paiement.getMontantAttendu()) >= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un paiement PARTIEL exige 0 < montant reçu < montant attendu.");
        }
        if (requete.statut() == StatutPaiement.RECU
                && requete.montantRecu().compareTo(paiement.getMontantAttendu()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un paiement RECU exige un montant reçu >= montant attendu.");
        }
        paiement.pointer(requete.montantRecu(), requete.statut());
        Paiement enregistre = paiements.save(paiement);
        audit.enregistrer(authentication, bailleurId, "POINTER_PAIEMENT", "paiement",
                enregistre.getId());
        return PaiementDto.from(enregistre);
    }
}
