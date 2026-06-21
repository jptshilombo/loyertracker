package com.loyertracker.biens;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.patrimoine.PatrimoineRepository;
import com.loyertracker.patrimoine.TypeBien;
import com.loyertracker.patrimoine.TypeBienRepository;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.EntityManager;

@Service
public class BienService {

    private final BienRepository biens;
    private final PatrimoineRepository patrimoines;
    private final TypeBienRepository typesBiens;
    private final TenantContext tenant;
    private final EntityManager em;

    public BienService(BienRepository biens, PatrimoineRepository patrimoines,
            TypeBienRepository typesBiens, TenantContext tenant, EntityManager em) {
        this.biens = biens;
        this.patrimoines = patrimoines;
        this.typesBiens = typesBiens;
        this.tenant = tenant;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<BienDto> lister(Authentication authentication) {
        Jwt jwt = principalJwt(authentication);
        if (aRole(authentication, "ROLE_BAILLEUR")) {
            UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());
            return biens.findByBailleurIdOrderByAdresseAsc(bailleurId).stream()
                    .map(BienDto::from)
                    .toList();
        }
        if (aRole(authentication, "ROLE_GESTIONNAIRE")) {
            return listerBiensGestionnaire(jwt.getSubject());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @Transactional
    public BienDto creer(Jwt jwt, BienRequest requete) {
        UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());
        validerPatrimoine(requete.patrimoineId());
        validerType(requete.type());
        Bien bien = new Bien(UUID.randomUUID(), bailleurId, requete.adresse(), requete.type(),
                requete.statut(), requete.patrimoineId());
        return BienDto.from(biens.saveAndFlush(bien));
    }

    @Transactional
    public BienDto modifier(UUID id, Jwt jwt, BienRequest requete) {
        tenant.activerDepuisKeycloak(jwt.getSubject());
        validerPatrimoine(requete.patrimoineId());
        validerType(requete.type());
        Bien bien = biens.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bien introuvable."));
        bien.modifier(requete.adresse(), requete.type(), requete.statut(), requete.patrimoineId());
        return BienDto.from(bien);
    }

    private void validerPatrimoine(UUID patrimoineId) {
        if (!patrimoines.existsById(patrimoineId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Patrimoine introuvable.");
        }
    }

    private void validerType(String type) {
        TypeBien typeBien = typesBiens.findById(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Type de bien invalide."));
        if (!typeBien.isActif()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de bien invalide.");
        }
    }

    @Transactional
    public BienDto archiver(UUID id, Jwt jwt) {
        tenant.activerDepuisKeycloak(jwt.getSubject());
        Bien bien = biens.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bien introuvable."));
        bien.archiver();
        return BienDto.from(bien);
    }

    @SuppressWarnings("unchecked")
    private List<BienDto> listerBiensGestionnaire(String keycloakId) {
        List<Object[]> lignes = em.createNativeQuery(
                        "SELECT id, adresse, type, statut FROM biens_affectes_gestionnaire(:keycloakId)")
                .setParameter("keycloakId", keycloakId)
                .getResultList();
        return lignes.stream()
                .map(l -> new BienDto((UUID) l[0], (String) l[1], (String) l[2], (String) l[3], null))
                .toList();
    }

    private static Jwt principalJwt(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return jwt;
    }

    private static boolean aRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(autorite -> role.equals(autorite.getAuthority()));
    }
}
