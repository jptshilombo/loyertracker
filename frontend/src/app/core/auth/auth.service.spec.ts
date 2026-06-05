import { TestBed } from '@angular/core/testing';
import { KeycloakService } from 'keycloak-angular';

import { AuthService } from './auth.service';

/**
 * Vérifie que getUsername() lit `preferred_username` depuis le token déjà parsé (et ne
 * dépend pas de loadUserProfile(), source du bug corrigé à l'étape 05), et reste robuste
 * lorsqu'aucun token n'est disponible.
 */
describe('AuthService', () => {
  function configure(tokenParsed: unknown): AuthService {
    const keycloakMock: Partial<KeycloakService> = {
      getKeycloakInstance: () => ({ tokenParsed } as ReturnType<KeycloakService['getKeycloakInstance']>),
    };
    TestBed.configureTestingModule({
      providers: [AuthService, { provide: KeycloakService, useValue: keycloakMock }],
    });
    return TestBed.inject(AuthService);
  }

  afterEach(() => TestBed.resetTestingModule());

  it('retourne preferred_username depuis le token parsé', () => {
    const auth = configure({ preferred_username: 'bailleur-test' });
    expect(auth.getUsername()).toBe('bailleur-test');
  });

  it('retourne une chaîne vide quand le token est absent', () => {
    const auth = configure(undefined);
    expect(auth.getUsername()).toBe('');
  });

  it('retourne une chaîne vide quand preferred_username est manquant', () => {
    const auth = configure({ sub: 'abc-123' });
    expect(auth.getUsername()).toBe('');
  });
});
