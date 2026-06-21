package com.loyertracker.patrimoine;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patrimoines")
public class PatrimoineController {

    private final PatrimoineService patrimoineService;

    public PatrimoineController(PatrimoineService patrimoineService) {
        this.patrimoineService = patrimoineService;
    }

    @GetMapping
    @PreAuthorize("hasRole('BAILLEUR')")
    public List<PatrimoineDto> lister(@AuthenticationPrincipal Jwt jwt) {
        return patrimoineService.lister(jwt);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BAILLEUR')")
    public PatrimoineDto creer(@Valid @RequestBody PatrimoineRequest requete,
            @AuthenticationPrincipal Jwt jwt) {
        return patrimoineService.creer(jwt, requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederPatrimoine(#id, authentication)")
    public PatrimoineDto renommer(@PathVariable UUID id, @Valid @RequestBody PatrimoineRequest requete,
            @AuthenticationPrincipal Jwt jwt) {
        return patrimoineService.renommer(id, jwt, requete);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederPatrimoine(#id, authentication)")
    public PatrimoineDto archiver(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return patrimoineService.archiver(id, jwt);
    }
}
