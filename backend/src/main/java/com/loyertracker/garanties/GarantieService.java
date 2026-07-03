package com.loyertracker.garanties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import com.loyertracker.honoraires.HonoraireService;
import com.loyertracker.paiements.Paiement;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.paiements.StatutPaiement;
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
    private final PaiementRepository paiements;
    private final TenantContext tenant;
    private final AuditService audit;
    private final HonoraireService honoraires;

    public GarantieService(GarantieRepository garanties, GarantieMovementRepository mouvements,
            BailRepository baux, PaiementRepository paiements, TenantContext tenant,
            AuditService audit, HonoraireService honoraires) {
        this.garanties = garanties;
        this.mouvements = mouvements;
        this.baux = baux;
        this.paiements = paiements;
        this.tenant = tenant;
        this.audit = audit;
        this.honoraires = honoraires;
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
        Garantie garantie = exigerGarantieDuBail(bailId, garantieId);
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
        if (requete.montantRetenu().compareTo(garantie.getSoldeActuel()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant retenu ne peut excéder le solde disponible de la garantie.");
        }
        garantie.restituerPartiel(requete.montantRetenu(), requete.motifRetenue());
    }

    /**
     * Retenue explicite sur un loyer impayé (US-95, ADR-14 §5) : jamais un prélèvement
     * automatique — le mouvement n'existe que parce que le gestionnaire a choisi ce paiement et ce
     * montant. Fait transitionner le paiement couvert vers {@code RECU}/{@code PARTIEL} (mêmes
     * seuils que {@link com.loyertracker.paiements.PaiementService#pointer}) et déclenche le
     * recalcul des honoraires, exactement comme un pointage manuel.
     */
    @Transactional
    public GarantieDto retenirSurLoyer(UUID bienId, UUID bailId, UUID garantieId,
            RetenueLoyerRequest requete, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        exigerBailDuBien(bienId, bailId);
        Garantie garantie = exigerGarantieDuBail(bailId, garantieId);
        if (garantie.getStatut() == StatutGarantie.RESTITUE_TOTAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Garantie déjà restituée intégralement.");
        }
        Paiement paiement = paiements.findById(requete.paiementId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Paiement introuvable."));
        exigerPaiementDuBail(paiement, bienId, bailId);
        if (paiement.getGarantieMovementId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce paiement est déjà couvert par un mouvement de garantie.");
        }
        if (requete.montant().compareTo(garantie.getSoldeActuel()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant excède le solde disponible de la garantie.");
        }
        if (requete.montant().compareTo(paiement.getResteDu()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant excède le reste dû de ce paiement.");
        }

        garantie.retenirSurLoyer(requete.montant());
        Garantie enregistre = garanties.save(garantie);
        GarantieMovement mouvement = enregistrerMouvement(enregistre, TypeMouvementGarantie.RETENUE_LOYER,
                requete.montant(), BigDecimal.ZERO, "Retenue sur loyer impayé", authentication);

        StatutPaiement statutResultant = requete.montant().compareTo(paiement.getMontantAttendu()) >= 0
                ? StatutPaiement.RECU : StatutPaiement.PARTIEL;
        paiement.pointer(requete.montant(), statutResultant);
        paiement.lierMouvementGarantie(mouvement.getId());
        paiements.save(paiement);

        audit.enregistrer(authentication, bailleurId, "RETENUE_LOYER_GARANTIE", "paiement",
                paiement.getId());
        honoraires.recalculerPourBien(bienId);
        return GarantieDto.from(enregistre);
    }

    /** Réapprovisionnement d'une garantie active (US-96). */
    @Transactional
    public GarantieDto complementer(UUID bienId, UUID bailId, UUID garantieId,
            ComplementRequest requete, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        exigerBailDuBien(bienId, bailId);
        Garantie garantie = exigerGarantieDuBail(bailId, garantieId);
        if (garantie.getStatut() == StatutGarantie.RESTITUE_TOTAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Garantie déjà restituée intégralement.");
        }
        garantie.complementer(requete.montant());
        Garantie enregistre = garanties.save(garantie);
        enregistrerMouvement(enregistre, TypeMouvementGarantie.COMPLEMENT, BigDecimal.ZERO,
                requete.montant(), requete.motif(), authentication);
        audit.enregistrer(authentication, bailleurId, "COMPLEMENT_GARANTIE", "garantie",
                enregistre.getId());
        return GarantieDto.from(enregistre);
    }

    /** Historique complet des mouvements d'une garantie, ordonné chronologiquement (US-97). */
    @Transactional(readOnly = true)
    public List<GarantieMovementDto> listerMouvements(UUID bienId, UUID bailId, UUID garantieId) {
        tenant.activerDepuisBien(bienId);
        exigerBailDuBien(bienId, bailId);
        exigerGarantieDuBail(bailId, garantieId);
        return mouvements.findByGarantieIdOrderByDateMouvementAscIdAsc(garantieId).stream()
                .map(GarantieMovementDto::from).toList();
    }

    /** Export CSV de l'historique des mouvements d'une garantie (US-97). */
    @Transactional(readOnly = true)
    public byte[] exporterMouvementsCsv(UUID bienId, UUID bailId, UUID garantieId) {
        List<GarantieMovementDto> lignes = listerMouvements(bienId, bailId, garantieId);
        StringBuilder csv = new StringBuilder(
                "date;type;debit;credit;solde;auteur;motif;commentaire;referenceDocument\n");
        for (GarantieMovementDto m : lignes) {
            csv.append(m.dateMouvement()).append(';').append(m.type()).append(';')
                    .append(m.debit()).append(';').append(m.credit()).append(';')
                    .append(m.soldeApres()).append(';').append(csvEchapper(m.utilisateur()))
                    .append(';').append(csvEchapper(m.motif())).append(';')
                    .append(csvEchapper(m.commentaire())).append(';')
                    .append(csvEchapper(m.referenceDocument())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvEchapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        String echappe = valeur.replace("\"", "\"\"");
        return echappe.contains(";") || echappe.contains("\"") || echappe.contains("\n")
                ? "\"" + echappe + "\"" : echappe;
    }

    /**
     * Journalise un mouvement du ledger (ADR-14 §1/§6) : {@code solde_apres} est lu depuis le
     * cache déjà à jour sur l'entité (mis à jour par {@code Garantie.restituerXxx}/constructeur
     * avant cet appel), garantissant la cohérence solde/mouvement dans la même transaction.
     */
    private GarantieMovement enregistrerMouvement(Garantie garantie, TypeMouvementGarantie type,
            BigDecimal debit, BigDecimal credit, String motif, Authentication authentication) {
        GarantieMovement.MouvementMontants montants =
                new GarantieMovement.MouvementMontants(debit, credit, garantie.getSoldeActuel());
        GarantieMovement mouvement = new GarantieMovement(garantie.getBailleurId(), garantie.getId(),
                type, montants, motif, resoudreUtilisateur(authentication));
        GarantieMovement enregistre = mouvements.save(mouvement);
        audit.enregistrer(authentication, garantie.getBailleurId(), type.name(),
                "garantie_movement", enregistre.getId());
        return enregistre;
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

    /** Vérifie que la garantie existe et appartient bien au bail ciblé (fail-closed 404). */
    private Garantie exigerGarantieDuBail(UUID bailId, UUID garantieId) {
        return garanties.findById(garantieId)
                .filter(g -> g.getBailId().equals(bailId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Garantie introuvable pour ce bail."));
    }

    /**
     * Vérifie que le paiement résolu par id appartient bien au bien et au bail ciblés
     * (fail-closed 404, ADR-14 §5) : Postgres ne peut pas vérifier nativement cette cohérence
     * cross-table — un même bien peut avoir eu plusieurs baux successifs générant chacun ses
     * propres paiements.
     */
    private void exigerPaiementDuBail(Paiement paiement, UUID bienId, UUID bailId) {
        if (!paiement.getBienId().equals(bienId) || !paiement.getBailId().equals(bailId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Paiement introuvable pour ce bail.");
        }
    }
}
