import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { Paiement, S03ApiService, StatutPaiement } from '../core/s03/s03-api.service';

/**
 * Historique et pointage des loyers d'un bien (US-31). Réutilisable par l'espace bailleur et
 * l'espace gestionnaire ; le déclenchement de la génération des échéances n'est exposé qu'au
 * bailleur via {@code peutDeclencher} (cohérent avec {@code @PreAuthorize hasRole('BAILLEUR')}).
 */
@Component({
  selector: 'app-paiements-bien',
  imports: [ReactiveFormsModule],
  template: `
    <div class="panel">
      <header class="panel-head">
        <h2>Loyers</h2>
        <span class="muted">{{ message() }}</span>
      </header>

      <div class="toolbar">
        <button type="button" (click)="charger()" [disabled]="chargement()">Rafraîchir</button>
        @if (peutDeclencher()) {
          <button type="button" (click)="declencher()" [disabled]="chargement()">
            Générer les échéances
          </button>
        }
      </div>

      @if (paiements().length === 0) {
        <p class="muted">Aucun loyer.</p>
      }
      <div class="list">
        @for (p of paiements(); track p.id) {
          <button
            type="button"
            class="row"
            [class.selected]="p.periode === selection()?.periode"
            (click)="selectionner(p)"
          >
            <span><strong>{{ p.periode }}</strong> <small>exig. {{ p.dateExigibilite }}</small></span>
            <span>
              {{ p.montantRecu }} / {{ p.montantAttendu }} · reste {{ p.resteDu }}
            </span>
            <span class="badge" [attr.data-statut]="p.statut">{{ p.statut }}</span>
          </button>
        }
      </div>

      @if (selection(); as p) {
        <form [formGroup]="pointageForm" (ngSubmit)="pointer()" class="pointage">
          <h3>Pointer {{ p.periode }} (attendu {{ p.montantAttendu }})</h3>
          <div class="fields">
            <label>
              Montant reçu
              <input type="number" formControlName="montantRecu" min="0" step="0.01" />
            </label>
            <label>
              Statut
              <select formControlName="statut">
                <option value="RECU">RECU</option>
                <option value="PARTIEL">PARTIEL</option>
                <option value="EN_RETARD">EN_RETARD</option>
                <option value="IMPAYE">IMPAYE</option>
              </select>
            </label>
          </div>
          <button type="submit" [disabled]="pointageForm.invalid || chargement()">Enregistrer</button>
        </form>
      }
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
      .fields {
        display: flex;
        gap: 0.75rem;
        align-items: center;
      }
      .panel-head,
      .row {
        justify-content: space-between;
      }
      h2,
      h3 {
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
        text-align: left;
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.5rem;
        background: #0f172a;
        color: #e2e8f0;
      }
      .selected {
        border-color: #38bdf8;
      }
      .pointage {
        margin-top: 1rem;
        border-top: 1px solid #334155;
        padding-top: 0.75rem;
      }
      label {
        display: grid;
        gap: 0.35rem;
        color: #cbd5e1;
      }
      input,
      select {
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.5rem;
        background: #0f172a;
        color: #e2e8f0;
      }
      .muted,
      small {
        color: #94a3b8;
      }
      .badge {
        font-size: 0.85rem;
        color: #bae6fd;
      }
      .badge[data-statut='EN_RETARD'] {
        color: #fecaca;
      }
      .badge[data-statut='RECU'] {
        color: #bbf7d0;
      }
    `,
  ],
})
export class PaiementsBienComponent {
  private readonly api = inject(S03ApiService);

  readonly bienId = input.required<string>();
  readonly peutDeclencher = input<boolean>(false);

  readonly paiements = signal<Paiement[]>([]);
  readonly selection = signal<Paiement | null>(null);
  readonly message = signal('Prêt');
  readonly chargement = signal(false);

  readonly pointageForm = new FormGroup({
    montantRecu: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    statut: new FormControl<StatutPaiement>('RECU', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor() {
    // Recharge l'historique chaque fois que le bien ciblé change.
    effect(() => {
      const id = this.bienId();
      this.selection.set(null);
      this.chargerPour(id);
    });
  }

  charger(): void {
    this.chargerPour(this.bienId());
  }

  selectionner(p: Paiement): void {
    this.selection.set(p);
    this.pointageForm.setValue({ montantRecu: p.montantRecu, statut: p.statut });
  }

  pointer(): void {
    const p = this.selection();
    if (!p || this.pointageForm.invalid) {
      return;
    }
    const { montantRecu, statut } = this.pointageForm.getRawValue();
    // Contrôles miroir du backend pour un retour immédiat (le backend reste l'autorité : 400).
    if (statut === 'PARTIEL' && (montantRecu <= 0 || montantRecu >= p.montantAttendu)) {
      this.message.set('PARTIEL : 0 < reçu < attendu');
      return;
    }
    if (statut === 'RECU' && montantRecu < p.montantAttendu) {
      this.message.set('RECU : reçu >= attendu');
      return;
    }
    this.chargement.set(true);
    this.message.set('Pointage…');
    this.api.pointer(this.bienId(), p.periode, { montantRecu, statut }).subscribe({
      next: () => {
        this.message.set(`Loyer ${p.periode} pointé`);
        this.selection.set(null);
        this.chargerPour(this.bienId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  declencher(): void {
    this.chargement.set(true);
    this.message.set('Génération…');
    this.api.declencherEcheances().subscribe({
      next: (res) => {
        this.message.set(
          `${res.echeancesCreees} créée(s), ${res.loyersEnRetard} en retard`,
        );
        this.chargerPour(this.bienId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  private chargerPour(bienId: string): void {
    this.chargement.set(true);
    this.api.listerPaiements(bienId).subscribe({
      next: (paiements) => {
        this.paiements.set(paiements);
        this.message.set(`${paiements.length} loyer(s)`);
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
