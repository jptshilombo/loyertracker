import { Injectable, inject } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

/**
 * Façade applicative au-dessus de {@link KeycloakService} : expose l'état d'authentification,
 * les rôles et le logout sans coupler les composants à l'API Keycloak.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = inject(KeycloakService);

  isLoggedIn(): boolean {
    return this.keycloak.isLoggedIn();
  }

  getToken(): Promise<string> {
    return this.keycloak.getToken();
  }

  getUsername(): string {
    // Lu depuis le token (preferred_username) plutôt que getUsername(), qui exige un
    // loadUserProfile() préalable et lève sinon « user profile was not loaded ».
    const token = this.keycloak.getKeycloakInstance().tokenParsed as
      | { preferred_username?: string }
      | undefined;
    return token?.preferred_username ?? '';
  }

  get roles(): string[] {
    return this.keycloak.getUserRoles();
  }

  hasRole(role: string): boolean {
    return this.keycloak.isUserInRole(role);
  }

  login(): Promise<void> {
    return this.keycloak.login();
  }

  logout(): Promise<void> {
    return this.keycloak.logout(window.location.origin);
  }
}
