import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';

/**
 * Garde de route : exige une session authentifiée, sinon redirige vers la connexion Keycloak
 * (Authorization Code + PKCE). Défense applicative complémentaire à `onLoad: 'login-required'`.
 */
export const authGuard: CanActivateFn = async () => {
  const keycloak = inject(KeycloakService);
  if (keycloak.isLoggedIn()) {
    return true;
  }
  await keycloak.login({ redirectUri: window.location.href });
  return false;
};
