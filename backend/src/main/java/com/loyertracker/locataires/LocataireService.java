package com.loyertracker.locataires;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.audit.AuditDto;
import com.loyertracker.audit.AuditLogRepository;
import com.loyertracker.audit.AuditService;
import com.loyertracker.securite.TenantContext;

/**
 * CRUD et cycle de vie du Locataire (EP-15, ADR-16 D2). Entité RLS-scopée au bailleur courant —
 * contrairement au Gestionnaire, aucune garde cross-tenant n'est nécessaire pour l'archivage
 * (aucune pré-condition métier exigée par le besoin PO).
 */
@Service
public class LocataireService {

    private static final String ENTITY_TYPE = "locataire";

    private final LocataireRepository locataires;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final TenantContext tenant;

    public LocataireService(LocataireRepository locataires, AuditLogRepository auditLog,
            AuditService audit, TenantContext tenant) {
        this.locataires = locataires;
        this.auditLog = auditLog;
        this.audit = audit;
        this.tenant = tenant;
    }

    /** Recherche multicritère (EF-102) : nom/prénom/téléphone/email/numéro de pièce d'identité. */
    @Transactional(readOnly = true)
    public List<LocataireDto> rechercher(Authentication authentication, String q) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        return locataires.findByBailleurIdOrderByNomAscPrenomAsc(bailleurId).stream()
                .filter(l -> correspond(l, q))
                .map(LocataireDto::from)
                .toList();
    }

    /** Détection de doublons (EF-103) : avertissement, jamais un rejet automatique. */
    @Transactional(readOnly = true)
    public List<LocataireDto> detecterDoublons(Authentication authentication, String email,
            String telephone, String numeroPieceIdentite) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        return locataires.findByBailleurIdOrderByNomAscPrenomAsc(bailleurId).stream()
                .filter(l -> (email != null && !email.isBlank() && email.equalsIgnoreCase(l.getEmail()))
                        || (telephone != null && !telephone.isBlank() && telephone.equals(l.getTelephone()))
                        || (numeroPieceIdentite != null && !numeroPieceIdentite.isBlank()
                                && numeroPieceIdentite.equals(l.getNumeroPieceIdentite())))
                .map(LocataireDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocataireDto consulter(UUID id, Authentication authentication) {
        tenant.activerDepuisKeycloak(sub(authentication));
        return LocataireDto.from(trouver(id));
    }

    @Transactional
    public LocataireDto creer(LocataireRequest requete, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Locataire l = new Locataire(UUID.randomUUID(), bailleurId, requete);
        locataires.saveAndFlush(l);
        audit.enregistrer(authentication, bailleurId, "CREER_LOCATAIRE", ENTITY_TYPE, l.getId());
        return LocataireDto.from(l);
    }

    @Transactional
    public LocataireDto modifier(UUID id, LocataireRequest requete, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Locataire l = trouver(id);
        l.modifier(requete);
        audit.enregistrer(authentication, bailleurId, "MODIFIER_LOCATAIRE", ENTITY_TYPE, id);
        return LocataireDto.from(l);
    }

    @Transactional
    public LocataireDto archiver(UUID id, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Locataire l = trouver(id);
        l.archiver();
        audit.enregistrer(authentication, bailleurId, "ARCHIVER_LOCATAIRE", ENTITY_TYPE, id);
        return LocataireDto.from(l);
    }

    @Transactional
    public LocataireDto restaurer(UUID id, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Locataire l = trouver(id);
        l.restaurer();
        audit.enregistrer(authentication, bailleurId, "RESTAURER_LOCATAIRE", ENTITY_TYPE, id);
        return LocataireDto.from(l);
    }

    @Transactional(readOnly = true)
    public LocataireHistoriqueDto historique(UUID id, Authentication authentication) {
        tenant.activerDepuisKeycloak(sub(authentication));
        Locataire l = trouver(id);
        List<AuditDto> auditDto = auditLog.findByEntityTypeAndEntityIdOrderByHorodatageDesc(ENTITY_TYPE, id)
                .stream().map(AuditDto::from).toList();
        return new LocataireHistoriqueDto(LocataireDto.from(l), auditDto);
    }

    private Locataire trouver(UUID id) {
        return locataires.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Locataire introuvable."));
    }

    private static String sub(Authentication authentication) {
        return ((Jwt) authentication.getPrincipal()).getSubject();
    }

    private static boolean correspond(Locataire l, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String besoin = q.toLowerCase();
        return contient(l.getNom(), besoin) || contient(l.getPrenom(), besoin)
                || contient(l.getTelephone(), besoin) || contient(l.getEmail(), besoin)
                || contient(l.getNumeroPieceIdentite(), besoin);
    }

    private static boolean contient(String champ, String besoin) {
        return champ != null && champ.toLowerCase().contains(besoin);
    }
}
