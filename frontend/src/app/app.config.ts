import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import {
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  createInterceptorCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
} from 'keycloak-angular';

import { routes } from './app.routes';

// Le Bearer est attaché à tous les appels /api SAUF /api/public/ : la surface publique de
// vérification des quittances (US-102) est atteinte par des tiers non authentifiés (check-sso, sans
// token). Sans cette exclusion, includeBearerTokenInterceptor bloquerait ces appels faute de token.
const apiBearerCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: /^\/api\/(?!public\/).*/i,
  bearerPrefix: 'Bearer',
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideKeycloak({
      config: {
        url: '/auth',
        realm: 'loyertracker',
        clientId: 'loyertracker-spa',
      },
      initOptions: {
        // check-sso (et non login-required) : l'application n'impose plus l'authentification au
        // bootstrap. Les routes protégées restent gardées par `authGuard` (qui déclenche le login
        // au besoin) ; la page publique de vérification `/verify/receipt/:id` (US-103) est ainsi
        // atteignable sans compte ni formulaire de connexion.
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        redirectUri: window.location.origin + '/',
      },
    }),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: [apiBearerCondition],
    },
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
  ],
};
