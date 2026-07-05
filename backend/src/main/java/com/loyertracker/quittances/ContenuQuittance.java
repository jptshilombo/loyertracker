package com.loyertracker.quittances;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.loyertracker.baux.Money;

/**
 * Payload canonique d'une quittance certifiée (ADR-15 D2). Le JSON est construit manuellement,
 * champ par champ dans un ordre fixe, sans bibliothèque de sérialisation : le résultat doit être
 * <strong>déterministe octet à octet</strong> — c'est la chaîne exacte stockée en
 * {@code quittance.contenu}, et {@code content_hash = SHA-256(contenu)} est re-calculable par
 * quiconque à partir du contenu affiché sur la page de vérification.
 *
 * <p>Le champ {@code schema} versionne le format : toute évolution du payload devra incrémenter
 * ce numéro (les contenus déjà certifiés restent vérifiables tels quels, jamais re-sérialisés).</p>
 */
public final class ContenuQuittance {

    private ContenuQuittance() {
        // utilitaire statique
    }

    /** JSON canonique complet — la chaîne certifiée (une par version de quittance). */
    public static String canonique(DonneesQuittanceCertifiee d) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\"schema\":1");
        champ(json, "numero", d.numero());
        json.append(",\"version\":").append(d.version());
        champ(json, "emise_le", d.dateEmission().toString());
        json.append(",\"bailleur\":{");
        json.append("\"nom\":\"").append(esc(d.bailleurNom())).append('"');
        champ(json, "adresse", d.bailleurAdresse());
        json.append("},\"locataire\":{\"nom\":\"").append(esc(d.locataireNom())).append("\"}");
        json.append(",\"patrimoine\":{\"nom\":\"").append(esc(d.patrimoineNom())).append("\"}");
        json.append(",\"bien\":{\"adresse\":\"").append(esc(d.bienAdresse())).append("\"}");
        json.append(",\"periode\":{\"code\":\"").append(esc(d.periode())).append('"');
        champ(json, "libelle", d.periodeLibelle());
        json.append("},").append(blocMontants(d));
        json.append('}');
        return json.toString();
    }

    /**
     * Empreinte des seules données métier (hors numéro, version et date d'émission) : sert à
     * l'idempotence de la ré-émission (US-99) — redemander la quittance d'un loyer inchangé
     * renvoie l'exemplaire officiel existant ; toute différence métier produit une version N+1.
     */
    public static String empreinteMetier(DonneesQuittanceCertifiee d) {
        StringBuilder json = new StringBuilder(256);
        json.append("{\"bailleur\":\"").append(esc(d.bailleurNom())).append('"');
        champ(json, "bailleur_adresse", d.bailleurAdresse());
        champ(json, "locataire", d.locataireNom());
        champ(json, "patrimoine", d.patrimoineNom());
        champ(json, "bien", d.bienAdresse());
        champ(json, "periode", d.periode());
        json.append(',').append(blocMontants(d)).append('}');
        return sha256Hex(json.toString());
    }

    /** SHA-256 hexadécimal minuscule des octets UTF-8 (empreintes de contenu et de PDF). */
    public static String sha256Hex(String texte) {
        return sha256Hex(texte.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 hexadécimal minuscule. */
    public static String sha256Hex(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible.", e);
        }
    }

    private static String blocMontants(DonneesQuittanceCertifiee d) {
        StringBuilder json = new StringBuilder(160);
        json.append("\"montants\":{\"devise\":\"").append(d.loyerHc().devise().name()).append('"');
        montant(json, "loyer_hc", d.loyerHc());
        montant(json, "provision_charges", d.provisionCharges());
        montant(json, "loyer_cc", d.loyerCc());
        montant(json, "montant_recu", d.montantRecu());
        json.append("},\"paiement\":{\"mode\":\"").append(esc(d.modePaiement())).append('"');
        json.append(",\"garantie_retenue\":");
        if (d.garantieRetenue() == null) {
            json.append("null");
        } else {
            json.append('"').append(d.garantieRetenue().montant().toPlainString()).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static void champ(StringBuilder json, String cle, String valeur) {
        json.append(",\"").append(cle).append("\":\"").append(esc(valeur)).append('"');
    }

    private static void montant(StringBuilder json, String cle, Money valeur) {
        json.append(",\"").append(cle).append("\":\"").append(valeur.montant().toPlainString())
                .append('"');
    }

    /** Échappement JSON minimal des chaînes injectées (saisies utilisateur). */
    private static String esc(String valeur) {
        if (valeur == null) {
            return "";
        }
        StringBuilder sortie = new StringBuilder(valeur.length());
        for (int i = 0; i < valeur.length(); i++) {
            char c = valeur.charAt(i);
            switch (c) {
                case '"' -> sortie.append("\\\"");
                case '\\' -> sortie.append("\\\\");
                case '\n' -> sortie.append("\\n");
                case '\r' -> sortie.append("\\r");
                case '\t' -> sortie.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sortie.append(String.format("\\u%04x", (int) c));
                    } else {
                        sortie.append(c);
                    }
                }
            }
        }
        return sortie.toString();
    }
}
