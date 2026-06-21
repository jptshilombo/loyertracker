package com.loyertracker.documents;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Component;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Rend un document XHTML bien formé en PDF (PDF/A-friendly) via OpenHTMLtoPDF. Le PDF est produit
 * en mémoire et retourné tel quel — aucun fichier n'est écrit ni conservé (arbitrage C).
 */
@Component
public class PdfRenderer {

    /**
     * @param xhtml contenu XHTML bien formé
     * @return les octets du PDF généré
     */
    public byte[] rendre(String xhtml) {
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(sortie);
            builder.run();
            return sortie.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Échec de génération du PDF du document locatif.", e);
        }
    }
}
