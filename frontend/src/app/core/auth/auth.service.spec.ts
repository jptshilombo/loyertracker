import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  function configure(keycloakMock: Partial<Keycloak>): AuthService {
    TestBed.configureTestingModule({
      providers: [AuthService, { provide: Keycloak, useValue: keycloakMock }],
    });
    return TestBed.inject(AuthService);
  }

  afterEach(() => TestBed.resetTestingModule());

  it('retourne preferred_username depuis le token parse', () => {
    const auth = configure({ tokenParsed: { preferred_username: 'bailleur-test' } });
    expect(auth.getUsername()).toBe('bailleur-test');
  });

  it('retourne une chaine vide quand le token est absent', () => {
    const auth = configure({ tokenParsed: undefined });
    expect(auth.getUsername()).toBe('');
  });

  it('retourne une chaine vide quand preferred_username est manquant', () => {
    const auth = configure({ tokenParsed: { sub: 'abc-123' } });
    expect(auth.getUsername()).toBe('');
  });

  it('retourne vrai quand le client Keycloak est authentifie', () => {
    expect(configure({ authenticated: true }).isLoggedIn()).toBeTrue();
  });

  it('retourne faux quand le client Keycloak n est pas authentifie', () => {
    expect(configure({ authenticated: false }).isLoggedIn()).toBeFalse();
  });

  it('retourne les roles realm', () => {
    const auth = configure({ realmAccess: { roles: ['BAILLEUR'] } });
    expect(auth.roles).toEqual(['BAILLEUR']);
  });

  it('delegue hasRole au client Keycloak', () => {
    const auth = configure({ hasRealmRole: (role: string) => role === 'BAILLEUR' });
    expect(auth.hasRole('BAILLEUR')).toBeTrue();
    expect(auth.hasRole('GESTIONNAIRE')).toBeFalse();
  });
});
