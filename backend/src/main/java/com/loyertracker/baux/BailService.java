package com.loyertracker.baux;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditService;
import com.loyertracker.biens.Bien;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.biens.StatutBien;
import com.loyertracker.garanties.Garantie;
import com.loyertracker.garanties.GarantieRepository;
import com.loyertracker.garanties.StatutGarantie;
import com.loyertracker.paiements.PaiementRepository;
import com.loyertracker.paiements.StatutPaiement;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.PersistenceException;

@Service
public class BailService {

    private static final String ENTITY_TYPE = "bail";

    private final BailRepository baux;
    private final BienRepository biens;
    private final GarantieRepository garanties;
    private final PaiementRepository paiements;
    private final TenantContext tenant;
    private final AuditService audit;

    public BailService(BailRepository baux, BienRepository biens, GarantieRepository garanties,
            PaiementRepository paiements, TenantContext tenant, AuditService audit) {
        this.baux = baux;
        this.biens = biens;
        this.garanties = garanties;
        this.paiements = paiements;
        this.tenant = tenant;
        this.audit = audit;
    }

    @Transactional
    public BailDto creer(UUID bienId, BailRequest requete) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        Bien bien = biens.findById(bienId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bien introuvable."));
        if (bien.getStatut() != StatutBien.LIBRE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bail ne peut être créé que sur un bien libre.");
        }
        if (baux.existsByBienIdAndStatut(bienId, StatutBail.ACTIF)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bail actif existe déjà sur ce bien.");
        }
        Devise devise = requete.devise() != null ? requete.devise() : Devise.EUR;
        Bail bail = new Bail(UUID.randomUUID(), bailleurId, bienId, requete.locataireNom(),
                requete.locataireEmail(), requete.loyerHc(), requete.provisionCharges(),
                requete.dateDebut(), requete.dateFin(), devise);
        try {
            Bail enregistre = baux.saveAndFlush(bail);
            bien.louer();
            // Aucune garantie ne peut encore exister pour un bail qui vient d'être créé.
            return BailDto.from(enregistre, BigDecimal.ZERO);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bail actif existe déjà sur ce bien.");
        } catch (PersistenceException e) {
            if (estViolationUnicite(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un bail actif existe déjà sur ce bien.");
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<BailDto> historique(UUID bienId) {
        tenant.activerDepuisBien(bienId);
        return baux.findByBienIdOrderByDateDebutDesc(bienId).stream()
                .map(bail -> BailDto.from(bail, garanties.sommeMontantDeposeParBail(bail.getId())))
                .toList();
    }

    @Transactional
    public ClotureBailDto cloturer(UUID bienId, UUID bailId, ClotureRequest requete,
            Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        Bail bail = exigerBailDuBien(bienId, bailId);
        LocalDate dateCloture = (requete != null && requete.dateClotureEffective() != null)
                ? requete.dateClotureEffective() : LocalDate.now();

        bail.cloturer(dateCloture); // 409 si déjà CLOS
        Bail enregistre = baux.save(bail);

        List<String> avertissements = new ArrayList<>();

        // K3 : garantie non RESTITUE_TOTAL, ou aucune garantie du tout.
        List<Garantie> garantiesDuBail = garanties.findByBailIdOrderByDateDepotDesc(bailId);
        boolean garantieNonRestituee = garantiesDuBail.isEmpty()
                || garantiesDuBail.stream().anyMatch(g -> g.getStatut() != StatutGarantie.RESTITUE_TOTAL);
        if (garantieNonRestituee) {
            avertissements.add("La garantie n'est pas restituée intégralement (ou aucune garantie "
                    + "n'a été enregistrée pour ce bail).");
        }

        // K4 : paiements IMPAYE/EN_RETARD/PARTIEL en cours.
        if (paiements.existsByBailIdAndStatutIn(bailId,
                List.of(StatutPaiement.IMPAYE, StatutPaiement.EN_RETARD, StatutPaiement.PARTIEL))) {
            avertissements.add("Des paiements impayés, en retard ou partiels subsistent sur ce bail.");
        }

        // US-117/K6 : purge des A_VENIR strictement postérieurs au mois de clôture.
        String moisCloture = dateCloture.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        paiements.deleteByBailIdAndStatutAndPeriodeGreaterThan(bailId, StatutPaiement.A_VENIR, moisCloture);

        audit.enregistrer(authentication, bailleurId, "CLOTURER_BAIL", ENTITY_TYPE, bailId);
        BigDecimal montantDepose = garanties.sommeMontantDeposeParBail(bailId);
        return new ClotureBailDto(BailDto.from(enregistre, montantDepose), avertissements);
    }

    @Transactional
    public BailDto rouvrir(UUID bienId, UUID bailId, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisBien(bienId);
        Bail bail = exigerBailDuBien(bienId, bailId);
        if (baux.existsByBienIdAndStatut(bienId, StatutBail.ACTIF)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bail actif existe déjà sur ce bien.");
        }
        bail.rouvrir(); // 409 si le bail n'est pas CLOS
        try {
            Bail enregistre = baux.saveAndFlush(bail);
            audit.enregistrer(authentication, bailleurId, "ROUVRIR_BAIL", ENTITY_TYPE, bailId);
            return BailDto.from(enregistre, garanties.sommeMontantDeposeParBail(bailId));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bail actif existe déjà sur ce bien.");
        } catch (PersistenceException e) {
            if (estViolationUnicite(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Un bail actif existe déjà sur ce bien.");
            }
            throw e;
        }
    }

    private Bail exigerBailDuBien(UUID bienId, UUID bailId) {
        return baux.findByIdAndBienId(bailId, bienId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bail introuvable."));
    }

    private static boolean estViolationUnicite(Throwable t) {
        while (t != null) {
            if (t instanceof ConstraintViolationException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
