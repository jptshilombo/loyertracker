import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';

import { Honoraire, S04ApiService, StatutHonoraire } from '../core/s04/s04-api.service';

/**
 * Consultation des honoraires de gestion d'un bien (US-40). Réutilisable par l'espace bailleur et
 * l'espace gestionnaire ; la validation (transition de statut) et le recalcul ne sont exposés
 * qu'au bailleur via {@code peutValider} (cohérent avec {@code @PreAuthorize hasRole('BAILLEUR')}
 * sur `PATCH /api/honoraires/{id}/statut` et `POST /api/batch/honoraires`). Le statut `PAYE` est
 * figé côté backend : aucune action n'est alors proposée.
 */
@Component({
  selector: 'app-honoraires-bien',
  template: `
    <div class="panel">
      <header class="panel-head">
        <h2>Honoraires</h2>
        <span class="muted">{{ message() }}</span>
      </header>

      <div class="toolbar">
        <button type="button" (click)="charger()" [disabled]="chargement()">Rafraîchir</button>
        @if (peutValider()) {
          <button type="button" (click)="recalculer()" [disabled]="chargement()">
            Recalculer
          </button>
        }
      </div>

      @if (honoraires().length === 0) {
        <p class="muted">Aucun honoraire.</p>
      }
      <div class="list">
        @for (h of honoraires(); track h.id) {
          <div class="row">
            <span><strong>{{ h.periode }}</strong></span>
            <span>{{ h.montant }}</span>
            <span class="badge" [attr.data-statut]="h.statut">{{ h.statut }}</span>
            @if (peutValider() && h.statut !== 'PAYE') {
              <span class="actions">
                @if (h.statut === 'DU') {
                  <button type="button" (click)="changer(h, 'EN_ATTENTE')" [disabled]="chargement()">
                    Mettre en attente
                  </button>
                }
                <button type="button" (click)="changer(h, 'PAYE')" [disabled]="chargement()">
                  Marquer payé
                </button>
              </span>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .panel {
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 1rem;
        background: #111827;
      }
      .panel-head,
      .toolbar,
      .row,
      .actions {
        display: flex;
        gap: 0.75rem;
        align-items: center;
      }
      .panel-head,
      .row {
        justify-content: space-between;
      }
      h2 {
        margin-top: 0;
      }
      .toolbar {
        margin-bottom: 0.75rem;
      }
      .list {
        display: grid;
        gap: 0.5rem;
      }
      .row {
        width: 100%;
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.5rem;
        background: #0f172a;
        color: #e2e8f0;
      }
      button {
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.35rem 0.6rem;
        background: #0f172a;
        color: #e2e8f0;
      }
      .muted {
        color: #94a3b8;
      }
      .badge {
        font-size: 0.85rem;
        color: #bae6fd;
      }
      .badge[data-statut='PAYE'] {
        color: #bbf7d0;
      }
      .badge[data-statut='EN_ATTENTE'] {
        color: #fde68a;
      }
    `,
  ],
})
export class HonorairesBienComponent {
  private readonly api = inject(S04ApiService);

  readonly bienId = input.required<string>();
  readonly peutValider = input<boolean>(false);

  readonly honoraires = signal<Honoraire[]>([]);
  readonly message = signal('Prêt');
  readonly chargement = signal(false);

  constructor() {
    // Recharge la liste chaque fois que le bien ciblé change.
    effect(() => this.chargerPour(this.bienId()));
  }

  charger(): void {
    this.chargerPour(this.bienId());
  }

  changer(h: Honoraire, statut: StatutHonoraire): void {
    this.chargement.set(true);
    this.message.set('Mise à jour…');
    this.api.changerStatutHonoraire(h.id, statut).subscribe({
      next: () => {
        this.message.set(`Honoraire ${h.periode} → ${statut}`);
        this.chargerPour(this.bienId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  recalculer(): void {
    this.chargement.set(true);
    this.message.set('Recalcul…');
    this.api.recalculerHonoraires().subscribe({
      next: (res) => {
        this.message.set(`${res.honorairesCalcules} honoraire(s) recalculé(s)`);
        this.chargerPour(this.bienId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  private chargerPour(bienId: string): void {
    this.chargement.set(true);
    this.api.listerHonoraires(bienId).subscribe({
      next: (honoraires) => {
        this.honoraires.set(honoraires);
        this.message.set(`${honoraires.length} honoraire(s)`);
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  private signalerErreur(err: unknown): void {
    this.chargement.set(false);
    if (err instanceof HttpErrorResponse) {
      const detail =
        err.status === 400
          ? 'incohérence'
          : err.status === 404
            ? 'introuvable'
            : err.status === 403
              ? 'accès refusé'
              : 'erreur API';
      this.message.set(`${detail} (${err.status})`);
      return;
    }
    this.message.set('erreur inconnue');
  }
}
