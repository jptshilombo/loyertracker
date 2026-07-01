package com.loyertracker.bailleur;

import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.patrimoine.Patrimoine;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Inscription d'un bailleur (US-10) : crée l'enregistrement applicatif rattaché au compte Keycloak
 * du porteur du JWT. Self-service — un utilisateur ne peut inscrire que <em>lui-même</em>
 * (l'identité provient du jeton, jamais du corps de la requête).
 *
 * <p><strong>Interaction RLS (ADR-01).</strong> La policy {@code bailleur_isolation} autorise une
 * ligne {@code bailleur} seulement si {@code id = app.current_bailleur_id} (clause {@code WITH CHECK}
 * pour l'INSERT). On génère donc l'identifiant côté application, on positionne le contexte tenant
 * sur cet id <em>dans la transaction</em> (via {@code set_config(..., is_local := true)}, donc même
 * connexion que l'INSERT), puis on insère. L'opération est ainsi auto-autorisante.</p>
 *
 * <p>La RLS masquant les lignes des autres tenants, on ne peut pas vérifier au préalable l'existence
 * d'un compte par {@code keycloakId} : l'idempotence repose sur la contrainte {@code UNIQUE} en base
 * (clé Keycloak / email), traduite en {@link DejaInscritException} (409).</p>
 */
@Service
public class InscriptionService {

    private final EntityManager em;

    public InscriptionService(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public Bailleur inscrire(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String email = requireClaim(jwt, "email");
        String prenom = firstNonBlank(jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("preferred_username"), "—");
        String nom = firstNonBlank(jwt.getClaimAsString("family_name"), "—");

        UUID id = UUID.randomUUID();
        // Contexte tenant local à la transaction : la policy WITH CHECK n'autorise que SA ligne.
        em.createNativeQuery("SELECT set_config('app.current_bailleur_id', :id, true)")
                .setParameter("id", id.toString())
                .getSingleResult();

        Bailleur bailleur = new Bailleur(id, keycloakId, email, nom, prenom);
        try {
            em.persist(bailleur);
            em.flush(); // déclenche l'INSERT (et l'éventuelle violation d'unicité) dans cette transaction
        } catch (PersistenceException e) {
            if (estViolationUnicite(e)) {
                throw new DejaInscritException("Ce compte est déjà inscrit comme bailleur.");
            }
            throw e;
        }
        // Patrimoine par défaut (Hotfix 2026-06-24) : sans cela, un nouveau bailleur n'a aucun
        // patrimoine et ne peut créer aucun bien (Bien.patrimoineId est NOT NULL depuis V12).
        // Même contexte RLS que l'INSERT bailleur ci-dessus (même transaction). Adresse
        // placeholder (ADR-12/US-90, adresse désormais obligatoire) — à corriger par le
        // bailleur via PUT /api/patrimoines/{id}.
        em.persist(new Patrimoine(UUID.randomUUID(), id, "Patrimoine principal", "Adresse à renseigner"));
        return bailleur;
    }

    private static String requireClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le jeton ne contient pas le claim requis : " + claim);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "—";
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
