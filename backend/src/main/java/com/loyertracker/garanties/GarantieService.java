package com.loyertracker.garanties;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditService;
import com.loyertracker.baux.Bail;
import com.loyertracker.baux.BailRepository;
import com.loyertracker.securite.TenantContext;

/**
 * Gestion du dépôt de garantie d'un bail (US-32, EF-40/41/42, Annexe A.5). Les opérations sont
 * cloisonnées par RLS via {@link TenantContext} ; l'accès est borné au bien (ReBAC {@code @authz}).
 */
@Service
public class GarantieService {

    private final GarantieRepository garanties;
    private final BailRepository baux;
    private final TenantContext tenant;
    private final AuditService audit;

    public GarantieService(GarantieRepository garanties, BailRepository baux, TenantContext tenant,
            AuditService audit) {
        this.garanties = garanties;
        this.baux = baux;
        this.tenant = tenant;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GarantieDto> lister(UUID bienId, UUID bailId) {
        tenant.activerDepuisBien(bienId);
        exigerBailDuBien(bienId, bailId);
        return garanties.findByBailIdOrderByDateDepotDesc(bailId).stream().map(GarantieDto::from).toList();
    }

    @Transactional
    public GarantieDto creer(UUID bienId, UUID bailId, GarantieRequest requete,
            Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        exigerBailDuBien(bienId, bailId);
        Garantie garantie = new Garantie(UUID.randomUUID(), bailleurId, bailId, requete.montant(),
                requete.typeGarantie(), requete.dateDepot());
        Garantie enregistre = garanties.save(garantie);
        audit.enregistrer(authentication, bailleurId, "CREATE_GARANTIE", "garantie",
                enregistre.getId());
        return GarantieDto.from(enregistre);
    }

    @Transactional
    public GarantieDto restituer(UUID bienId, UUID bailId, UUID garantieId,
            RestitutionRequest requete, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        exigerBailDuBien(bienId, bailId);
        Garantie garantie = garanties.findById(garantieId)
                .filter(g -> g.getBailId().equals(bailId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Garantie introuvable pour ce bail."));
        appliquerRestitution(garantie, requete);
        Garantie enregistre = garanties.save(garantie);
        audit.enregistrer(authentication, bailleurId, "RESTITUER_GARANTIE", "garantie",
                enregistre.getId());
        return GarantieDto.from(enregistre);
    }

    /** Transitions A.5 : DETENU → RESTITUE_PARTIEL → RESTITUE_TOTAL (sans retour en arrière). */
    private void appliquerRestitution(Garantie garantie, RestitutionRequest requete) {
        if (garantie.getStatut() == StatutGarantie.RESTITUE_TOTAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Garantie déjà restituée.");
        }
        if (requete.type() == TypeRestitution.TOTALE) {
            garantie.restituerTotal();
            return;
        }
        // PARTIELLE : uniquement depuis DETENU, retenue + motif obligatoires (EF-42).
        if (garantie.getStatut() != StatutGarantie.DETENU) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une restitution partielle n'est possible que depuis le statut DETENU.");
        }
        if (requete.montantRetenu() == null || requete.montantRetenu().signum() <= 0
                || requete.motifRetenue() == null || requete.motifRetenue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une restitution partielle exige un montant retenu (> 0) et un motif.");
        }
        if (requete.montantRetenu().compareTo(garantie.getMontant()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant retenu ne peut excéder le montant de la garantie.");
        }
        garantie.restituerPartiel(requete.montantRetenu(), requete.motifRetenue());
    }

    /** Vérifie, sous RLS, que le bail existe et appartient bien au bien ciblé (fail-closed 404). */
    private void exigerBailDuBien(UUID bienId, UUID bailId) {
        Bail bail = baux.findById(bailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bail introuvable."));
        if (!bail.getBienId().equals(bienId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Bail introuvable pour ce bien.");
        }
    }
}
