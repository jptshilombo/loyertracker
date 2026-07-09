package com.loyertracker.comptes;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.affectations.AffectationDto;
import com.loyertracker.affectations.AffectationRepository;
import com.loyertracker.audit.AuditDto;
import com.loyertracker.audit.AuditLogRepository;
import com.loyertracker.audit.AuditService;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.EntityManager;

/**
 * Cycle de vie et profil du Gestionnaire (EP-15, ADR-16). Le statut (D1) est <strong>global</strong>
 * : les opérations de suspension/réactivation/archivage/restauration affectent le compte pour
 * tous les bailleurs qui l'emploient — l'autorisation (« ce bailleur a une relation avec ce
 * gestionnaire ») est vérifiée en amont par {@code @authz.peutAccederGestionnaire}.
 */
@Service
public class GestionnaireService {

    private static final String ENTITY_TYPE = "gestionnaire";

    private final GestionnaireRepository gestionnaires;
    private final AffectationRepository affectations;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final GestionnaireIdentityProvider idp;
    private final TenantContext tenant;
    private final EntityManager em;

    public GestionnaireService(GestionnaireRepository gestionnaires, AffectationRepository affectations,
            AuditLogRepository auditLog, AuditService audit, GestionnaireIdentityProvider idp,
            TenantContext tenant, EntityManager em) {
        this.gestionnaires = gestionnaires;
        this.affectations = affectations;
        this.auditLog = auditLog;
        this.audit = audit;
        this.idp = idp;
        this.tenant = tenant;
        this.em = em;
    }

    /** Recherche multicritère (EF-102) parmi les gestionnaires en relation avec ce bailleur. */
    @Transactional(readOnly = true)
    public List<GestionnaireDto> rechercher(Authentication authentication, String q) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        return gestionnaires.findEnRelationAvecBailleur(bailleurId).stream()
                .filter(g -> correspond(g, q))
                .map(GestionnaireDto::from)
                .toList();
    }

    /** Détection de doublons (EF-103) : avertissement, jamais un rejet automatique. */
    @Transactional(readOnly = true)
    public List<GestionnaireDto> detecterDoublons(Authentication authentication, String email, String telephone) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        return gestionnaires.findEnRelationAvecBailleur(bailleurId).stream()
                .filter(g -> (email != null && !email.isBlank() && email.equalsIgnoreCase(g.getEmail()))
                        || (telephone != null && !telephone.isBlank() && telephone.equals(g.getTelephone())))
                .map(GestionnaireDto::from)
                .toList();
    }

    /** Détail d'un Gestionnaire (accès conditionné à la relation, cf. {@code @authz}). */
    @Transactional(readOnly = true)
    public GestionnaireDto consulter(UUID id, Authentication authentication) {
        tenant.activerDepuisKeycloak(sub(authentication));
        return GestionnaireDto.from(trouver(id));
    }

    /** Complète/modifie le profil métier (K1 : jamais la création du compte technique). */
    @Transactional
    public GestionnaireDto modifierProfil(UUID id, GestionnaireProfilRequest requete, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Gestionnaire g = trouver(id);
        byte[] photo = decoderPhoto(requete.photoBase64());
        g.modifierProfil(requete.telephone(), photo, requete.observations());
        audit.enregistrer(authentication, bailleurId, "MODIFIER_GESTIONNAIRE", ENTITY_TYPE, id);
        return GestionnaireDto.from(g);
    }

    /** Immédiat, sans pré-condition (règle métier PO). */
    @Transactional
    public GestionnaireDto suspendre(UUID id, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Gestionnaire g = trouver(id);
        g.suspendre();
        idp.definirActivation(g.getKeycloakId(), false);
        audit.enregistrer(authentication, bailleurId, "SUSPENDRE_GESTIONNAIRE", ENTITY_TYPE, id);
        return GestionnaireDto.from(g);
    }

    /** Restaure un compte {@code SUSPENDU}. */
    @Transactional
    public GestionnaireDto reactiver(UUID id, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Gestionnaire g = trouver(id);
        g.reactiver();
        idp.definirActivation(g.getKeycloakId(), true);
        audit.enregistrer(authentication, bailleurId, "REACTIVER_GESTIONNAIRE", ENTITY_TYPE, id);
        return GestionnaireDto.from(g);
    }

    /** Exige l'absence de toute affectation ACTIVE, tous bailleurs confondus (ADR-16 D4). */
    @Transactional
    public GestionnaireDto archiver(UUID id, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Gestionnaire g = trouver(id);
        if (affectationActiveQuelquePart(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archivage refusé : une affectation active existe pour ce gestionnaire (tous bailleurs confondus).");
        }
        g.archiver();
        idp.definirActivation(g.getKeycloakId(), false);
        audit.enregistrer(authentication, bailleurId, "ARCHIVER_GESTIONNAIRE", ENTITY_TYPE, id);
        return GestionnaireDto.from(g);
    }

    /** Restaure un compte {@code ARCHIVE} ; aucune affectation n'est recréée automatiquement. */
    @Transactional
    public GestionnaireDto restaurer(UUID id, Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(sub(authentication));
        Gestionnaire g = trouver(id);
        g.restaurer();
        idp.definirActivation(g.getKeycloakId(), true);
        audit.enregistrer(authentication, bailleurId, "RESTAURER_GESTIONNAIRE", ENTITY_TYPE, id);
        return GestionnaireDto.from(g);
    }

    /**
     * Chronologie (EF-104) : profil courant, affectations et audit — les deux dernières listes
     * sont naturellement scopées par RLS au bailleur courant (aucune fuite cross-bailleur).
     */
    @Transactional(readOnly = true)
    public GestionnaireHistoriqueDto historique(UUID id, Authentication authentication) {
        tenant.activerDepuisKeycloak(sub(authentication));
        Gestionnaire g = trouver(id);
        List<AffectationDto> affectationsDto = affectations.findByGestionnaireIdOrderByDateDebutDesc(id)
                .stream().map(AffectationDto::from).toList();
        List<AuditDto> auditDto = auditLog.findByEntityTypeAndEntityIdOrderByHorodatageDesc(ENTITY_TYPE, id)
                .stream().map(AuditDto::from).toList();
        return new GestionnaireHistoriqueDto(GestionnaireDto.from(g), affectationsDto, auditDto);
    }

    private Gestionnaire trouver(UUID id) {
        return gestionnaires.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gestionnaire introuvable."));
    }

    /** ADR-16 D4 : traverse la RLS d'`affectation` via la fonction SECURITY DEFINER étroite. */
    private boolean affectationActiveQuelquePart(UUID gestionnaireId) {
        return (Boolean) em.createNativeQuery(
                        "SELECT gestionnaire_a_affectation_active(CAST(:g AS uuid))")
                .setParameter("g", gestionnaireId.toString())
                .getSingleResult();
    }

    private static String sub(Authentication authentication) {
        return ((Jwt) authentication.getPrincipal()).getSubject();
    }

    private static byte[] decoderPhoto(String photoBase64) {
        return (photoBase64 == null || photoBase64.isBlank())
                ? null
                : Base64.getDecoder().decode(photoBase64);
    }

    private static boolean correspond(Gestionnaire g, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String besoin = q.toLowerCase();
        return contient(g.getNom(), besoin) || contient(g.getPrenom(), besoin)
                || contient(g.getTelephone(), besoin) || contient(g.getEmail(), besoin);
    }

    private static boolean contient(String champ, String besoin) {
        return champ != null && champ.toLowerCase().contains(besoin);
    }
}
