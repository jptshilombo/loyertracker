import { Injectable, inject } from '@angular/core';
import Keycloak from 'keycloak-js';

/**
 * Façade applicative au-dessus du client Keycloak JS fourni par `provideKeycloak`.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = inject(Keycloak);

  isLoggedIn(): boolean {
    return this.keycloak.authenticated === true;
  }

  getToken(): Promise<string> {
    return Promise.resolve(this.keycloak.token ?? '');
  }

  getUsername(): string {
    const token = this.keycloak.tokenParsed as { preferred_username?: string } | undefined;
    return token?.preferred_username ?? '';
  }

  get roles(): string[] {
    return this.keycloak.realmAccess?.roles ?? [];
  }

  hasRole(role: string): boolean {
    return this.keycloak.hasRealmRole(role);
  }

  login(): Promise<void> {
    return this.keycloak.login();
  }

  logout(): Promise<void> {
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
