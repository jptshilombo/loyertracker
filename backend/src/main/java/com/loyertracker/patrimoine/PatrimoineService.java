package com.loyertracker.patrimoine;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.loyertracker.securite.TenantContext;

@Service
public class PatrimoineService {

    private final PatrimoineRepository patrimoines;
    private final TenantContext tenant;

    public PatrimoineService(PatrimoineRepository patrimoines, TenantContext tenant) {
        this.patrimoines = patrimoines;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true)
    public List<PatrimoineDto> lister(Jwt jwt) {
        UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());
        return patrimoines.findByBailleurIdOrderByNomAsc(bailleurId).stream()
                .map(PatrimoineDto::from)
                .toList();
    }

    @Transactional
    public PatrimoineDto creer(Jwt jwt, PatrimoineRequest requete) {
        UUID bailleurId = tenant.activerDepuisKeycloak(jwt.getSubject());
        Patrimoine patrimoine = new Patrimoine(UUID.randomUUID(), bailleurId, requete.nom());
        return PatrimoineDto.from(patrimoines.saveAndFlush(patrimoine));
    }

    @Transactional
    public PatrimoineDto renommer(UUID id, Jwt jwt, PatrimoineRequest requete) {
        tenant.activerDepuisKeycloak(jwt.getSubject());
        Patrimoine patrimoine = patrimoines.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Patrimoine introuvable."));
        patrimoine.renommer(requete.nom());
        return PatrimoineDto.from(patrimoine);
    }

    @Transactional
    public PatrimoineDto archiver(UUID id, Jwt jwt) {
        tenant.activerDepuisKeycloak(jwt.getSubject());
        Patrimoine patrimoine = patrimoines.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Patrimoine introuvable."));
        patrimoine.archiver();
        return PatrimoineDto.from(patrimoine);
    }
}
