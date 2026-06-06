import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';

@Component({
    selector: 'app-navbar',
    imports: [RouterLink, RouterLinkActive],
    template: `
    <nav class="navbar">
      <span class="brand">LoyerTracker</span>
      <a routerLink="/bailleur" routerLinkActive="active">Bailleur</a>
      <a routerLink="/gestionnaire" routerLinkActive="active">Gestionnaire</a>
      <span class="spacer"></span>
      <span class="user">{{ username }}</span>
      <button type="button" (click)="logout()">Déconnexion</button>
    </nav>
  `,
    styles: [
        `
      .navbar {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 0.75rem 1.5rem;
        background: #1e293b;
        border-bottom: 1px solid #334155;
      }
      .brand {
        font-weight: 700;
        color: #38bdf8;
      }
      a {
        color: #cbd5e1;
        text-decoration: none;
      }
      a.active {
        color: #38bdf8;
        font-weight: 600;
      }
      .spacer {
        flex: 1;
      }
      .user {
        color: #94a3b8;
        font-size: 0.9rem;
      }
    `,
    ]
})
export class NavbarComponent {
  private readonly auth = inject(AuthService);
  readonly username = this.auth.getUsername();

  logout(): void {
    void this.auth.logout();
  }
}
