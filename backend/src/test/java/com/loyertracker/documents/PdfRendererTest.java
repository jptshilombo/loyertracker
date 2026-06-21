package com.loyertracker.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Vérifie que le moteur produit bien un PDF (en-tête %PDF) à partir d'un XHTML bien formé. */
class PdfRendererTest {

    private final PdfRenderer renderer = new PdfRenderer();

    @Test
    void rendUnPdfValide() {
        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\" /></head>"
                + "<body><h1>Test</h1><p>Document locatif</p></body></html>";

        byte[] pdf = renderer.rendre(xhtml);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
