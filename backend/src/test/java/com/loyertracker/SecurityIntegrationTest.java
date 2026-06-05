package com.loyertracker;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie le contrat de sécurité de l'étape 04 :
 * health public, /api/biens protégé, matrice de rôles (200 BAILLEUR/GESTIONNAIRE, 401 anonyme, 403 sans rôle).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void health_estPublic() throws Exception {
        mockMvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void biens_sansJwt_renvoie401() throws Exception {
        mockMvc.perform(get("/api/biens"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void biens_avecRoleBailleur_renvoie200ListeVide() throws Exception {
        mockMvc.perform(get("/api/biens")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void biens_avecRoleGestionnaire_renvoie200ListeVide() throws Exception {
        mockMvc.perform(get("/api/biens")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void biens_jwtSansRoleMetier_renvoie403() throws Exception {
        mockMvc.perform(get("/api/biens").with(jwt()))
                .andExpect(status().isForbidden());
    }
}
