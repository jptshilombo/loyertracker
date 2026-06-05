import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';
import { from, switchMap } from 'rxjs';

import { API_BASE_URL } from '../api/api.config';

/**
 * Ajoute l'en-tête `Authorization: Bearer <token>` aux seuls appels vers l'API (`/api`).
 * Les endpoints Keycloak (`/auth`) sont exclus : ils ne doivent jamais recevoir ce token.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(API_BASE_URL)) {
    return next(req);
  }
  const keycloak = inject(KeycloakService);
  return from(keycloak.getToken()).pipe(
    switchMap((token) => {
      const authReq = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(authReq);
    }),
  );
};
