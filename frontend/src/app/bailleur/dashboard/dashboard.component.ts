import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';

import { API_BASE_URL } from '../../core/api/api.config';
import { AuthService } from '../../core/auth/auth.service';
import { BailleurInscriptionService } from '../inscription/bailleur-inscription.service';

/**
 * Tableau de bord bailleur (placeholder — étape 05). Au premier accès authentifié, il rattache
 * le compte Keycloak au bailleur applicatif (US-10), puis permet de tester l'appel protégé
 * `GET /api/biens`.
 */
@Component({
  selector: 'app-bailleur-dashboard',
  standalone: true,
  template: `
    <h1>Tableau de bord — Bailleur</h1>
    <p>
      Connecté en tant que <strong>{{ username }}</strong> · rôles : {{ roles.join(', ') || '—' }}
    </p>
    <p>Inscription applicative : {{ inscriptionStatus() }}</p>
    <button type="button" (click)="chargerBiens()">Charger mes biens (GET /api/biens)</button>
    <pre>{{ resultat() }}</pre>
  `,
})
export class BailleurDashboardComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly inscription = inject(BailleurInscriptionService);

  readonly username = this.auth.getUsername();
  readonly roles = this.auth.roles;
  readonly inscriptionStatus = signal('en cours');
  readonly resultat = signal('—');

  ngOnInit(): void {
    if (!this.auth.hasRole('BAILLEUR')) {
      this.inscriptionStatus.set('non applicable');
      return;
    }

    this.inscription.inscrire().subscribe({
      next: (result) => {
        this.inscriptionStatus.set(
          result.status === 'created' ? 'créée' : 'déjà existante',
        );
      },
      error: (err) => this.inscriptionStatus.set(`erreur ${err.status}`),
    });
  }

  chargerBiens(): void {
    this.http.get<unknown[]>(`${API_BASE_URL}/biens`).subscribe({
      next: (biens) => this.resultat.set(`200 OK — ${biens.length} bien(s)`),
      error: (err) => this.resultat.set(`Erreur ${err.status}`),
    });
  }
}
