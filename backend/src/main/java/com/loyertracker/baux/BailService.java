package com.loyertracker.baux;

import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.biens.Bien;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.biens.StatutBien;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.PersistenceException;

@Service
public class BailService {

    private final BailRepository baux;
    private final BienRepository biens;
    private final TenantContext tenant;

    public BailService(BailRepository baux, BienRepository biens, TenantContext tenant) {
        this.baux = baux;
        this.biens = biens;
        this.tenant = tenant;
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
        Bail bail = new Bail(UUID.randomUUID(), bailleurId, bienId, requete.locataireNom(),
                requete.locataireEmail(), requete.loyerCc(), requete.depotGarantie(),
                requete.dateDebut(), requete.dateFin());
        try {
            Bail enregistre = baux.saveAndFlush(bail);
            bien.louer();
            return BailDto.from(enregistre);
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
        return baux.findByBienIdOrderByDateDebutDesc(bienId).stream().map(BailDto::from).toList();
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
