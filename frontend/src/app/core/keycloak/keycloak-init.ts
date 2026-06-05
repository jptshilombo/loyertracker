import { KeycloakService } from 'keycloak-angular';

/**
 * Initialisation OIDC (Authorization Code Flow + PKCE S256) contre le realm `loyertracker`.
 *
 * - `url: '/auth'` : même origine, proxifié vers Keycloak par Nginx (ADR-08).
 * - `onLoad: 'login-required'` : l'accès à l'application déclenche la connexion Keycloak.
 * - `checkLoginIframe: false` : évite l'iframe de vérification de session (fragile derrière proxy).
 *
 * Le squelette protège toute l'application ; les routes publiques (acceptation d'invitation)
 * passeront en `check-sso` lorsqu'elles seront introduites.
 */
export function initializeKeycloak(keycloak: KeycloakService): () => Promise<boolean> {
  return () =>
    keycloak.init({
      config: {
        url: '/auth',
        realm: 'loyertracker',
        clientId: 'loyertracker-spa',
      },
      initOptions: {
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        redirectUri: window.location.origin + '/',
      },
      // Le Bearer est posé par notre intercepteur ; on n'envoie jamais le token à Keycloak.
      bearerExcludedUrls: ['/auth'],
    });
}
