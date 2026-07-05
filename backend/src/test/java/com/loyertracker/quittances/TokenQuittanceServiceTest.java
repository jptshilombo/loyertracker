package com.loyertracker.quittances;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Token HMAC de vérification (ADR-15 D3) : lié au triplet identifiant + version + contenu — tout
 * token forgé, tronqué, d'une autre quittance ou d'une autre version est rejeté.
 */
class TokenQuittanceServiceTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String HASH =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final TokenQuittanceService service = new TokenQuittanceService("secret-de-test", (short) 1);

    @Test
    void tokenGenereEstVerifiableEtDeterministe() {
        String token = service.generer(ID, 1, HASH);

        assertThat(service.verifier(token, ID, 1, HASH)).isTrue();
        assertThat(service.generer(ID, 1, HASH)).isEqualTo(token);
        // base64url sans padding : insérable tel quel dans une URL de QR.
        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tokenLieAuTripletEstRejeteSurToutAutreTriplet() {
        String token = service.generer(ID, 1, HASH);

        assertThat(service.verifier(token, UUID.randomUUID(), 1, HASH))
                .as("token d'une autre quittance").isFalse();
        assertThat(service.verifier(token, ID, 2, HASH))
                .as("token d'une autre version").isFalse();
        assertThat(service.verifier(token, ID, 1, HASH.replace('b', 'c')))
                .as("token d'un autre contenu certifié").isFalse();
    }

    @Test
    void tokenForgeTronqueOuMalformeEstRejete() {
        String token = service.generer(ID, 1, HASH);
        TokenQuittanceService autreSecret = new TokenQuittanceService("autre-secret", (short) 1);

        assertThat(service.verifier(autreSecret.generer(ID, 1, HASH), ID, 1, HASH))
                .as("token signé avec un autre secret").isFalse();
        assertThat(service.verifier(token.substring(0, token.length() - 4), ID, 1, HASH))
                .as("token tronqué").isFalse();
        assertThat(service.verifier("%%%pas-du-base64%%%", ID, 1, HASH))
                .as("base64 invalide").isFalse();
        assertThat(service.verifier(null, ID, 1, HASH)).as("token absent").isFalse();
        assertThat(service.verifier("  ", ID, 1, HASH)).as("token blanc").isFalse();
    }

    @Test
    void kidConfigureEstExpose() {
        assertThat(new TokenQuittanceService("s", (short) 3).kid()).isEqualTo((short) 3);
    }

    @Test
    void sansSecretConfigureUnSecretEphemereDistinctEstGenere() {
        TokenQuittanceService a = new TokenQuittanceService("", (short) 1);
        TokenQuittanceService b = new TokenQuittanceService(null, (short) 1);

        // Fail-safe : chaque instance signe avec son propre secret aléatoire (aucun secret par
        // défaut en dur) — les tokens restent auto-cohérents mais ne survivent pas au redémarrage.
        assertThat(a.verifier(a.generer(ID, 1, HASH), ID, 1, HASH)).isTrue();
        assertThat(a.generer(ID, 1, HASH)).isNotEqualTo(b.generer(ID, 1, HASH));
    }
}
