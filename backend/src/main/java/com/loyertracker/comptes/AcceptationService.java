package com.loyertracker.comptes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loyertracker.securite.TenantContext;

/**
 * Acceptation d'une invitation (US-12, EF-04/05).
 *
 * <p>Flux non authentifié porté par le token (capacité) : on résout le tenant émetteur (ADR-09),
 * on valide l'invitation (existante, non consommée, non expirée — usage unique), puis on crée ou
 * réutilise le compte gestionnaire dans l'IdP (port {@link GestionnaireIdentityProvider}, ADR-10)
 * et l'enregistrement applicatif. Toute création hors invitation valide est de fait impossible.</p>
 */
@Service
public class AcceptationService {

    private final InvitationRepository invitations;
    private final GestionnaireRepository gestionnaires;
    private final GestionnaireIdentityProvider idp;
    private final TenantContext tenant;

    public AcceptationService(InvitationRepository invitations, GestionnaireRepository gestionnaires,
            GestionnaireIdentityProvider idp, TenantContext tenant) {
        this.invitations = invitations;
        this.gestionnaires = gestionnaires;
        this.idp = idp;
        this.tenant = tenant;
    }

    @Transactional
    public AcceptationDto accepter(String token, AcceptationRequest requete) {
        // 1. Résolution du tenant émetteur → contexte RLS, sinon token inconnu.
        if (tenant.activerDepuisInvitation(token) == null) {
            throw new InvitationIntrouvableException("Invitation introuvable.");
        }
        Invitation invitation = invitations.findByToken(token)
                .orElseThrow(() -> new InvitationIntrouvableException("Invitation introuvable."));

        // 2. Validation : usage unique + fenêtre de validité (EF-03).
        if (invitation.estConsommee()) {
            throw new InvitationNonAcceptableException("Cette invitation a déjà été utilisée.");
        }
        if (invitation.estExpiree(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvitationNonAcceptableException("Cette invitation a expiré.");
        }

        // 3. Création / réutilisation du compte IdP (EF-05), puis de l'enregistrement applicatif.
        GestionnaireIdentity identite = idp.creerOuRecuperer(
                invitation.getEmail(), requete.nom(), requete.prenom(), requete.motDePasse());

        Gestionnaire gestionnaire = gestionnaires.findByKeycloakId(identite.keycloakId())
                .orElseGet(() -> gestionnaires.save(new Gestionnaire(
                        UUID.randomUUID(), identite.keycloakId(),
                        invitation.getEmail(), requete.nom(), requete.prenom())));

        // 4. Consommation de l'invitation (usage unique).
        invitation.marquerAcceptee();

        return AcceptationDto.from(gestionnaire, identite.cree());
    }
}
