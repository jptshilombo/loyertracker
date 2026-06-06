import { CanActivateFn } from '@angular/router';
import { createAuthGuard } from 'keycloak-angular';

/**
 * Garde de route : exige une session authentifiée, sinon redirige vers Keycloak.
 */
export const authGuard = createAuthGuard<CanActivateFn>(async (_route, state, authData) => {
  if (authData.authenticated) {
    return true;
  }

  await authData.keycloak.login({ redirectUri: window.location.origin + state.url });
  return false;
});
