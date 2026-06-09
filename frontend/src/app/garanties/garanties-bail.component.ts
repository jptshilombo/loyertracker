import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { Garantie, S03ApiService, TypeRestitution } from '../core/s03/s03-api.service';

/**
 * Dépôt et restitution des garanties d'un bail (US-32, Annexe A.5). Réutilisable bailleur /
 * gestionnaire affecté ; le cloisonnement est assuré côté backend (ReBAC + RLS).
 */
@Component({
  selector: 'app-garanties-bail',
  imports: [ReactiveFormsModule],
  template: `
    <div class="panel">
      <header class="panel-head">
        <h2>Garanties</h2>
        <span class="muted">{{ message() }}</span>
      </header>

      <form [formGroup]="depotForm" (ngSubmit)="deposer()" class="depot">
        <div class="fields">
          <label>
            Montant
            <input type="number" formControlName="montant" min="0" step="0.01" />
          </label>
          <label>
            Type
            <input type="text" formControlName="typeGarantie" />
          </label>
          <label>
            Date de dépôt
            <input type="date" formControlName="dateDepot" />
          </label>
        </div>
        <button type="submit" [disabled]="depotForm.invalid || chargement()">Déposer</button>
      </form>

      @if (garanties().length === 0) {
        <p class="muted">Aucune garantie.</p>
      }
      <div class="list">
        @for (g of garanties(); track g.id) {
          <div class="item">
            <span>
              <strong>{{ g.montant }}</strong> <small>{{ g.typeGarantie }} · {{ g.dateDepot }}</small>
            </span>
            <span class="badge" [attr.data-statut]="g.statut">{{ g.statut }}</span>
            @if (g.montantRetenu > 0) {
              <small>retenu {{ g.montantRetenu }} — {{ g.motifRetenue }}</small>
            }
            @if (g.statut !== 'RESTITUE_TOTAL') {
              <button type="button" (click)="selectionner(g)">Restituer</button>
            }
          </div>
        }
      </div>

      @if (selection(); as g) {
        <form [formGroup]="restitutionForm" (ngSubmit)="restituer()" class="restitution">
          <h3>Restituer la garantie {{ g.montant }} ({{ g.statut }})</h3>
          <label>
            Type
            <select formControlName="type">
              <option value="TOTALE">TOTALE</option>
              @if (g.statut === 'DETENU') {
                <option value="PARTIELLE">PARTIELLE</option>
              }
            </select>
          </label>
          @if (restitutionForm.value.type === 'PARTIELLE') {
            <div class="fields">
              <label>
                Montant retenu
                <input type="number" formControlName="montantRetenu" min="0" step="0.01" />
              </label>
              <label>
                Motif
                <input type="text" formControlName="motifRetenue" />
              </label>
            </div>
          }
          <button type="submit" [disabled]="chargement()">Confirmer</button>
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
      .item,
      .fields {
        display: flex;
        gap: 0.75rem;
        align-items: center;
      }
      .panel-head,
      .item {
        justify-content: space-between;
      }
      h2,
      h3 {
        margin-top: 0;
      }
      .depot {
        margin-bottom: 0.75rem;
      }
      .list {
        display: grid;
        gap: 0.5rem;
      }
      .item {
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.5rem;
        background: #0f172a;
      }
      .restitution {
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
      .badge[data-statut='RESTITUE_TOTAL'] {
        color: #bbf7d0;
      }
    `,
  ],
})
export class GarantiesBailComponent {
  private readonly api = inject(S03ApiService);

  readonly bienId = input.required<string>();
  readonly bailId = input.required<string>();

  readonly garanties = signal<Garantie[]>([]);
  readonly selection = signal<Garantie | null>(null);
  readonly message = signal('Prêt');
  readonly chargement = signal(false);

  readonly depotForm = new FormGroup({
    montant: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    typeGarantie: new FormControl('CAUTION', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(50)],
    }),
    dateDepot: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly restitutionForm = new FormGroup({
    type: new FormControl<TypeRestitution>('TOTALE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    montantRetenu: new FormControl<number | null>(null),
    motifRetenue: new FormControl<string | null>(null),
  });

  constructor() {
    effect(() => {
      const bienId = this.bienId();
      const bailId = this.bailId();
      this.selection.set(null);
      this.chargerPour(bienId, bailId);
    });
  }

  selectionner(g: Garantie): void {
    this.selection.set(g);
    this.restitutionForm.reset({ type: 'TOTALE', montantRetenu: null, motifRetenue: null });
  }

  deposer(): void {
    if (this.depotForm.invalid) {
      return;
    }
    this.chargement.set(true);
    this.message.set('Dépôt…');
    this.api.deposerGarantie(this.bienId(), this.bailId(), this.depotForm.getRawValue()).subscribe({
      next: () => {
        this.message.set('Garantie déposée');
        this.depotForm.reset({ montant: 0, typeGarantie: 'CAUTION', dateDepot: '' });
        this.chargerPour(this.bienId(), this.bailId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  restituer(): void {
    const g = this.selection();
    if (!g) {
      return;
    }
    const { type, montantRetenu, motifRetenue } = this.restitutionForm.getRawValue();
    if (type === 'PARTIELLE') {
      if (!montantRetenu || montantRetenu <= 0 || !motifRetenue || !motifRetenue.trim()) {
        this.message.set('Partielle : montant retenu (> 0) et motif requis');
        return;
      }
      if (montantRetenu > g.montant) {
        this.message.set('Le montant retenu ne peut excéder la garantie');
        return;
      }
    }
    this.chargement.set(true);
    this.message.set('Restitution…');
    this.api
      .restituer(this.bienId(), this.bailId(), g.id, {
        type,
        montantRetenu: type === 'PARTIELLE' ? montantRetenu! : undefined,
        motifRetenue: type === 'PARTIELLE' ? motifRetenue! : undefined,
      })
      .subscribe({
        next: () => {
          this.message.set('Garantie restituée');
          this.selection.set(null);
          this.chargerPour(this.bienId(), this.bailId());
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      });
  }

  private chargerPour(bienId: string, bailId: string): void {
    this.chargement.set(true);
    this.api.listerGaranties(bienId, bailId).subscribe({
      next: (garanties) => {
        this.garanties.set(garanties);
        this.message.set(`${garanties.length} garantie(s)`);
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
          ? 'données invalides'
          : err.status === 404
            ? 'introuvable'
            : err.status === 409
              ? 'transition interdite'
              : err.status === 403
                ? 'accès refusé'
                : 'erreur API';
      this.message.set(`${detail} (${err.status})`);
      return;
    }
    this.message.set('erreur inconnue');
  }
}
