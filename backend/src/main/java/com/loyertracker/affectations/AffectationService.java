package com.loyertracker.affectations;

import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.biens.Bien;
import com.loyertracker.biens.BienRepository;
import com.loyertracker.comptes.GestionnaireRepository;
import com.loyertracker.affectations.Affectation.HonorairesAffectation;
import com.loyertracker.patrimoine.PatrimoineRepository;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.PersistenceException;

@Service
public class AffectationService {

    private final AffectationRepository affectations;
    private final BienRepository biens;
    private final PatrimoineRepository patrimoines;
    private final GestionnaireRepository gestionnaires;
    private final TenantContext tenant;

    public AffectationService(AffectationRepository affectations, BienRepository biens,
            PatrimoineRepository patrimoines, GestionnaireRepository gestionnaires, TenantContext tenant) {
        this.affectations = affectations;
        this.biens = biens;
        this.patrimoines = patrimoines;
        this.gestionnaires = gestionnaires;
        this.tenant = tenant;
    }

    @Transactional
    public AffectationDto creer(Jwt jwt, AffectationRequest requete) {
        UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());
        validerPerimetre(requete);
        Bien bien = resoudreBien(requete);
        if (!gestionnaires.existsById(requete.gestionnaireId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Gestionnaire introuvable.");
        }
        TypeException typeException = resoudreTypeException(requete, bien);
        Affectation affectation = construireAffectation(requete, bailleurId, bien, typeException);
        return enregistrer(affectation);
    }

    private static void validerPerimetre(AffectationRequest requete) {
        if (!requete.aExactementUnPerimetre()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une affectation doit cibler exactement un bien ou un patrimoine.");
        }
        if (requete.exceptionSansBien()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une exception (INCLUSION/EXCLUSION) ne s'applique qu'à une affectation sur un bien.");
        }
    }

    private Bien resoudreBien(AffectationRequest requete) {
        if (requete.bienId() == null) {
            if (!patrimoines.existsById(requete.patrimoineId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patrimoine introuvable.");
            }
            return null;
        }
        Bien bien = biens.findById(requete.bienId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bien introuvable."));
        if (bien.getStatut().name().equals("ARCHIVE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un bien archivé ne peut pas être affecté.");
        }
        return bien;
    }

    private TypeException resoudreTypeException(AffectationRequest requete, Bien bien) {
        if (bien == null) {
            return null;
        }
        TypeException typeException = requete.typeException() != null ? requete.typeException()
                : TypeException.INCLUSION;
        if (typeException == TypeException.EXCLUSION) {
            verifierExclusionAdossee(bien, requete.gestionnaireId());
        }
        return typeException;
    }

    private static Affectation construireAffectation(AffectationRequest requete, UUID bailleurId, Bien bien,
            TypeException typeException) {
        HonorairesAffectation honoraires = new HonorairesAffectation(requete.typeHonoraires(),
                requete.montantHonoraires(), requete.dateDebut(), requete.dateFin());
        if (bien != null) {
            return Affectation.surBien(UUID.randomUUID(), bailleurId, requete.bienId(),
                    requete.gestionnaireId(), typeException, honoraires);
        }
        return Affectation.surPatrimoine(UUID.randomUUID(), bailleurId, requete.patrimoineId(),
                requete.gestionnaireId(), honoraires);
    }

    private AffectationDto enregistrer(Affectation affectation) {
        try {
            return AffectationDto.from(affectations.saveAndFlush(affectation));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une affectation active existe déjà sur ce périmètre.");
        } catch (PersistenceException e) {
            if (estViolationUnicite(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Une affectation active existe déjà sur ce périmètre.");
            }
            throw e;
        }
    }

    @Transactional
    public AffectationDto revoquer(UUID id, Jwt jwt) {
        UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());
        Affectation affectation = affectations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Affectation introuvable."));
        if (!affectation.getBailleurId().equals(bailleurId)) {
            // Autorisation applicative explicite (ReBAC) : seul le bailleur propriétaire révoque.
            // Fail-closed en 404 (anti-énumération), sans dépendre de la seule RLS.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Affectation introuvable.");
        }
        if (affectation.getStatut() != StatutAffectation.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une affectation active peut être révoquée.");
        }
        affectation.revoquer();
        return AffectationDto.from(affectation);
    }

    @Transactional(readOnly = true)
    public List<AffectationDto> historique(UUID bienId, Jwt jwt) {
        tenant.activerDepuisKeycloak(jwt.getSubject());
        if (biens.findById(bienId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bien introuvable.");
        }
        return affectations.findByBienIdOrderByDateDebutDesc(bienId).stream()
                .map(AffectationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AffectationDto> historiquePatrimoine(UUID patrimoineId, Jwt jwt) {
        tenant.activerDepuisKeycloak(jwt.getSubject());
        if (!patrimoines.existsById(patrimoineId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patrimoine introuvable.");
        }
        return affectations.findByPatrimoineIdOrderByDateDebutDesc(patrimoineId).stream()
                .map(AffectationDto::from)
                .toList();
    }

    /**
     * RS-04 (validé PO 2026-06-21) : une EXCLUSION sans affectation patrimoine ACTIVE
     * correspondante du même gestionnaire est un état incohérent (rien à exclure) — rejetée en 400.
     */
    private void verifierExclusionAdossee(Bien bien, UUID gestionnaireId) {
        if (bien.getPatrimoineId() == null
                || !affectations.existsByPatrimoineIdAndGestionnaireIdAndStatut(
                        bien.getPatrimoineId(), gestionnaireId, StatutAffectation.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une exclusion nécessite une affectation patrimoine active de ce gestionnaire "
                            + "sur le patrimoine de ce bien.");
        }
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
