package com.loyertracker.securite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt jwtWithRealmAccess(Map<String, Object> realmAccess) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (realmAccess != null) {
            builder.claim("realm_access", realmAccess);
        } else {
            builder.claim("scope", "openid");
        }
        return builder.build();
    }

    @Test
    void mappe_les_roles_realm_en_autorites_prefixees() {
        Jwt jwt = jwtWithRealmAccess(Map.of("roles", List.of("BAILLEUR", "GESTIONNAIRE")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_BAILLEUR", "ROLE_GESTIONNAIRE");
    }

    @Test
    void renvoie_vide_sans_claim_realm_access() {
        assertThat(converter.convert(jwtWithRealmAccess(null))).isEmpty();
    }

    @Test
    void renvoie_vide_si_realm_access_sans_roles() {
        assertThat(converter.convert(jwtWithRealmAccess(Map.of()))).isEmpty();
    }
}
