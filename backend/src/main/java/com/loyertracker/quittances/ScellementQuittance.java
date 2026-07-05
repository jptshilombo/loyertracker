package com.loyertracker.quittances;

/**
 * Scellement de l'exemplaire officiel avant stockage (ADR-15 D6). L'implémentation actuelle est
 * le cachet électronique visuel (déjà porté par le gabarit HTML) : les octets sont stockés tels
 * quels. Une future implémentation PAdES (signature cryptographique PDFBox) se branchera ici —
 * {@code pdf_hash} étant calculé APRÈS scellement, le flux d'émission ne change pas ; une
 * re-signature d'un document déjà émis passe par une nouvelle version, jamais par une mutation.
 */
public interface ScellementQuittance {

    byte[] sceller(byte[] pdf);
}
