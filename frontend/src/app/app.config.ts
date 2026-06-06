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

const apiBearerCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: /^\/api(\/.*)?$/i,
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
        onLoad: 'login-required',
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
