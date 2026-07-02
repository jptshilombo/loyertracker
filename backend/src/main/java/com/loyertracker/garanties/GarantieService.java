package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditService;
import com.loyertracker.baux.Bail;
import com.loyertracker.baux.BailRepository;
import com.loyertracker.securite.TenantContext;

/**
 * Gestion du dépôt de garantie d'un bail (US-32, EF-40/41/42, Annexe A.5) et de son ledger de
 * mouvements (US-94, ADR-14/D-GAR-001). Les opérations sont cloisonnées par RLS via
 * {@link TenantContext} ; l'accès est borné au bien (ReBAC {@code @authz}). Chaque transition
 * d'état ({@code creer}/{@code restituer}) ajuste le cache {@code Garantie.soldeActuel} (via les
 * méthodes de l'entité) et journalise le mouvement correspondant dans {@code garantie_movement},
 * dans la même transaction — jamais de mutation directe du solde sans mouvement associé.
 */
@Service
public class GarantieService {

    private final GarantieRepository garanties;
    private final GarantieMovementRepository mouvements;
    private final BailRepository baux;
    private final TenantContext tenant;
    private final AuditService audit;

    public GarantieService(GarantieRepository garanties, GarantieMovementRepository mouvements,
            BailRepository baux, TenantContext tenant, AuditService audit) {
        this.garanties = garanties;
        this.mouvements = mouvements;
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
        enregistrerMouvement(enregistre, TypeMouvementGarantie.DEPOT_INITIAL, BigDecimal.ZERO,
                requete.montant(), "Dépôt initial de la garantie", authentication);
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
        BigDecimal soldeAvant = garantie.getSoldeActuel();
        appliquerRestitution(garantie, requete);
        Garantie enregistre = garanties.save(garantie);
        // Débit = ce que la transition a réellement retiré du solde (générique aux deux
        // branches TOTALE/PARTIELLE ci-dessous, plutôt que dupliquer la logique de calcul).
        BigDecimal debit = soldeAvant.subtract(enregistre.getSoldeActuel());
        if (debit.signum() > 0) {
            boolean totale = requete.type() == TypeRestitution.TOTALE;
            enregistrerMouvement(enregistre,
                    totale ? TypeMouvementGarantie.RESTITUTION : TypeMouvementGarantie.AJUSTEMENT,
                    debit, BigDecimal.ZERO,
                    totale ? "Restitution totale" : requete.motifRetenue(), authentication);
        }
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

    /**
     * Journalise un mouvement du ledger (ADR-14 §1/§6) : {@code solde_apres} est lu depuis le
     * cache déjà à jour sur l'entité (mis à jour par {@code Garantie.restituerXxx}/constructeur
     * avant cet appel), garantissant la cohérence solde/mouvement dans la même transaction.
     */
    private void enregistrerMouvement(Garantie garantie, TypeMouvementGarantie type,
            BigDecimal debit, BigDecimal credit, String motif, Authentication authentication) {
        GarantieMovement mouvement = new GarantieMovement(UUID.randomUUID(), garantie.getBailleurId(),
                garantie.getId(), LocalDate.now(), type, debit, credit, garantie.getSoldeActuel(),
                motif, resoudreUtilisateur(authentication));
        mouvements.save(mouvement);
        audit.enregistrer(authentication, garantie.getBailleurId(), type.name(),
                "garantie_movement", mouvement.getId());
    }

    /** Identifiant lisible de l'acteur pour le ledger (email si disponible, sinon sub Keycloak). */
    private static String resoudreUtilisateur(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String email = jwt.getClaimAsString("email");
        return email != null ? email : jwt.getSubject();
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
