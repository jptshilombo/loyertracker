package com.loyertracker.bailleur;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.securite.TenantContext;

/**
 * Consultation et mise à jour du profil du bailleur courant (V11). L'adresse postale renseignée ici
 * alimente les mentions obligatoires de la quittance de loyer.
 *
 * <p>Cloisonnement RLS (ADR-01) : on positionne le contexte tenant depuis l'identité Keycloak du
 * jeton ({@link TenantContext#activerDepuisKeycloak(String)}) avant tout accès JPA. La lecture/écriture
 * ne portent donc que sur la ligne {@code bailleur} du porteur du jeton.</p>
 */
@Service
public class ProfilService {

    private final TenantContext tenant;
    private final BailleurRepository bailleurs;

    public ProfilService(TenantContext tenant, BailleurRepository bailleurs) {
        this.tenant = tenant;
        this.bailleurs = bailleurs;
    }

    @Transactional(readOnly = true)
    public BailleurDto consulter(String keycloakId) {
        UUID id = tenant.activerDepuisKeycloak(keycloakId);
        return BailleurDto.from(charger(id));
    }

    @Transactional
    public BailleurDto mettreAJour(String keycloakId, ProfilRequest requete) {
        UUID id = tenant.activerDepuisKeycloak(keycloakId);
        Bailleur bailleur = charger(id);
        bailleur.setAdresse(requete.adresse().trim());
        // Entité gérée : le flush en fin de transaction persiste la modification (dirty checking).
        return BailleurDto.from(bailleur);
    }

    private Bailleur charger(UUID id) {
        return bailleurs.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Profil bailleur introuvable."));
    }
}
