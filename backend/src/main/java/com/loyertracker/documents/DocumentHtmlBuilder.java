package com.loyertracker.documents;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Met en page un {@link DonneesDocument} en XHTML bien formé, consommable par OpenHTMLtoPDF.
 * Composant pur (sans état, sans I/O) : la mise en forme est testable indépendamment du rendu PDF
 * et de la base.
 */
@Component
public class DocumentHtmlBuilder {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    /** Construit le XHTML complet du document. */
    public String construire(DonneesDocument d) {
        boolean quittance = d.type() == TypeDocument.QUITTANCE;
        String titre = quittance ? "Quittance de loyer" : "Avis d'échéance";

        StringBuilder corps = new StringBuilder();
        if (quittance) {
            corps.append("<p>Je soussigné(e) <strong>").append(esc(d.bailleurNom()))
                    .append("</strong>, demeurant ").append(esc(d.bailleurAdresse()))
                    .append(", propriétaire du logement situé <strong>").append(esc(d.bienAdresse()))
                    .append("</strong>, déclare avoir reçu de <strong>").append(esc(d.locataireNom()))
                    .append("</strong> la somme de <strong>").append(euros(d.montant()))
                    .append("</strong> au titre du loyer et des charges pour la période de <strong>")
                    .append(esc(d.periodeLibelle()))
                    .append("</strong>, et lui en donne quittance, sous réserve de tous mes droits.</p>");
        } else {
            corps.append("<p><strong>").append(esc(d.bailleurNom()))
                    .append("</strong>, demeurant ").append(esc(d.bailleurAdresse()))
                    .append(".</p><p>Logement situé <strong>").append(esc(d.bienAdresse()))
                    .append("</strong> — locataire <strong>").append(esc(d.locataireNom()))
                    .append("</strong>.</p><p>Le loyer pour la période de <strong>")
                    .append(esc(d.periodeLibelle()))
                    .append("</strong> est exigible le <strong>").append(d.dateExigibilite().format(DATE_FR))
                    .append("</strong>. Somme restant due : <strong>").append(euros(d.montant()))
                    .append("</strong>.</p>");
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
                + "<meta charset=\"UTF-8\" />"
                + "<style>"
                + "  @page { size: A4; margin: 2.5cm; }"
                + "  body { font-family: sans-serif; font-size: 12pt; color: #1a1a1a; }"
                + "  h1 { font-size: 18pt; margin-bottom: 0.2cm; }"
                + "  .meta { color: #555; font-size: 10pt; margin-bottom: 0.8cm; }"
                + "  table { width: 100%; border-collapse: collapse; margin: 0.6cm 0; }"
                + "  th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #ccc; }"
                + "  td.montant, th.montant { text-align: right; }"
                + "  tr.total td { font-weight: bold; border-top: 2px solid #333; border-bottom: none; }"
                + "  .pied { margin-top: 1.2cm; }"
                + "</style></head><body>"
                + "<h1>" + titre + "</h1>"
                + "<div class=\"meta\">Établi le " + d.dateEmission().format(DATE_FR) + "</div>"
                + corps
                + "<table>"
                + "<tr><th>Poste</th><th class=\"montant\">Montant</th></tr>"
                + "<tr><td>Loyer hors charges</td><td class=\"montant\">" + euros(d.loyerHc()) + "</td></tr>"
                + "<tr><td>Provision pour charges</td><td class=\"montant\">" + euros(d.provisionCharges()) + "</td></tr>"
                + "<tr class=\"total\"><td>Total charges comprises</td><td class=\"montant\">" + euros(d.loyerCc()) + "</td></tr>"
                + "</table>"
                + "<div class=\"pied\">" + esc(d.bailleurNom()) + "</div>"
                + "</body></html>";
    }

    private static String euros(BigDecimal montant) {
        return String.format(Locale.FRENCH, "%.2f €", montant);
    }

    /** Échappement XML minimal des données injectées (saisies utilisateur). */
    private static String esc(String valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
