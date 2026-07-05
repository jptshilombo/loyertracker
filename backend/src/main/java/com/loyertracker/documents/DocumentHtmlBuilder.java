package com.loyertracker.documents;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.loyertracker.quittances.DonneesQuittanceCertifiee;
import com.loyertracker.quittances.ThemeQuittance;

/**
 * Met en page un {@link DonneesDocument} en XHTML bien formé, consommable par OpenHTMLtoPDF.
 * Composant pur (sans état, sans I/O) : la mise en forme est testable indépendamment du rendu PDF
 * et de la base.
 */
@Component
public class DocumentHtmlBuilder {

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private static final String DEMEURANT = "</strong>, demeurant ";

    /** Construit le XHTML complet du document. */
    public String construire(DonneesDocument d) {
        boolean quittance = d.type() == TypeDocument.QUITTANCE;
        String titre = quittance ? "Quittance de loyer" : "Avis d'échéance";

        StringBuilder corps = new StringBuilder();
        if (quittance) {
            corps.append("<p>Je soussigné(e) <strong>").append(esc(d.bailleurNom()))
                    .append(DEMEURANT).append(esc(d.bailleurAdresse()))
                    .append(", propriétaire du logement situé <strong>").append(esc(d.bienAdresse()))
                    .append("</strong>, déclare avoir reçu de <strong>").append(esc(d.locataireNom()))
                    .append("</strong> la somme de <strong>").append(d.montant().formate())
                    .append("</strong> au titre du loyer et des charges pour la période de <strong>")
                    .append(esc(d.periodeLibelle()))
                    .append("</strong>, et lui en donne quittance, sous réserve de tous mes droits.</p>");
        } else {
            corps.append("<p><strong>").append(esc(d.bailleurNom()))
                    .append(DEMEURANT).append(esc(d.bailleurAdresse()))
                    .append(".</p><p>Logement situé <strong>").append(esc(d.bienAdresse()))
                    .append("</strong> — locataire <strong>").append(esc(d.locataireNom()))
                    .append("</strong>.</p><p>Le loyer pour la période de <strong>")
                    .append(esc(d.periodeLibelle()))
                    .append("</strong> est exigible le <strong>").append(d.dateExigibilite().format(DATE_FR))
                    .append("</strong>. Somme restant due : <strong>").append(d.montant().formate())
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
                + "<tr><td>Loyer hors charges</td><td class=\"montant\">" + d.loyerHc().formate() + "</td></tr>"
                + "<tr><td>Provision pour charges</td><td class=\"montant\">" + d.provisionCharges().formate() + "</td></tr>"
                + "<tr class=\"total\"><td>Total charges comprises</td><td class=\"montant\">" + d.loyerCc().formate() + "</td></tr>"
                + "</table>"
                + "<div class=\"pied\">" + esc(d.bailleurNom()) + "</div>"
                + "</body></html>";
    }

    /**
     * Gabarit de la quittance certifiée (ADR-15 D6, US-101) : A4, mise en page tableau (CSS 2.1,
     * seul niveau supporté par OpenHTMLtoPDF), autosuffisant — logo et QR embarqués en data-URI.
     *
     * @param d               données métier certifiées (périmètre exact du {@code content_hash})
     * @param theme           thème visuel résolu pour le bailleur (défaut LoyerTracker)
     * @param urlVerification URL publique de vérification (aussi encodée dans le QR)
     * @param contentHash     empreinte SHA-256 du payload canonique, imprimée sur le document
     * @param qrDataUri       QR code de vérification (PNG en data-URI)
     */
    public String construireQuittanceCertifiee(DonneesQuittanceCertifiee d, ThemeQuittance theme,
            String urlVerification, String contentHash, String qrDataUri) {
        String accent = theme.couleurAccent();
        String encre = theme.couleurEncre();
        String logo = theme.logoDataUri() == null ? ""
                : "<img class=\"logo\" src=\"" + theme.logoDataUri() + "\" alt=\"\" />";

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
                + "<meta charset=\"UTF-8\" />"
                + "<style>"
                + "  @page { size: A4; margin: 1.5cm 1.6cm; }"
                + "  body { font-family: sans-serif; font-size: 10pt; color: " + encre + "; }"
                + "  table { width: 100%; border-collapse: collapse; }"
                + "  td { vertical-align: top; }"
                + "  .entete { border-bottom: 3px solid " + accent + "; padding-bottom: 10px; }"
                + "  .logo { width: 58px; height: 58px; }"
                + "  .marque { font-size: 15pt; font-weight: bold; color: " + accent + "; padding-left: 10px; }"
                + "  .titre { text-align: right; }"
                + "  .titre h1 { font-size: 17pt; margin: 0; color: " + accent + "; }"
                + "  .badge { display: inline-block; font-size: 8pt; font-weight: bold; color: #ffffff;"
                + "           background-color: " + accent + "; padding: 3px 8px; margin-top: 4px; }"
                + "  .identite { margin-top: 8px; color: #555555; font-size: 9pt; }"
                + "  .identite strong { color: " + encre + "; }"
                + "  .cartes { margin-top: 14px; }"
                + "  .carte { border: 1px solid #d8d8d8; padding: 10px 12px; width: 50%; }"
                + "  .carte h2, .bloc h2 { font-size: 8pt; text-transform: uppercase; margin: 0 0 6px 0;"
                + "                        color: " + accent + "; letter-spacing: 1px; }"
                + "  .bloc { margin-top: 12px; border: 1px solid #d8d8d8; padding: 10px 12px; }"
                + "  .declaration { margin: 14px 0; line-height: 1.5; }"
                + "  .montants { margin-top: 4px; }"
                + "  .montants th, .montants td { padding: 7px 10px; border-bottom: 1px solid #dddddd;"
                + "                               text-align: left; }"
                + "  .montants th { background-color: #f2f5f8; font-size: 8.5pt; text-transform: uppercase;"
                + "                 color: " + accent + "; }"
                + "  td.montant, th.montant { text-align: right; }"
                + "  tr.total td { font-weight: bold; border-top: 2px solid " + accent + ";"
                + "                border-bottom: none; font-size: 11pt; }"
                + "  .reglement td { padding: 6px 10px 0 10px; font-size: 9pt; color: #555555; }"
                + "  .reglement strong { color: " + encre + "; }"
                + "  .verif { margin-top: 18px; border: 1px solid " + accent + "; }"
                + "  .verif td { padding: 10px 12px; }"
                + "  .verif .qr { width: 88px; }"
                + "  .verif img { width: 82px; height: 82px; }"
                + "  .verif .consigne { font-weight: bold; }"
                + "  .verif .url, .verif .hash { font-family: monospace; font-size: 7pt; color: #555555; }"
                + "  .cachet { margin-top: 14px; font-size: 8.5pt; font-style: italic; color: #555555;"
                + "            border-top: 1px solid #dddddd; padding-top: 8px; }"
                + "  .mentions { margin-top: 6px; font-size: 7.5pt; color: #888888; }"
                + "</style></head><body>"

                + "<table class=\"entete\"><tr>"
                + "<td style=\"width:62px\">" + logo + "</td>"
                + "<td class=\"marque\">" + esc(theme.nomMarque()) + "</td>"
                + "<td class=\"titre\"><h1>Quittance de loyer</h1>"
                + "<span class=\"badge\">DOCUMENT CERTIFIÉ</span></td>"
                + "</tr></table>"

                + "<div class=\"identite\">N° <strong>" + esc(d.numero()) + "</strong>"
                + " · Version <strong>" + d.version() + "</strong>"
                + " · Émise le <strong>" + d.dateEmission().format(DATE_FR) + "</strong></div>"

                + "<table class=\"cartes\"><tr>"
                + "<td class=\"carte\"><h2>Bailleur</h2><strong>" + esc(d.bailleurNom())
                + "</strong><br/>" + esc(d.bailleurAdresse()) + "</td>"
                + "<td style=\"width:12px\"></td>"
                + "<td class=\"carte\"><h2>Locataire</h2><strong>" + esc(d.locataireNom())
                + "</strong></td>"
                + "</tr></table>"

                + "<div class=\"bloc\"><h2>Location</h2>"
                + "<table><tr>"
                + "<td><strong>Patrimoine</strong><br/>" + esc(d.patrimoineNom()) + "</td>"
                + "<td><strong>Bien loué</strong><br/>" + esc(d.bienAdresse()) + "</td>"
                + "<td><strong>Période</strong><br/>" + esc(d.periodeLibelle()) + "</td>"
                + "</tr></table></div>"

                + "<p class=\"declaration\">Je soussigné(e) <strong>" + esc(d.bailleurNom())
                + DEMEURANT + esc(d.bailleurAdresse())
                + ", déclare avoir reçu de <strong>" + esc(d.locataireNom())
                + "</strong> la somme de <strong>" + d.montantRecu().formate()
                + "</strong> au titre du loyer et des charges pour la période de <strong>"
                + esc(d.periodeLibelle())
                + "</strong>, et lui en donne quittance, sous réserve de tous mes droits.</p>"

                + "<table class=\"montants\">"
                + "<tr><th>Poste</th><th class=\"montant\">Montant</th></tr>"
                + "<tr><td>Loyer hors charges</td><td class=\"montant\">"
                + d.loyerHc().formate() + "</td></tr>"
                + "<tr><td>Provision pour charges</td><td class=\"montant\">"
                + d.provisionCharges().formate() + "</td></tr>"
                + "<tr class=\"total\"><td>Total reçu</td><td class=\"montant\">"
                + d.montantRecu().formate() + "</td></tr>"
                + "</table>"

                + "<table class=\"reglement\"><tr>"
                + "<td>Mode de paiement : <strong>" + esc(d.modePaiement()) + "</strong></td>"
                + (d.garantieRetenue() == null ? ""
                        : "<td>Garantie utilisée : <strong>" + d.garantieRetenue().formate()
                                + "</strong></td>")
                + "</tr></table>"

                + "<table class=\"verif\"><tr>"
                + "<td class=\"qr\"><img src=\"" + qrDataUri + "\" alt=\"QR code\" /></td>"
                + "<td><div class=\"consigne\">Scanner ce QR Code pour vérifier "
                + "l’authenticité de cette quittance.</div>"
                + "<div class=\"url\">" + esc(urlVerification) + "</div>"
                + "<div class=\"hash\">Empreinte SHA-256 du contenu certifié : "
                + esc(contentHash) + "</div></td>"
                + "</tr></table>"

                + "<div class=\"cachet\">Document généré électroniquement par "
                + esc(theme.nomMarque())
                + ". Toute modification rend ce document invalide.</div>"
                + "<div class=\"mentions\">Quittance n° " + esc(d.numero()) + ", version "
                + d.version() + " — numéro permanent, jamais réutilisé. "
                + "L’exemplaire officiel est conservé par " + esc(theme.nomMarque())
                + " et téléchargeable depuis la page de vérification. "
                + "La présente quittance ne libère le locataire que pour la période "
                + "indiquée.</div>"
                + "</body></html>";
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
