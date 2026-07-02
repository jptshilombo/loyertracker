package com.loyertracker.honoraires;

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
import com.loyertracker.baux.Devise;
import com.loyertracker.securite.TenantContext;

import jakarta.persistence.EntityManager;

/**
 * Calcul et suivi des honoraires de gestion (US-40, EF-50/51/52, Annexe A.6).
 *
 * <p>Le calcul est délégué à la fonction SQL {@code calculer_honoraires(p_bien_id)} (V8),
 * {@code SECURITY DEFINER} propriété du rôle {@code loyertracker_batch} : recalcul CIBLÉ après un
 * pointage de loyer (hook synchrone), ou COMPLET multi-bailleur via le batch. Le gel à {@code PAYE}
 * (EF-52) est garanti en base. La consultation et la validation sont cloisonnées par RLS via
 * {@link TenantContext} ; la validation est réservée au bailleur (cf. {@code HonoraireController}).</p>
 */
@Service
public class HonoraireService {

    private final HonoraireRepository honoraires;
    private final BailRepository baux;
    private final TenantContext tenant;
    private final AuditService audit;
    private final EntityManager em;

    public HonoraireService(HonoraireRepository honoraires, BailRepository baux, TenantContext tenant,
            AuditService audit, EntityManager em) {
        this.honoraires = honoraires;
        this.baux = baux;
        this.tenant = tenant;
        this.audit = audit;
        this.em = em;
    }

    /**
     * Recalcul ciblé des honoraires d'un bien (hook synchrone au pointage). À appeler DANS la
     * transaction de pointage (le contexte tenant y est déjà positionné). Le gel à PAYE protège les
     * honoraires déjà validés.
     *
     * @return le nombre d'honoraires créés/mis à jour.
     */
    @Transactional
    public int recalculerPourBien(UUID bienId) {
        return ((Number) em.createNativeQuery("SELECT calculer_honoraires(CAST(:b AS uuid))")
                .setParameter("b", bienId.toString())
                .getSingleResult()).intValue();
    }

    /**
     * Recalcul complet multi-bailleur (batch). Aucun contexte tenant requis : la fonction
     * {@code SECURITY DEFINER} couvre tous les bailleurs.
     *
     * @return le nombre d'honoraires créés/mis à jour (0 si tout est à jour — idempotence).
     */
    @Transactional
    public int recalculerBatch() {
        return ((Number) em.createNativeQuery("SELECT calculer_honoraires(CAST(NULL AS uuid))")
                .getSingleResult()).intValue();
    }

    @Transactional(readOnly = true)
    public List<HonoraireDto> lister(UUID bienId) {
        tenant.activerDepuisBien(bienId);
        // Approximation documentée (US-93, ADR-13) : Honoraire n'a pas de bailId propre (seulement
        // affectationId, potentiellement mutualisé sur tout un patrimoine). On affiche la devise du
        // bail le plus récent du bien ; un bien ayant changé de devise entre deux baux affichera la
        // devise courante sur des honoraires historiques — accepté (US-93 "Should", risque de
        // périmètre uniquement, cf. backlog EP-11).
        Devise devise = baux.findByBienIdOrderByDateDebutDesc(bienId).stream()
                .findFirst().map(Bail::getDevise).orElse(null);
        return honoraires.findByBien(bienId).stream()
                .map(h -> HonoraireDto.from(h, devise)).toList();
    }

    /**
     * Transition de statut d'un honoraire, réservée au bailleur (validation / paiement, EF-52). Un
     * honoraire déjà {@code PAYE} est figé : toute nouvelle transition est refusée.
     */
    @Transactional
    public HonoraireDto changerStatut(UUID honoraireId, StatutHonoraire nouveau,
            Authentication authentication) {
        UUID bailleurId = tenant.activerDepuisKeycloak(
                ((Jwt) authentication.getPrincipal()).getSubject());
        // Défense en profondeur (en plus de la RLS) : on ne valide QUE ses propres honoraires.
        // Fail-closed en 404 (on ne révèle pas l'existence d'un honoraire d'un autre tenant).
        Honoraire honoraire = honoraires.findById(honoraireId)
                .filter(h -> h.getBailleurId().equals(bailleurId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Honoraire introuvable."));
        if (honoraire.getStatut() == StatutHonoraire.PAYE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un honoraire PAYE est figé et ne peut plus changer de statut.");
        }
        honoraire.changerStatut(nouveau);
        Honoraire enregistre = honoraires.save(honoraire);
        audit.enregistrer(authentication, bailleurId, "VALIDER_HONORAIRE", "honoraire",
                enregistre.getId());
        // Pas de bienId disponible à cet endpoint (résolu depuis honoraireId seul) et une
        // Affectation peut couvrir tout un patrimoine multi-devises : repli EUR (US-93, ADR-13).
        // Sans impact utilisateur : le frontend re-fetch systématiquement la liste complète après
        // ce PATCH et ne consomme jamais le champ devise de cette réponse immédiate.
        return HonoraireDto.from(enregistre, null);
    }
}
