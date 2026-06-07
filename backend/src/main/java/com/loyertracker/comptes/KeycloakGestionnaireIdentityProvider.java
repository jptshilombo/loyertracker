package com.loyertracker.comptes;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Adaptateur de production du port {@link GestionnaireIdentityProvider} vers l'Admin API Keycloak
 * (ADR-10), via Spring {@link RestClient} — aucune dépendance ajoutée.
 *
 * <p>Flux : jeton {@code client_credentials} du client confidentiel {@code loyertracker-api} →
 * recherche de l'utilisateur par e-mail ({@code exact=true}) → réutilisation s'il existe (EF-05),
 * sinon création + mot de passe + rôle realm {@code GESTIONNAIRE}.</p>
 *
 * <p><strong>Validation runtime = réserve R6</strong> (avant prod). L'adaptateur n'ouvre aucune
 * connexion au démarrage ; en test, il est remplacé par un double en mémoire.</p>
 */
@Component
public class KeycloakGestionnaireIdentityProvider implements GestionnaireIdentityProvider {

    private static final String ROLE_GESTIONNAIRE = "GESTIONNAIRE";

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakGestionnaireIdentityProvider(
            @Value("${keycloak.admin.base-url:http://keycloak:8080}") String baseUrl,
            @Value("${keycloak.admin.realm:loyertracker}") String realm,
            @Value("${keycloak.admin.client-id:loyertracker-api}") String clientId,
            @Value("${keycloak.admin.client-secret:}") String clientSecret) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public GestionnaireIdentity creerOuRecuperer(String email, String nom, String prenom,
            String motDePasse) {
        try {
            String token = jetonAdmin();
            String existant = chercherUtilisateurParEmail(token, email);
            if (existant != null) {
                return new GestionnaireIdentity(existant, false); // réutilisation (EF-05)
            }
            String keycloakId = creerUtilisateur(token, email, nom, prenom, motDePasse);
            affecterRoleGestionnaire(token, keycloakId);
            return new GestionnaireIdentity(keycloakId, true);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Le fournisseur d'identité est indisponible.", e);
        }
    }

    private String jetonAdmin() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        JsonNode reponse = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        return reponse.get("access_token").asText();
    }

    /** Renvoie le {@code keycloakId} de l'utilisateur portant cet e-mail, ou {@code null}. */
    private String chercherUtilisateurParEmail(String token, String email) {
        JsonNode utilisateurs = http.get()
                .uri(uri -> uri.path("/admin/realms/{realm}/users")
                        .queryParam("email", email)
                        .queryParam("exact", true)
                        .build(realm))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);
        if (utilisateurs != null && utilisateurs.isArray() && !utilisateurs.isEmpty()) {
            return utilisateurs.get(0).get("id").asText();
        }
        return null;
    }

    /** Crée l'utilisateur et renvoie son id (extrait de l'en-tête {@code Location}). */
    private String creerUtilisateur(String token, String email, String nom, String prenom,
            String motDePasse) {
        Map<String, Object> credential = Map.of(
                "type", "password", "value", motDePasse, "temporary", false);
        Map<String, Object> utilisateur = Map.of(
                "username", email,
                "email", email,
                "firstName", prenom,
                "lastName", nom,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(credential));

        HttpHeaders enTetes = http.post()
                .uri("/admin/realms/{realm}/users", realm)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(utilisateur)
                .retrieve()
                .toBodilessEntity()
                .getHeaders();

        String location = enTetes.getFirst(HttpHeaders.LOCATION);
        if (location == null || !location.contains("/users/")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Création du compte gestionnaire : réponse IdP inattendue.");
        }
        return location.substring(location.lastIndexOf("/users/") + "/users/".length());
    }

    private void affecterRoleGestionnaire(String token, String keycloakId) {
        JsonNode role = http.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, ROLE_GESTIONNAIRE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(JsonNode.class);

        Map<String, Object> roleRep = Map.of(
                "id", role.get("id").asText(), "name", role.get("name").asText());

        http.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRep))
                .retrieve()
                .toBodilessEntity();
    }
}
