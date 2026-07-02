package com.loyertracker.s03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import com.loyertracker.testsupport.RlsTestDataSourceConfig;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/**
 * Tests d'intégration S03 — paiements (US-30/31) & garanties (US-32). Couvre la génération à terme
 * échu (Annexe A.3) et son idempotence, le pointage, le cycle de vie de la garantie (A.5),
 * l'autorisation cross-bailleur / cross-affectation (DoD) et la journalisation d'audit (BNF-05).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(RlsTestDataSourceConfig.class)
class S03PaiementsGarantiesIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    @Qualifier("admin")
    JdbcTemplate jdbc;

    @BeforeEach
    void nettoyerBase() {
        jdbc.execute("""
                TRUNCATE audit_log, garantie, paiement, affectation, bail, bien, patrimoine,
                         invitation, bailleur, gestionnaire
                RESTART IDENTITY CASCADE
                """);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Seule l'URL est dynamique : datasource applicatif sous loyertracker_api (creds statiques
        // dans application.properties), Flyway en admin. On ne surcharge plus username/password.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "https://localhost/auth/realms/loyertracker");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:0/realms/loyertracker/protocol/openid-connect/certs");
    }

    @Test
    void echeancesGenereesATermeEchuEtIdempotentes() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "1 rue des Loyers");
        // Bail de 3 mois civils consommés : 2026-01, 2026-02, 2026-03.
        creerBail(bailleur, bienId, "2026-01-01", "2026-03-31");

        // 1er passage : 3 échéances créées (mois début → mois terme, sans prorata). Le déclenchement
        // enchaîne génération PUIS marquage : ces 3 mois sont échus → 3 loyers passés EN_RETARD.
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancesCreees").value(3))
                .andExpect(jsonPath("$.loyersEnRetard").value(3));

        // 2e passage : idempotent (uq_paiement_periode) → 0 créée ; aucun IMPAYE restant → 0 retard.
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancesCreees").value(0))
                .andExpect(jsonPath("$.loyersEnRetard").value(0));

        // La plus récente période (tri desc) = 2026-03, exigible le 1er du mois suivant, montant = loyer CC.
        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].periode").value("2026-03"))
                .andExpect(jsonPath("$[0].dateExigibilite").value("2026-04-01"))
                .andExpect(jsonPath("$[0].montantAttendu").value(850.00))
                .andExpect(jsonPath("$[0].statut").value("EN_RETARD"))
                .andExpect(jsonPath("$[0].devise").value("EUR"))
                .andExpect(jsonPath("$[2].periode").value("2026-01"));
    }

    @Test
    void paiementsPortentLaDeviseDuBailParent() throws Exception {
        // US-93 (ADR-13) : la devise affichée sur un paiement est celle du bail parent, résolue
        // sans duplication en base.
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "9 rue Devise");
        creerBail(bailleur, bienId, "2026-01-01", "2026-01-31", "USD");
        genererEcheances(bailleur);

        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienId).with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].devise").value("USD"));

        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devise").value("USD"));
    }

    @Test
    void pointageRecuInsuffisantRefuse() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "7 rue Coherence");
        creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");
        genererEcheances(bailleur);

        // RECU avec montant reçu < attendu : incohérent → 400 (symétrique au contrôle PARTIEL).
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "100.00", "RECU"))
                .andExpect(status().isBadRequest());
        // RECU avec montant reçu = attendu : accepté, reste dû nul.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resteDu").value(0.00));
    }

    @Test
    void loyersEchusBasculentEnRetardSansToucherLesPointages() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "8 rue Retard");
        creerBail(bailleur, bienId, "2026-01-01", "2026-02-28");

        // Génération + marquage en un seul déclenchement : 2 échéances échues → 2 EN_RETARD.
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancesCreees").value(2))
                .andExpect(jsonPath("$.loyersEnRetard").value(2));
        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienId).with(bailleurJwt(bailleur)))
                .andExpect(jsonPath("$[0].statut").value("EN_RETARD"))
                .andExpect(jsonPath("$[1].statut").value("EN_RETARD"));

        // Un loyer pointé RECU ne doit pas être ré-altéré par un nouveau passage du batch.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk());

        // 2e passage : rien à créer ; les échus sont déjà EN_RETARD, le RECU est ignoré → 0 bascule.
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(bailleur)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancesCreees").value(0))
                .andExpect(jsonPath("$.loyersEnRetard").value(0));
        // Tri desc : [0] = 2026-02 (EN_RETARD), [1] = 2026-01 (RECU, préservé).
        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienId).with(bailleurJwt(bailleur)))
                .andExpect(jsonPath("$[0].statut").value("EN_RETARD"))
                .andExpect(jsonPath("$[1].statut").value("RECU"));
    }

    @Test
    void pointageLoyerRecuPartielEtControles() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "2 rue Pointage");
        creerBail(bailleur, bienId, "2026-01-01", "2026-02-28");
        genererEcheances(bailleur);

        // Pointage RECU : reste dû nul.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECU"))
                .andExpect(jsonPath("$.resteDu").value(0.00));

        // Pointage PARTIEL : reste dû = attendu - reçu.
        mockMvc.perform(pointer(bienId, "2026-02", bailleur, "300.00", "PARTIEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PARTIEL"))
                .andExpect(jsonPath("$.resteDu").value(550.00));

        // PARTIEL incohérent (reçu >= attendu) → 400.
        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "PARTIEL"))
                .andExpect(status().isBadRequest());

        // Période inexistante → 404.
        mockMvc.perform(pointer(bienId, "2030-12", bailleur, "10.00", "RECU"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cycleVieGarantie() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "3 rue Caution");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-12-31");

        String garantieId = JsonPath.read(mockMvc.perform(
                        post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                                .with(bailleurJwt(bailleur))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"montant\":850.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("DETENU"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // Restitution partielle avec retenue + motif → RESTITUE_PARTIEL.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur,
                        "{\"type\":\"PARTIELLE\",\"montantRetenu\":100.00,\"motifRetenue\":\"Dégâts\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RESTITUE_PARTIEL"))
                .andExpect(jsonPath("$.montantRetenu").value(100.00));

        // Puis restitution totale → RESTITUE_TOTAL.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur, "{\"type\":\"TOTALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RESTITUE_TOTAL"));

        // Une garantie déjà totalement restituée ne peut plus l'être → 409.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur, "{\"type\":\"TOTALE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ledgerGarantieEnregistreChaqueMouvementEtSoldeCoherent() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "7 rue Ledger");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-12-31");

        String garantieId = JsonPath.read(mockMvc.perform(
                        post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                                .with(bailleurJwt(bailleur))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"montant\":900.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soldeActuel").value(900.00))
                .andReturn().getResponse().getContentAsString(), "$.id");

        assertThat(soldeActuel(garantieId)).isEqualByComparingTo("900.00");
        assertThat((BigDecimal) mouvementUnique(garantieId, "DEPOT_INITIAL").get("credit"))
                .isEqualByComparingTo("900.00");
        assertThat(compterAudit("DEPOT_INITIAL")).isEqualTo(1);

        // Restitution partielle : retenue 150 -> solde 750, mouvement AJUSTEMENT débit 150
        // (ADR-14 : type précis de retenue non exposé avant US-95, Sprint 10).
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur,
                        "{\"type\":\"PARTIELLE\",\"montantRetenu\":150.00,\"motifRetenue\":\"Nettoyage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldeActuel").value(750.00));

        assertThat(soldeActuel(garantieId)).isEqualByComparingTo("750.00");
        assertThat((BigDecimal) mouvementUnique(garantieId, "AJUSTEMENT").get("debit"))
                .isEqualByComparingTo("150.00");
        assertThat(compterAudit("AJUSTEMENT")).isEqualTo(1);

        // Restitution totale : solde résiduel (750) intégralement débité -> 0, RESTITUTION.
        mockMvc.perform(restituer(bienId, bailId, garantieId, bailleur, "{\"type\":\"TOTALE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soldeActuel").value(0));

        assertThat(soldeActuel(garantieId)).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) mouvementUnique(garantieId, "RESTITUTION").get("debit"))
                .isEqualByComparingTo("750.00");
        assertThat(compterAudit("RESTITUTION")).isEqualTo(1);

        // Invariant du ledger (critère GO Sprint 9) : solde == somme(credit) - somme(debit).
        BigDecimal sommeCredit = jdbc.queryForObject(
                "SELECT coalesce(sum(credit),0) FROM garantie_movement WHERE garantie_id = ?::uuid",
                BigDecimal.class, garantieId);
        BigDecimal sommeDebit = jdbc.queryForObject(
                "SELECT coalesce(sum(debit),0) FROM garantie_movement WHERE garantie_id = ?::uuid",
                BigDecimal.class, garantieId);
        assertThat(sommeCredit.subtract(sommeDebit)).isEqualByComparingTo(soldeActuel(garantieId));
    }

    @Test
    void accesFinancierRefuseCrossBailleur() throws Exception {
        String bailleurA = "kc-" + UUID.randomUUID();
        String bailleurB = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleurA);
        inscrireBailleur(bailleurB);
        String bienA = creerBien(bailleurA, "4 rue Privee");
        String bailA = creerBail(bailleurA, bienA, "2026-01-01", "2026-06-30");
        genererEcheances(bailleurA);

        // Bailleur B n'a aucun droit sur le bien de A : ReBAC refuse (403).
        mockMvc.perform(pointer(bienA, "2026-01", bailleurB, "850.00", "RECU"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/biens/{bienId}/paiements", bienA).with(bailleurJwt(bailleurB)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/biens/{bienId}/baux/{bailId}/garanties", bienA, bailA)
                        .with(bailleurJwt(bailleurB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":1.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestionnaireAffecteActifPuisRevoque() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "5 rue Delegation");
        creerBail(bailleur, bienId, "2026-01-01", "2026-03-31");
        genererEcheances(bailleur);
        UUID gestionnaire = insererGestionnaire("kc-g-" + UUID.randomUUID(), "g@test.local");
        String affectationId = affecter(bailleur, bienId, gestionnaire);
        String gJwt = keycloakId(gestionnaire);

        // Gestionnaire affecté actif : peut pointer.
        mockMvc.perform(pointer(bienId, "2026-01", null, "850.00", "RECU").with(gestionnaireJwt(gJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RECU"));

        // Après révocation : accès refusé (403).
        mockMvc.perform(post("/api/affectations/{id}/revocation", affectationId)
                        .with(bailleurJwt(bailleur)))
                .andExpect(status().isOk());
        mockMvc.perform(pointer(bienId, "2026-02", null, "850.00", "RECU").with(gestionnaireJwt(gJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ecrituresFinancieresJournalisees() throws Exception {
        String bailleur = "kc-" + UUID.randomUUID();
        inscrireBailleur(bailleur);
        String bienId = creerBien(bailleur, "6 rue Audit");
        String bailId = creerBail(bailleur, bienId, "2026-01-01", "2026-01-31");
        genererEcheances(bailleur);

        mockMvc.perform(pointer(bienId, "2026-01", bailleur, "850.00", "RECU"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/biens/{bienId}/baux/{bailId}/garanties", bienId, bailId)
                        .with(bailleurJwt(bailleur))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":850.00,\"typeGarantie\":\"CAUTION\",\"dateDepot\":\"2026-01-01\"}"))
                .andExpect(status().isCreated());

        assertThat(compterAudit("POINTER_PAIEMENT")).isEqualTo(1);
        assertThat(compterAudit("CREATE_GARANTIE")).isEqualTo(1);
    }

    // ---- helpers --------------------------------------------------------------------

    private void inscrireBailleur(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/bailleurs/inscription").with(bailleurJwt(keycloakId)))
                .andExpect(status().isCreated());
    }

    private String creerBien(String keycloakId, String adresse) throws Exception {
        String patrimoineId = creerPatrimoine(keycloakId, "Patrimoine " + adresse);
        return JsonPath.read(mockMvc.perform(post("/api/biens")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adresse\":\"" + adresse + "\",\"type\":\"APPARTEMENT\",\"statut\":\"LIBRE\","
                                + "\"patrimoineId\":\"" + patrimoineId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerPatrimoine(String keycloakId, String nom) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/patrimoines")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"" + nom + "\",\"adresse\":\"1 rue du " + nom + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String creerBail(String keycloakId, String bienId, String debut, String fin)
            throws Exception {
        return creerBail(keycloakId, bienId, debut, fin, "EUR");
    }

    private String creerBail(String keycloakId, String bienId, String debut, String fin, String devise)
            throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/biens/{bienId}/baux", bienId)
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locataireNom\":\"Locataire\",\"locataireEmail\":\"loc@test.local\","
                                + "\"loyerHc\":850.00,\"provisionCharges\":0.00,\"dateDebut\":\""
                                + debut + "\",\"dateFin\":\"" + fin + "\",\"devise\":\"" + devise + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void genererEcheances(String keycloakId) throws Exception {
        mockMvc.perform(post("/api/batch/echeances").with(bailleurJwt(keycloakId)))
                .andExpect(status().isOk());
    }

    private String affecter(String keycloakId, String bienId, UUID gestionnaireId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/affectations")
                        .with(bailleurJwt(keycloakId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bienId\":\"" + bienId + "\",\"gestionnaireId\":\"" + gestionnaireId
                                + "\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":10.00,"
                                + "\"dateDebut\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pointer(
            String bienId, String periode, String bailleurKc, String montantRecu, String statut) {
        var builder = patch("/api/biens/{bienId}/paiements/{periode}/pointage", bienId, periode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantRecu\":" + montantRecu + ",\"statut\":\"" + statut + "\"}");
        return bailleurKc == null ? builder : builder.with(bailleurJwt(bailleurKc));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder restituer(
            String bienId, String bailId, String garantieId, String bailleurKc, String body) {
        return post("/api/biens/{bienId}/baux/{bailId}/garanties/{gid}/restitution",
                bienId, bailId, garantieId)
                .with(bailleurJwt(bailleurKc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private UUID insererGestionnaire(String keycloakId, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO gestionnaire (id, keycloak_id, email, nom, prenom) VALUES (?,?,?,?,?)",
                id, keycloakId, email, "Martin", "Bob");
        return id;
    }

    private String keycloakId(UUID gestionnaireId) {
        return jdbc.queryForObject("SELECT keycloak_id FROM gestionnaire WHERE id = ?", String.class,
                gestionnaireId);
    }

    private Integer compterAudit(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = ?", Integer.class,
                action);
    }

    private BigDecimal soldeActuel(String garantieId) {
        return jdbc.queryForObject("SELECT solde_actuel FROM garantie WHERE id = ?::uuid",
                BigDecimal.class, garantieId);
    }

    private Map<String, Object> mouvementUnique(String garantieId, String type) {
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "SELECT * FROM garantie_movement WHERE garantie_id = ?::uuid AND type = ?",
                garantieId, type);
        assertThat(lignes).hasSize(1);
        return lignes.get(0);
    }

    private static JwtRequestPostProcessor bailleurJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token.subject(keycloakId)
                        .claim("email", keycloakId + "@test.local")
                        .claim("given_name", "Alice")
                        .claim("family_name", "Durand"))
                .authorities(new SimpleGrantedAuthority("ROLE_BAILLEUR"));
    }

    private static JwtRequestPostProcessor gestionnaireJwt(String keycloakId) {
        return jwt()
                .jwt(token -> token.subject(keycloakId))
                .authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE"));
    }
}
