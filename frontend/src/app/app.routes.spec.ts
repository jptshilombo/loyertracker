import { Route } from '@angular/router';

import { routes } from './app.routes';
import { authGuard } from './core/auth/auth.guard';

/**
 * Non-régression US-103 : l'ouverture de la route publique `/verify/receipt/:id` ne doit pas
 * relâcher la protection des routes existantes. On vérifie sur la configuration de routage —
 * la source de vérité — plutôt que par un scénario de navigation.
 */
describe('routes', () => {
  function routePour(path: string): Route {
    const route = routes.find((r) => r.path === path);
    if (!route) {
      throw new Error(`Route introuvable : ${path}`);
    }
    return route;
  }

  it('la route publique de vérification n’a aucune garde', () => {
    expect(routePour('verify/receipt/:id').canActivate).toBeUndefined();
  });

  it('les routes métier restent gardées par authGuard', () => {
    for (const path of ['bailleur', 'bailleur/profil', 'gestionnaire']) {
      expect(routePour(path).canActivate).toEqual([authGuard]);
    }
  });
});
