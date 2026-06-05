import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import { API_BASE_URL } from '../../core/api/api.config';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Tableau de bord bailleur (placeholder — étape 05). Le bouton démontre que l'intercepteur
 * ajoute bien le Bearer sur l'appel `GET /api/biens` (200 attendu pour le rôle BAILLEUR).
 */
@Component({
  selector: 'app-bailleur-dashboard',
  standalone: true,
  template: `
    <h1>Tableau de bord — Bailleur</h1>
    <p>
      Connecté en tant que <strong>{{ username }}</strong> · rôles : {{ roles.join(', ') || '—' }}
    </p>
    <button type="button" (click)="chargerBiens()">Charger mes biens (GET /api/biens)</button>
    <pre>{{ resultat() }}</pre>
  `,
})
export class BailleurDashboardComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly username = this.auth.getUsername();
  readonly roles = this.auth.roles;
  readonly resultat = signal('—');

  chargerBiens(): void {
    this.http.get<unknown[]>(`${API_BASE_URL}/biens`).subscribe({
      next: (biens) => this.resultat.set(`200 OK — ${biens.length} bien(s)`),
      error: (err) => this.resultat.set(`Erreur ${err.status}`),
    });
  }
}
