package com.loyertracker.securite;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuration de sécurité (ADR-01 / ADR-02).
 *
 * <ul>
 *   <li>Stateless : aucune session, authentification par JWT (Bearer) émis par Keycloak.</li>
 *   <li>Rôles extraits du claim {@code realm_access.roles} ({@link KeycloakRealmRoleConverter}).</li>
 *   <li>Liste blanche : health/info/prometheus Actuator et l'acceptation d'invitation (non authentifiée).</li>
 *   <li>CORS limité à l'origine de la SPA ; CSRF désactivé (API stateless à jeton).</li>
 *   <li>{@code @EnableMethodSecurity} active {@code @PreAuthorize} pour l'autorisation fine.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${app.cors.allowed-origin:https://localhost}")
    private String allowedOrigin;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // API OAuth2 Resource Server strictement stateless : authentification uniquement par
            // Bearer JWT dans Authorization, aucun cookie de session ni credential implicite. Un
            // navigateur tiers ne peut donc pas fabriquer une requête authentifiée par CSRF. Cette
            // exception est revue dans le plan de remédiation CGPA v5.4.1 (alerte CodeQL #1).
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/actuator/health", "/api/actuator/health/**", "/api/actuator/info").permitAll()
                // Métriques Prometheus : exposition interne uniquement (lot Production Readiness 4a).
                // Le port 8080 n'est pas publié sur l'hôte et Nginx renvoie 404 publiquement
                // (cf. infra/nginx/nginx.conf) : seul un scrapeur du réseau Docker y accède, sans jeton.
                .requestMatchers("/api/actuator/prometheus").permitAll()
                // Acceptation d'invitation via lien tokenisé : pas encore de compte → non authentifié.
                .requestMatchers(HttpMethod.POST, "/api/invitations/*/acceptation").permitAll()
                // Vérification publique des quittances certifiées (US-102, ADR-15 D5) : accès sans
                // compte, autorisé par le seul token HMAC du QR (vérifié applicativement) ; GET seul,
                // lecture via fonctions SECURITY DEFINER, réponses indifférenciées.
                .requestMatchers(HttpMethod.GET, "/api/public/receipts/*",
                        "/api/public/receipts/*/download").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
