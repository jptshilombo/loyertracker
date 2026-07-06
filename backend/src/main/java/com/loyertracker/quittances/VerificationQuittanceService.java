package com.loyertracker.quittances;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

/**
 * Vérification publique et téléchargement officiel d'une quittance certifiée (US-102/104, ADR-15
 * D5/D7). Chemin <strong>non authentifié</strong> : la RLS ne s'applique pas ici, l'accès passe
 * exclusivement par les fonctions {@code SECURITY DEFINER} de V22 (propriété {@code loyertracker_batch}
 * BYPASSRLS), et l'autorisation repose uniquement sur le token HMAC vérifié côté application.
 *
 * <p><strong>Absence d'oracle</strong> : toute cause d'échec (identifiant inconnu, token invalide,
 * PDF altéré en base) produit le même résultat côté appelant — {@code Optional.empty()}. Le token
 * est comparé en temps constant ({@link TokenQuittanceService}).</p>
 *
 * <p><strong>Non-fuite K2</strong> : la projection publique est reconstruite en ne lisant que les
 * champs autorisés du contenu certifié — {@code paiement.mode} et {@code garantie_retenue} ne sont
 * jamais lus (voir {@link #projeterK2}).</p>
 */
@Service
public class VerificationQuittanceService {

    private static final String TYPE_VERIFICATION = "VERIFICATION";
    private static final String TYPE_TELECHARGEMENT = "TELECHARGEMENT";
    private static final String RESULTAT_VALIDE = "VALIDE";
    private static final String RESULTAT_INVALIDE = "INVALIDE";

    private final EntityManager em;
    private final TokenQuittanceService tokens;
    private final QuittanceMetrics metrics;
    private final ObjectMapper mapper;

    public VerificationQuittanceService(EntityManager em, TokenQuittanceService tokens,
            QuittanceMetrics metrics, ObjectMapper mapper) {
        this.em = em;
        this.tokens = tokens;
        this.metrics = metrics;
        this.mapper = mapper;
    }

    /**
     * Vérifie une quittance présentée par la page publique. Journalise l'événement (RGPD-minimal)
     * et incrémente les métriques, puis renvoie la projection K2 si — et seulement si — le token
     * correspond exactement au triplet certifié {@code (id, version, content_hash)}.
     */
    @Transactional
    public Optional<PublicReceiptDto> verifier(UUID id, String token) {
        Ligne ligne = lireLigne(id);
        if (ligne == null || !tokens.verifier(token, id, ligne.version(), ligne.contentHash())) {
            journaliser(ligne == null ? null : id, TYPE_VERIFICATION, RESULTAT_INVALIDE);
            metrics.verification(false);
            return Optional.empty();
        }
        PublicReceiptDto dto = projeterK2(ligne);
        journaliser(id, TYPE_VERIFICATION, RESULTAT_VALIDE);
        metrics.verification(true);
        return Optional.of(dto);
    }

    /**
     * Sert l'exemplaire officiel (PDF) si le token est valide <em>et</em> si le PDF stocké est
     * intègre — son SHA-256 recalculé doit égaler {@code pdf_hash} (défense contre une altération
     * en base). Toute défaillance renvoie {@code Optional.empty()} (404 indifférencié côté HTTP).
     */
    @Transactional
    public Optional<byte[]> telecharger(UUID id, String token) {
        Ligne ligne = lireLigne(id);
        if (ligne == null || !tokens.verifier(token, id, ligne.version(), ligne.contentHash())) {
            journaliser(ligne == null ? null : id, TYPE_TELECHARGEMENT, RESULTAT_INVALIDE);
            metrics.telechargement(false);
            return Optional.empty();
        }
        Object[] pdfRow = premiereLigne("""
                SELECT pdf, pdf_hash FROM lire_pdf_quittance_publique(CAST(:id AS uuid))
                """, id);
        if (pdfRow == null) {
            journaliser(id, TYPE_TELECHARGEMENT, RESULTAT_INVALIDE);
            metrics.telechargement(false);
            return Optional.empty();
        }
        byte[] pdf = (byte[]) pdfRow[0];
        String pdfHash = (String) pdfRow[1];
        if (pdf == null || !ContenuQuittance.sha256Hex(pdf).equals(pdfHash)) {
            journaliser(id, TYPE_TELECHARGEMENT, RESULTAT_INVALIDE);
            metrics.telechargement(false);
            return Optional.empty();
        }
        journaliser(id, TYPE_TELECHARGEMENT, RESULTAT_VALIDE);
        metrics.telechargement(true);
        return Optional.of(pdf);
    }

    private Ligne lireLigne(UUID id) {
        Object[] row = premiereLigne("""
                SELECT version, statut, contenu, content_hash, remplacante_numero, remplacante_version
                FROM lire_quittance_publique(CAST(:id AS uuid))
                """, id);
        if (row == null) {
            return null;
        }
        return new Ligne(
                ((Number) row[0]).intValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                row[5] == null ? null : ((Number) row[5]).intValue());
    }

    /**
     * Reconstruit le contrat public strict (K2) à partir du <em>contenu certifié</em>. Les seuls
     * champs lus sont ceux autorisés en public : le bloc {@code paiement} (mode + garantie retenue)
     * du payload canonique n'est délibérément jamais consulté.
     */
    private PublicReceiptDto projeterK2(Ligne ligne) {
        JsonNode c;
        try {
            c = mapper.readTree(ligne.contenu());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Contenu certifié illisible.", e);
        }
        JsonNode montants = c.path("montants");
        return new PublicReceiptDto(
                c.path("numero").asText(),
                c.path("version").asInt(),
                ligne.statut(),
                c.path("bailleur").path("nom").asText(),
                c.path("bailleur").path("adresse").asText(),
                c.path("locataire").path("nom").asText(),
                c.path("patrimoine").path("nom").asText(),
                c.path("bien").path("adresse").asText(),
                c.path("periode").path("code").asText(),
                c.path("periode").path("libelle").asText(),
                montants.path("devise").asText(),
                montants.path("loyer_hc").asText(),
                montants.path("provision_charges").asText(),
                montants.path("loyer_cc").asText(),
                montants.path("montant_recu").asText(),
                c.path("emise_le").asText(),
                ligne.contentHash(),
                ligne.remplacanteNumero(),
                ligne.remplacanteVersion());
    }

    private void journaliser(UUID quittanceOuNull, String type, String resultat) {
        String cible = quittanceOuNull == null ? "NULL" : "CAST(:id AS uuid)";
        var query = em.createNativeQuery(
                "SELECT journaliser_evenement_quittance(" + cible + ", :type, :resultat)")
                .setParameter("type", type)
                .setParameter("resultat", resultat);
        if (quittanceOuNull != null) {
            query.setParameter("id", quittanceOuNull.toString());
        }
        query.getSingleResult();
    }

    private Object[] premiereLigne(String sql, UUID id) {
        var rows = em.createNativeQuery(sql).setParameter("id", id.toString()).getResultList();
        return rows.isEmpty() ? null : (Object[]) rows.get(0);
    }

    private record Ligne(int version, String statut, String contenu, String contentHash,
            String remplacanteNumero, Integer remplacanteVersion) {
    }
}
