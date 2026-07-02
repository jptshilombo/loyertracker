package com.loyertracker.paiements;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditService;
import com.loyertracker.baux.Bail;
import com.loyertracker.baux.BailRepository;
import com.loyertracker.baux.Devise;
import com.loyertracker.honoraires.HonoraireService;
import com.loyertracker.securite.TenantContext;

/**
 * Pointage et consultation des loyers mensuels (US-31, EF-30/31/32). Les échéances sont produites
 * à terme échu par le batch (US-30). Toute opération est cloisonnée par RLS via {@link TenantContext}.
 */
@Service
public class PaiementService {

    private final PaiementRepository paiements;
    private final BailRepository baux;
    private final TenantContext tenant;
    private final AuditService audit;
    private final HonoraireService honoraires;

    public PaiementService(PaiementRepository paiements, BailRepository baux, TenantContext tenant,
            AuditService audit, HonoraireService honoraires) {
        this.paiements = paiements;
        this.baux = baux;
        this.tenant = tenant;
        this.audit = audit;
        this.honoraires = honoraires;
    }

    @Transactional(readOnly = true)
    public List<PaiementDto> historique(UUID bienId) {
        tenant.activerDepuisBien(bienId);
        // Résolution batch (pas de N+1) : tous les paiements d'un bien référencent un bail de ce
        // même bien (US-93, ADR-13).
        Map<UUID, Devise> devisesParBail = baux.findByBienIdOrderByDateDebutDesc(bienId).stream()
                .collect(Collectors.toMap(Bail::getId, Bail::getDevise));
        return paiements.findByBienIdOrderByPeriodeDesc(bienId).stream()
                .map(p -> PaiementDto.from(p, devisesParBail.get(p.getBailId())))
                .toList();
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
        // L'encaissement modifie l'assiette des honoraires POURCENTAGE du bien : recalcul synchrone
        // (US-40, EF-51). Le gel à PAYE protège les honoraires déjà validés. Même transaction/tenant.
        honoraires.recalculerPourBien(bienId);
        Devise devise = baux.findById(enregistre.getBailId()).map(Bail::getDevise).orElse(null);
        return PaiementDto.from(enregistre, devise);
    }
}
