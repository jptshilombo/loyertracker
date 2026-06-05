package com.loyertracker.securite;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Convertit le claim Keycloak {@code realm_access.roles} d'un JWT en autorités Spring
 * Security préfixées {@code ROLE_} (ex. {@code BAILLEUR} → {@code ROLE_BAILLEUR}).
 *
 * <p>ADR-02 : Keycloak ne porte que le RBAC grossier (rôles realm). L'autorisation fine
 * par bien est traitée applicativement ({@link AuthorizationService}) puis par la RLS.</p>
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(Object::toString)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
