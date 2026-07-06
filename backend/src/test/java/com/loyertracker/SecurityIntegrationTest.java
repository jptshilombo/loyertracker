package com.loyertracker;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import com.loyertracker.affectations.AffectationService;
import com.loyertracker.alertes.AlerteService;
import com.loyertracker.audit.AuditService;
import com.loyertracker.bailleur.InscriptionService;
import com.loyertracker.bailleur.ProfilService;
import com.loyertracker.batch.GenerationEcheancesService;
import com.loyertracker.baux.BailService;
import com.loyertracker.biens.BienService;
import com.loyertracker.comptes.AcceptationService;
import com.loyertracker.comptes.InvitationService;
import com.loyertracker.documents.QuittanceService;
import com.loyertracker.quittances.QuittanceCertifieeService;
import com.loyertracker.garanties.GarantieService;
import com.loyertracker.honoraires.HonoraireService;
import com.loyertracker.paiements.PaiementService;
import com.loyertracker.patrimoine.PatrimoineService;
import com.loyertracker.patrimoine.TypeBienService;
import com.loyertracker.securite.AuthorizationService;
import com.loyertracker.securite.TenantContext;
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
    @MockitoBean
    InscriptionService inscriptionService;
    // Profil bailleur (V11) : dépend de BailleurRepository (JPA), neutralisé comme les autres.
    @MockitoBean
    ProfilService profilService;
    @MockitoBean
    BienService bienService;
    @MockitoBean
    BailService bailService;
    @MockitoBean
    AffectationService affectationService;
    // Beans dépendant de JPA (EntityManager / repository), exclu du profil `test` : on les neutralise
    // pour garder ce test de contrat de sécurité léger (sans base), comme InscriptionService.
    @MockitoBean
    InvitationService invitationService;
    @MockitoBean
    AcceptationService acceptationService;
    @MockitoBean
    TenantContext tenantContext;
    @MockitoBean
    AuthorizationService authorizationService;
    // Modules financiers S03 (dépendant de JPA) : neutralisés pour ce test de contrat sans BDD.
    @MockitoBean
    PaiementService paiementService;
    @MockitoBean
    GarantieService garantieService;
    @MockitoBean
    GenerationEcheancesService generationEcheancesService;
    // Modules S04 (dépendant de JPA) : neutralisés pour ce test de contrat sans BDD.
    @MockitoBean
    HonoraireService honoraireService;
    @MockitoBean
    AlerteService alerteService;
    @MockitoBean
    AuditService auditService;
    // Documents locatifs (V11, PR2) : dépend des repositories JPA, neutralisé pour ce test sans BDD.
    @MockitoBean
    QuittanceService quittanceService;
    // Quittances certifiées (V22, EP-14) : même neutralisation.
    @MockitoBean
    QuittanceCertifieeService quittanceCertifieeService;
    // Vérification publique (EP-14b, US-102) : dépend de l'EntityManager (JPA), neutralisée comme les autres.
    @MockitoBean
    com.loyertracker.quittances.VerificationQuittanceService verificationQuittanceService;
    // Patrimoine & TypeBien (V12, US-80/81) : dépendent de repositories JPA, neutralisés comme les autres.
    @MockitoBean
    PatrimoineService patrimoineService;
    @MockitoBean
    TypeBienService typeBienService;
    // RGPD (US-70) : dépend de repositories JPA, neutralisé pour ce test de contrat sans BDD.
    @MockitoBean
    com.loyertracker.rgpd.RgpdService rgpdService;

    @Test
    void health_estPublic() throws Exception {
        mockMvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheus_nEstPasBloqueParLaSecurite() throws Exception {
        // Métriques en liste blanche (scrape interne sans jeton, lot 4a — accès public bloqué par
        // Nginx). Le contrat sous test ici est la SÉCURITÉ : une requête anonyme ne doit jamais
        // être rejetée par l'authentification (401). Sans permitAll, `.anyRequest().authenticated()`
        // renverrait 401 avant le dispatcher. Selon le câblage Actuator du contexte la réponse est
        // 200 (endpoint présent) ou 404 (slice sans registre métriques), mais jamais 401 ; la
        // présence réelle des métriques est validée en staging.
        mockMvc.perform(get("/api/actuator/prometheus"))
                .andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
    }

    @Test
    void biens_sansJwt_renvoie401() throws Exception {
        mockMvc.perform(get("/api/biens"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void biens_avecRoleBailleur_renvoie200() throws Exception {
        mockMvc.perform(get("/api/biens")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"))))
                .andExpect(status().isOk());
    }

    @Test
    void biens_avecRoleGestionnaire_renvoie200() throws Exception {
        mockMvc.perform(get("/api/biens")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"))))
                .andExpect(status().isOk());
    }

    @Test
    void biens_jwtSansRoleMetier_renvoie403() throws Exception {
        mockMvc.perform(get("/api/biens").with(jwt()))
                .andExpect(status().isForbidden());
    }
}
