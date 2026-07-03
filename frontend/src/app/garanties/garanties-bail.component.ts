import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import {
  Garantie,
  GarantieMovement,
  Paiement,
  S03ApiService,
  TypeMouvementGarantie,
  TypeRestitution,
} from '../core/s03/s03-api.service';

type ActionGarantie = 'RESTITUTION' | 'RETENUE' | 'COMPLEMENT';
type ColonneTri = 'dateMouvement' | 'type' | 'debit' | 'credit' | 'soldeApres';

/**
 * Dépôt, restitution et usage métier des garanties d'un bail (US-32, Annexe A.5 ; US-95/96/97,
 * EP-12b). Réutilisable bailleur / gestionnaire affecté ; le cloisonnement est assuré côté
 * backend (ReBAC + RLS).
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
              <small>solde {{ g.soldeActuel }}</small>
            </span>
            <span class="badge" [attr.data-statut]="g.statut">{{ g.statut }}</span>
            @if (g.montantRetenu > 0) {
              <small>retenu {{ g.montantRetenu }} — {{ g.motifRetenue }}</small>
            }
            <span class="actions">
              @if (g.statut !== 'RESTITUE_TOTAL') {
                <button type="button" (click)="ouvrir(g, 'RESTITUTION')">Restituer</button>
                @if (g.soldeActuel > 0) {
                  <button type="button" (click)="ouvrir(g, 'RETENUE')">Utiliser pour un impayé</button>
                }
                <button type="button" (click)="ouvrir(g, 'COMPLEMENT')">Compléter</button>
              }
              <button type="button" (click)="basculerHistorique(g)">
                {{ historiqueOuvert() === g.id ? 'Masquer l’historique' : 'Historique' }}
              </button>
            </span>
          </div>
          @if (historiqueOuvert() === g.id) {
            <div class="historique">
              <div class="historique-outils">
                <label>
                  Filtrer par type
                  <select [value]="filtreType()" (change)="filtrerType($event)">
                    <option value="">Tous</option>
                    @for (t of typesDisponibles(); track t) {
                      <option [value]="t">{{ t }}</option>
                    }
                  </select>
                </label>
                <button type="button" (click)="exporterCsv(g)" [disabled]="chargement()">
                  Exporter CSV
                </button>
              </div>
              @if (mouvementsAffiches().length === 0) {
                <p class="muted">Aucun mouvement.</p>
              } @else {
                <div class="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th (click)="trier('dateMouvement')">Date{{ indicateurTri('dateMouvement') }}</th>
                        <th (click)="trier('type')">Type{{ indicateurTri('type') }}</th>
                        <th (click)="trier('debit')">Débit{{ indicateurTri('debit') }}</th>
                        <th (click)="trier('credit')">Crédit{{ indicateurTri('credit') }}</th>
                        <th (click)="trier('soldeApres')">Solde après{{ indicateurTri('soldeApres') }}</th>
                        <th>Auteur</th>
                        <th>Motif</th>
                        <th>Observation</th>
                        <th>Document</th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (m of mouvementsAffiches(); track m.id) {
                        <tr>
                          <td>{{ m.dateMouvement }}</td>
                          <td>{{ m.type }}</td>
                          <td>{{ m.debit }}</td>
                          <td>{{ m.credit }}</td>
                          <td>{{ m.soldeApres }}</td>
                          <td>{{ m.utilisateur }}</td>
                          <td>{{ m.motif }}</td>
                          <td>{{ m.commentaire }}</td>
                          <td>{{ m.referenceDocument }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </div>
          }
        }
      </div>

      @if (selection(); as g) {
        @switch (action()) {
          @case ('RESTITUTION') {
            <form [formGroup]="restitutionForm" (ngSubmit)="restituer()" class="sous-formulaire">
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
          @case ('RETENUE') {
            <form [formGroup]="retenueForm" (ngSubmit)="retenir()" class="sous-formulaire">
              <h3>Utiliser la garantie pour un impayé (solde {{ g.soldeActuel }})</h3>
              @if (impayes().length === 0) {
                <p class="muted">Aucun loyer impayé sur ce bien.</p>
              } @else {
                <div class="fields">
                  <label>
                    Loyer impayé
                    <select formControlName="paiementId">
                      <option value="">— choisir —</option>
                      @for (p of impayes(); track p.id) {
                        <option [value]="p.id">
                          {{ p.periode }} — reste dû {{ p.resteDu }} {{ p.devise }} ({{ p.statut }})
                        </option>
                      }
                    </select>
                  </label>
                  <label>
                    Montant retenu
                    <input type="number" formControlName="montant" min="0" step="0.01" />
                  </label>
                </div>
                <button type="submit" [disabled]="retenueForm.invalid || chargement()">
                  Confirmer la retenue
                </button>
              }
            </form>
          }
          @case ('COMPLEMENT') {
            <form [formGroup]="complementForm" (ngSubmit)="complementer()" class="sous-formulaire">
              <h3>Compléter la garantie (solde {{ g.soldeActuel }})</h3>
              <div class="fields">
                <label>
                  Montant
                  <input type="number" formControlName="montant" min="0" step="0.01" />
                </label>
                <label>
                  Motif
                  <input type="text" formControlName="motif" />
                </label>
              </div>
              <button type="submit" [disabled]="complementForm.invalid || chargement()">
                Confirmer le complément
              </button>
            </form>
          }
        }
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
      .fields,
      .actions,
      .historique-outils {
        display: flex;
        gap: 0.75rem;
        align-items: center;
      }
      .panel-head,
      .item {
        justify-content: space-between;
      }
      .actions {
        flex-wrap: wrap;
        justify-content: flex-end;
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
      .historique {
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.5rem;
        background: #0f172a;
      }
      .historique-outils {
        justify-content: space-between;
        margin-bottom: 0.5rem;
      }
      .table-scroll {
        overflow-x: auto;
      }
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.85rem;
      }
      th,
      td {
        text-align: left;
        padding: 0.35rem 0.5rem;
        border-bottom: 1px solid #334155;
        color: #cbd5e1;
        white-space: nowrap;
      }
      th {
        cursor: pointer;
        color: #e2e8f0;
        user-select: none;
      }
      .sous-formulaire {
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
  readonly action = signal<ActionGarantie | null>(null);
  readonly message = signal('Prêt');
  readonly chargement = signal(false);

  /** Loyers du bien restant dus, candidats à une retenue (US-95). */
  readonly impayes = signal<Paiement[]>([]);

  readonly historiqueOuvert = signal<string | null>(null);
  readonly mouvements = signal<GarantieMovement[]>([]);
  readonly filtreType = signal<TypeMouvementGarantie | ''>('');
  readonly triColonne = signal<ColonneTri>('dateMouvement');
  readonly triSens = signal<1 | -1>(1);

  readonly typesDisponibles = computed(() =>
    [...new Set(this.mouvements().map((m) => m.type))].sort((a, b) => a.localeCompare(b)),
  );

  readonly mouvementsAffiches = computed(() => {
    const filtre = this.filtreType();
    const colonne = this.triColonne();
    const sens = this.triSens();
    return this.mouvements()
      .filter((m) => !filtre || m.type === filtre)
      .sort((a, b) => {
        const va = a[colonne];
        const vb = b[colonne];
        const cmp =
          typeof va === 'number' && typeof vb === 'number'
            ? va - vb
            : String(va).localeCompare(String(vb));
        return cmp * sens;
      });
  });

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

  readonly retenueForm = new FormGroup({
    paiementId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    montant: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.01)],
    }),
  });

  readonly complementForm = new FormGroup({
    montant: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.01)],
    }),
    motif: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    effect(() => {
      const bienId = this.bienId();
      const bailId = this.bailId();
      this.fermerFormulaires();
      this.historiqueOuvert.set(null);
      this.chargerPour(bienId, bailId);
    });
  }

  ouvrir(g: Garantie, action: ActionGarantie): void {
    this.selection.set(g);
    this.action.set(action);
    if (action === 'RESTITUTION') {
      this.restitutionForm.reset({ type: 'TOTALE', montantRetenu: null, motifRetenue: null });
    } else if (action === 'RETENUE') {
      this.retenueForm.reset({ paiementId: '', montant: null });
      this.chargerImpayes();
    } else {
      this.complementForm.reset({ montant: null, motif: '' });
    }
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
      if (montantRetenu > g.soldeActuel) {
        this.message.set('Le montant retenu ne peut excéder le solde de la garantie');
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
          this.fermerFormulaires();
          this.chargerPour(this.bienId(), this.bailId());
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      });
  }

  retenir(): void {
    const g = this.selection();
    if (!g || this.retenueForm.invalid) {
      return;
    }
    const { paiementId, montant } = this.retenueForm.getRawValue();
    if (!montant || montant <= 0) {
      this.message.set('Retenue : montant > 0 requis');
      return;
    }
    if (montant > g.soldeActuel) {
      this.message.set('Le montant ne peut excéder le solde de la garantie');
      return;
    }
    const paiement = this.impayes().find((p) => p.id === paiementId);
    if (paiement && montant > paiement.resteDu) {
      this.message.set('Le montant ne peut excéder le reste dû du loyer');
      return;
    }
    this.chargement.set(true);
    this.message.set('Retenue…');
    this.api.retenirSurLoyer(this.bienId(), this.bailId(), g.id, { paiementId, montant }).subscribe({
      next: () => {
        this.message.set('Impayé couvert par la garantie');
        this.fermerFormulaires();
        this.chargerPour(this.bienId(), this.bailId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  complementer(): void {
    const g = this.selection();
    if (!g || this.complementForm.invalid) {
      return;
    }
    const { montant, motif } = this.complementForm.getRawValue();
    if (!montant || montant <= 0 || !motif.trim()) {
      this.message.set('Complément : montant (> 0) et motif requis');
      return;
    }
    this.chargement.set(true);
    this.message.set('Complément…');
    this.api.complementer(this.bienId(), this.bailId(), g.id, { montant, motif }).subscribe({
      next: () => {
        this.message.set('Garantie complétée');
        this.fermerFormulaires();
        this.chargerPour(this.bienId(), this.bailId());
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  basculerHistorique(g: Garantie): void {
    if (this.historiqueOuvert() === g.id) {
      this.historiqueOuvert.set(null);
      this.mouvements.set([]);
      return;
    }
    this.historiqueOuvert.set(g.id);
    this.mouvements.set([]);
    this.filtreType.set('');
    this.triColonne.set('dateMouvement');
    this.triSens.set(1);
    this.chargement.set(true);
    this.api.listerMouvements(this.bienId(), this.bailId(), g.id).subscribe({
      next: (mouvements) => {
        this.mouvements.set(mouvements);
        this.message.set(`${mouvements.length} mouvement(s)`);
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  trier(colonne: ColonneTri): void {
    if (this.triColonne() === colonne) {
      this.triSens.update((s) => (s === 1 ? -1 : 1));
    } else {
      this.triColonne.set(colonne);
      this.triSens.set(1);
    }
  }

  indicateurTri(colonne: ColonneTri): string {
    if (this.triColonne() !== colonne) {
      return '';
    }
    return this.triSens() === 1 ? ' ↑' : ' ↓';
  }

  filtrerType(event: Event): void {
    this.filtreType.set((event.target as HTMLSelectElement).value as TypeMouvementGarantie | '');
  }

  exporterCsv(g: Garantie): void {
    this.chargement.set(true);
    this.message.set('Export…');
    this.api.exporterMouvements(this.bienId(), this.bailId(), g.id).subscribe({
      next: (blob) => {
        this.enregistrerFichier(blob, `garantie-${g.id}-mouvements.csv`);
        this.message.set('Historique exporté');
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  private chargerImpayes(): void {
    this.chargement.set(true);
    this.api.listerPaiements(this.bienId()).subscribe({
      next: (paiements) => {
        this.impayes.set(
          paiements.filter(
            (p) => p.bailId === this.bailId() && (p.statut === 'IMPAYE' || p.statut === 'EN_RETARD'),
          ),
        );
      },
      error: (err: unknown) => this.signalerErreur(err),
      complete: () => this.chargement.set(false),
    });
  }

  private fermerFormulaires(): void {
    this.selection.set(null);
    this.action.set(null);
    this.impayes.set([]);
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

  private enregistrerFichier(blob: Blob, nom: string): void {
    const url = URL.createObjectURL(blob);
    const lien = document.createElement('a');
    lien.href = url;
    lien.download = nom;
    lien.click();
    URL.revokeObjectURL(url);
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
