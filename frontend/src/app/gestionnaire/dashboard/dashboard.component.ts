import { Component, inject } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

/**
 * Tableau de bord gestionnaire (placeholder — étape 05). Le périmètre (biens couverts par une
 * affectation ACTIVE) sera filaire côté API à l'étape 06.
 */
@Component({
  selector: 'app-gestionnaire-dashboard',
  standalone: true,
  template: `
    <h1>Tableau de bord — Gestionnaire</h1>
    <p>
      Connecté en tant que <strong>{{ username }}</strong> · rôles : {{ roles.join(', ') || '—' }}
    </p>
    <p>Périmètre limité aux biens affectés (ADR-02) — à venir à l'étape 06.</p>
  `,
})
export class GestionnaireDashboardComponent {
  private readonly auth = inject(AuthService);

  readonly username = this.auth.getUsername();
  readonly roles = this.auth.roles;
}
