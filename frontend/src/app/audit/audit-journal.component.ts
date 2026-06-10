import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';

import { AuditEntry, S04ApiService } from '../core/s04/s04-api.service';

/**
 * Consultation du journal d'audit (US-62, ENF-05). Réservé au bailleur côté backend
 * ({@code @PreAuthorize hasRole('BAILLEUR')} ; un gestionnaire reçoit 403) : ce composant n'est
 * monté que dans l'espace bailleur. Liste brute la plus récente d'abord (le backend ordonne déjà
 * par horodatage décroissant), sans filtre ni pagination (défaut D du Plan d'Exécution).
 */
@Component({
  selector: 'app-audit-journal',
  template: `
    <div class="panel">
      <header class="panel-head">
        <h2>Journal d'audit</h2>
        <span class="muted">{{ message() }}</span>
      </header>

      <div class="toolbar">
        <button type="button" (click)="charger()" [disabled]="chargement()">Rafraîchir</button>
      </div>

      @if (entrees().length === 0) {
        <p class="muted">Aucune entrée.</p>
      }
      <div class="list">
        @for (e of entrees(); track e.id) {
          <div class="row">
            <span class="ts">{{ e.horodatage }}</span>
            <span class="role">{{ e.acteurRole }}</span>
            <span class="action">{{ e.action }}</span>
            <span class="entity">{{ e.entityType }}</span>
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
      .row {
        display: flex;
        gap: 0.75rem;
        align-items: center;
      }
      .panel-head {
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
        font-size: 0.85rem;
      }
      .ts {
        color: #94a3b8;
      }
      .action {
        flex: 1;
        color: #bae6fd;
      }
      .role,
      .entity {
        color: #cbd5e1;
      }
      .muted {
        color: #94a3b8;
      }
    `,
  ],
})
export class AuditJournalComponent implements OnInit {
  private readonly api = inject(S04ApiService);

  readonly entrees = signal<AuditEntry[]>([]);
  readonly message = signal('Prêt');
  readonly chargement = signal(false);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.api.listerAudit().subscribe({
      next: (entrees) => {
        this.entrees.set(entrees);
        this.message.set(`${entrees.length} entrée(s)`);
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  private signalerErreur(err: unknown): void {
    this.chargement.set(false);
    if (err instanceof HttpErrorResponse) {
      const detail = err.status === 403 ? 'accès refusé' : 'erreur API';
      this.message.set(`${detail} (${err.status})`);
      return;
    }
    this.message.set('erreur inconnue');
  }
}
