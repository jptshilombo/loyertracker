package com.loyertracker.quittances;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Génération du QR code de vérification (ADR-15 D4). Le QR ne contient que l'URL publique de
 * vérification — jamais le PDF ni des données personnelles. L'image est produite côté serveur et
 * embarquée en data-URI dans le XHTML : le PDF certifié est autosuffisant (OpenHTMLtoPDF ne
 * charge aucune ressource externe).
 */
@Component
public class QrCodeQuittance {

    private static final int TAILLE_PIXELS = 360;

    /** PNG du QR encodé en data-URI, prêt à être référencé par une balise {@code <img>}. */
    public String dataUri(String url) {
        try {
            BitMatrix matrice = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE,
                    TAILLE_PIXELS, TAILLE_PIXELS,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            BufferedImage image = new BufferedImage(TAILLE_PIXELS, TAILLE_PIXELS,
                    BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < TAILLE_PIXELS; x++) {
                for (int y = 0; y < TAILLE_PIXELS; y++) {
                    image.setRGB(x, y, matrice.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Échec de génération du QR code de vérification.", e);
        }
    }
}
