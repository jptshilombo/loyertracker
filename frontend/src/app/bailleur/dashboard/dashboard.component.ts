import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  Affectation,
  AffectationPayload,
  Bail,
  BailPayload,
  Bien,
  BienPayload,
  Patrimoine,
  PatrimoinePayload,
  S02ApiService,
  StatutBien,
  TypeBien,
  TypeHonoraires,
} from '../../core/s02/s02-api.service';
import { AlertesListeComponent } from '../../alertes/alertes-liste.component';
import { AuditJournalComponent } from '../../audit/audit-journal.component';
import { GarantiesBailComponent } from '../../garanties/garanties-bail.component';
import { HonorairesBienComponent } from '../../honoraires/honoraires-bien.component';
import { PaiementsBienComponent } from '../../paiements/paiements-bien.component';
import { BailleurInscriptionService } from '../inscription/bailleur-inscription.service';

@Component({
  selector: 'app-bailleur-dashboard',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PaiementsBienComponent,
    GarantiesBailComponent,
    HonorairesBienComponent,
    AlertesListeComponent,
    AuditJournalComponent,
  ],
  template: `
    <header class="page-head">
      <div>
        <h1>Espace bailleur</h1>
        <p>{{ username }} · {{ roles.join(', ') || 'aucun rôle' }}</p>
      </div>
      <div class="status">
        <a routerLink="/bailleur/profil">Mon profil</a>
      </div>
    </header>

    <section class="toolbar">
      <button type="button" (click)="chargerBiens()" [disabled]="chargement()">Rafraîchir</button>
      <span role="status" aria-live="polite" aria-atomic="true">{{ message() }}</span>
    </section>

    <section class="grid two">
      <form [formGroup]="bienForm" (ngSubmit)="enregistrerBien()" class="panel">
        <h2>{{ bienSelectionne() ? 'Modifier le bien' : 'Nouveau bien' }}</h2>
        <label>
          Adresse
          <input type="text" formControlName="adresse" />
        </label>
        <label>
          Type
          <select formControlName="type">
            @for (typeBien of typesBiensDisponibles(); track typeBien.code) {
              <option [value]="typeBien.code">{{ typeBien.libelle }}</option>
            }
          </select>
        </label>
        <label>
          Patrimoine
          <select formControlName="patrimoineId">
            <option value="" disabled>Choisir un patrimoine</option>
            @for (patrimoine of patrimoinesDisponibles(); track patrimoine.id) {
              <option [value]="patrimoine.id">{{ patrimoine.nom }}</option>
            }
          </select>
        </label>
        <label>
          Statut
          <select formControlName="statut">
            <option value="LIBRE">LIBRE</option>
            <option value="LOUE">LOUE</option>
            <option value="EN_TRAVAUX">EN_TRAVAUX</option>
            <option value="ARCHIVE">ARCHIVE</option>
          </select>
        </label>
        <div class="actions">
          <button type="submit" [disabled]="bienForm.invalid || chargement()">
            {{ bienSelectionne() ? 'Enregistrer' : 'Créer' }}
          </button>
          <button type="button" (click)="reinitialiserBien()">Nouveau</button>
        </div>
      </form>

      <div class="panel">
        <h2>Biens</h2>
        @if (biens().length === 0) {
          <p class="muted">Aucun bien.</p>
        }
        <div class="list">
          @for (bien of biens(); track bien.id) {
            <button
              type="button"
              class="row"
              [class.selected]="bien.id === bienSelectionne()?.id"
              (click)="selectionnerBien(bien)"
            >
              <span>
                <strong>{{ bien.adresse }}</strong>
                <small>{{ bien.type }}</small>
              </span>
              <span class="badge">{{ bien.statut }}</span>
            </button>
          }
        </div>
        @if (bienSelectionne()) {
          <button type="button" class="danger" (click)="archiverBien()">Archiver</button>
        }
      </div>
    </section>

    <section class="grid two detail">
      <form [formGroup]="patrimoineForm" (ngSubmit)="modifierPatrimoine()" class="panel">
        <h2>Modifier un patrimoine</h2>
        <label>
          Patrimoine
          <select #patModifSel (change)="selectionnerPatrimoineModif(patModifSel.value)">
            <option value="" disabled selected>Choisir un patrimoine</option>
            @for (p of patrimoinesDisponibles(); track p.id) {
              <option [value]="p.id">{{ p.nom }}{{ p.adresse ? ' — ' + p.adresse : '' }}</option>
            }
          </select>
        </label>
        @if (patrimoineModifId()) {
          <label>
            Nom
            <input type="text" formControlName="nom" />
          </label>
          <label>
            Adresse
            <input type="text" formControlName="adresse" placeholder="ex. 12 rue des Lilas, Paris" />
          </label>
          <label>
            Ville
            <input type="text" formControlName="ville" />
          </label>
          <label>
            Commune
            <input type="text" formControlName="commune" />
          </label>
          <label>
            Quartier
            <input type="text" formControlName="quartier" />
          </label>
          <label>
            Province / État
            <input type="text" formControlName="provinceEtat" />
          </label>
          <label>
            Pays
            <input type="text" formControlName="pays" />
          </label>
          <label>
            Référence interne
            <input type="text" formControlName="referenceInterne" />
          </label>
          <label>
            Description
            <textarea formControlName="description"></textarea>
          </label>
          <button type="submit" [disabled]="patrimoineForm.invalid || chargement()">Modifier</button>
        }
      </form>
      <div class="panel">
        <h2>Patrimoines</h2>
        @for (p of patrimoinesDisponibles(); track p.id) {
          <div class="item">
            <strong>{{ p.nom }}</strong>
            <span class="muted">{{ p.adresse }}{{ p.ville ? ', ' + p.ville : '' }}{{ p.pays ? ', ' + p.pays : '' }}</span>
            @if (p.referenceInterne) {
              <span class="muted">Réf. {{ p.referenceInterne }}</span>
            }
            <span class="badge">{{ p.statut }}</span>
          </div>
        } @empty {
          <p class="muted">Aucun patrimoine.</p>
        }
      </div>
    </section>

    @if (bienSelectionne(); as bien) {
      <section class="grid two detail">
        <form [formGroup]="bailForm" (ngSubmit)="creerBail()" class="panel">
          <h2>Bail · {{ bien.adresse }}</h2>
          <label>
            Locataire
            <input type="text" formControlName="locataireNom" />
          </label>
          <label>
            Email
            <input type="email" formControlName="locataireEmail" />
          </label>
          <div class="fields">
            <label>
              Loyer hors charges
              <input type="number" formControlName="loyerHc" min="0" step="0.01" />
            </label>
            <label>
              Provision charges
              <input type="number" formControlName="provisionCharges" min="0" step="0.01" />
            </label>
          </div>
          <div class="fields">
            <label>
              Dépôt
              <input type="number" formControlName="depotGarantie" min="0" step="0.01" />
            </label>
            <label>
              Devise
              <select formControlName="devise">
                <option value="EUR">EUR — Euro</option>
                <option value="USD">USD — Dollar américain</option>
                <option value="CDF">CDF — Franc congolais</option>
              </select>
            </label>
          </div>
          <div class="fields">
            <label>
              Début
              <input type="date" formControlName="dateDebut" />
            </label>
            <label>
              Fin
              <input type="date" formControlName="dateFin" />
            </label>
          </div>
          <button type="submit" [disabled]="bailForm.invalid || chargement()">Créer le bail</button>
        </form>

        <div class="panel">
          <h2>Historique des baux</h2>
          @for (bail of baux(); track bail.id) {
            <div class="item" [class.selected]="bail.id === bailSelectionne()?.id">
              <strong>{{ bail.locataireNom }}</strong>
              <span>{{ bail.loyerCc }} {{ bail.devise }} · {{ bail.dateDebut }} → {{ bail.dateFin || 'en cours' }}</span>
              <span class="badge">{{ bail.statut }}</span>
              <button type="button" (click)="selectionnerBail(bail)">Garanties</button>
            </div>
          } @empty {
            <p class="muted">Aucun bail.</p>
          }
        </div>
      </section>

      <section class="grid two detail">
        <app-paiements-bien [bienId]="bien.id" [peutDeclencher]="true" />
        @if (bailSelectionne(); as bail) {
          <app-garanties-bail [bienId]="bien.id" [bailId]="bail.id" />
        } @else {
          <div class="panel">
            <h2>Garanties</h2>
            <p class="muted">Choisir un bail (bouton « Garanties »).</p>
          </div>
        }
      </section>

      <section class="detail">
        <app-honoraires-bien [bienId]="bien.id" [peutValider]="true" />
      </section>

      <section class="grid two detail">
        <form [formGroup]="affectationForm" (ngSubmit)="creerAffectation()" class="panel">
          <h2>Affectation</h2>
          <label>
            Gestionnaire ID
            <input type="text" formControlName="gestionnaireId" />
          </label>
          <div class="fields">
            <label>
              Honoraires
              <select formControlName="typeHonoraires">
                <option value="POURCENTAGE">POURCENTAGE</option>
                <option value="FORFAIT">FORFAIT</option>
              </select>
            </label>
            <label>
              Montant
              <input type="number" formControlName="montantHonoraires" min="0" step="0.01" />
            </label>
          </div>
          <div class="fields">
            <label>
              Début
              <input type="date" formControlName="dateDebut" />
            </label>
            <label>
              Fin
              <input type="date" formControlName="dateFin" />
            </label>
          </div>
          <button type="submit" [disabled]="affectationForm.invalid || chargement()">
            Créer l'affectation
          </button>
        </form>

        <div class="panel">
          <h2>Historique affectations</h2>
          @for (affectation of affectations(); track affectation.id) {
            <div class="item">
              <strong>{{ affectation.gestionnaireId }}</strong>
              <span>
                {{ affectation.typeHonoraires }} {{ affectation.montantHonoraires }} ·
                {{ affectation.dateDebut }} → {{ affectation.dateFin || 'en cours' }}
              </span>
              <span class="badge">{{ affectation.statut }}</span>
              @if (affectation.statut === 'ACTIVE') {
                <button type="button" class="danger" (click)="revoquerAffectation(affectation.id)">
                  Révoquer
                </button>
              }
            </div>
          } @empty {
            <p class="muted">Aucune affectation.</p>
          }
        </div>
      </section>
    }

    <section class="grid two detail">
      <form [formGroup]="affectationPatrimoineForm" (ngSubmit)="creerAffectationPatrimoine()" class="panel">
        <h2>Affectation patrimoine</h2>
        <label>
          Patrimoine
          <select formControlName="patrimoineId">
            <option value="" disabled>Choisir un patrimoine</option>
            @for (p of patrimoinesDisponibles(); track p.id) {
              <option [value]="p.id">{{ p.nom }}</option>
            }
          </select>
        </label>
        <label>
          Gestionnaire ID
          <input type="text" formControlName="gestionnaireId" autocomplete="off" aria-describedby="gestionnaire-patrimoine-aide" [attr.aria-invalid]="affectationPatrimoineForm.controls.gestionnaireId.invalid && affectationPatrimoineForm.controls.gestionnaireId.touched" />
          <small id="gestionnaire-patrimoine-aide" class="field-help">UUID du gestionnaire, au format fourni lors de sa création.</small>
          @if (affectationPatrimoineForm.controls.gestionnaireId.invalid && affectationPatrimoineForm.controls.gestionnaireId.touched) {
            <small class="field-error">Identifiant gestionnaire requis.</small>
          }
        </label>
        <div class="fields">
          <label>
            Honoraires
            <select formControlName="typeHonoraires">
              <option value="POURCENTAGE">POURCENTAGE</option>
              <option value="FORFAIT">FORFAIT</option>
            </select>
          </label>
          <label>
            Montant
            <input type="number" formControlName="montantHonoraires" min="0" step="0.01" />
          </label>
        </div>
        <div class="fields">
          <label>
            Début
            <input type="date" formControlName="dateDebut" />
          </label>
          <label>
            Fin
            <input type="date" formControlName="dateFin" />
          </label>
        </div>
        <button type="submit" [disabled]="affectationPatrimoineForm.invalid || chargement()">
          Créer l'affectation
        </button>
      </form>

      <div class="panel">
        <h2>Affectations patrimoine</h2>
        @for (p of patrimoinesDisponibles(); track p.id) {
          @for (aff of (affectationsPatrimoine()[p.id] ?? []); track aff.id) {
            <div class="item">
              <span>
                <strong>{{ p.nom }}</strong>
                <small>{{ aff.gestionnaireId }}</small>
              </span>
              <span>
                {{ aff.typeHonoraires }} {{ aff.montantHonoraires }} ·
                {{ aff.dateDebut }} → {{ aff.dateFin || 'en cours' }}
              </span>
              <span class="badge">{{ aff.statut }}</span>
              @if (aff.statut === 'ACTIVE') {
                <button type="button" class="danger" (click)="revoquerAffectationPatrimoine(aff.id)">
                  Révoquer
                </button>
              }
            </div>
          }
        }
        @if (patrimoinesAvecAffectationActive().length === 0) {
          <p class="muted">Aucune affectation patrimoine active.</p>
        }
      </div>
    </section>

    @if (patrimoinesAvecAffectationActive().length > 0) {
      <section class="grid two detail">
        <form [formGroup]="exceptionForm" (ngSubmit)="creerException()" class="panel">
          <h2>Exception sur bien</h2>
          <label>
            Patrimoine affecté
            <select
              #patrimoineExcSel
              formControlName="patrimoineId"
              (change)="selectionnerPatrimoineException(patrimoineExcSel.value)"
            >
              <option value="" disabled>Choisir un patrimoine</option>
              @for (p of patrimoinesAvecAffectationActive(); track p.id) {
                <option [value]="p.id">{{ p.nom }}</option>
              }
            </select>
          </label>
          <label>
            Bien
            <select
              #bienExcSel
              formControlName="bienId"
              (change)="selectionnerBienException(bienExcSel.value)"
              [attr.disabled]="patrimoineExceptionId() ? null : true"
            >
              <option value="" disabled>Choisir un bien</option>
              @for (b of biensPatrimoineException(); track b.id) {
                <option [value]="b.id">{{ b.adresse }}</option>
              }
            </select>
          </label>
          <label>
            Gestionnaire ID
            <input type="text" formControlName="gestionnaireId" autocomplete="off" aria-describedby="gestionnaire-exception-aide" [attr.aria-invalid]="exceptionForm.controls.gestionnaireId.invalid && exceptionForm.controls.gestionnaireId.touched" />
            <small id="gestionnaire-exception-aide" class="field-help">UUID prérempli depuis l’affectation patrimoine active ; modifiable pour une inclusion ciblée.</small>
            @if (exceptionForm.controls.gestionnaireId.invalid && exceptionForm.controls.gestionnaireId.touched) {
              <small class="field-error">Identifiant gestionnaire requis.</small>
            }
          </label>
          <label>
            Type d’exception
            <select formControlName="typeException">
              <option value="EXCLUSION">EXCLUSION</option>
              <option value="INCLUSION">INCLUSION</option>
            </select>
          </label>
          <div class="fields">
            <label>
              Honoraires
              <select formControlName="typeHonoraires">
                <option value="POURCENTAGE">POURCENTAGE</option>
                <option value="FORFAIT">FORFAIT</option>
              </select>
            </label>
            <label>
              Montant
              <input type="number" formControlName="montantHonoraires" min="0" step="0.01" />
            </label>
          </div>
          <div class="fields">
            <label>
              Début
              <input type="date" formControlName="dateDebut" />
            </label>
            <label>
              Fin
              <input type="date" formControlName="dateFin" />
            </label>
          </div>
          <button type="submit" [disabled]="exceptionForm.invalid || chargement()">
            Créer l'exception
          </button>
        </form>

        <div class="panel">
          <h2>Exceptions du bien</h2>
          @if (!bienExceptionId()) {
            <p class="muted">Choisir un bien pour voir ses exceptions.</p>
          }
          @for (aff of exceptionsBien(); track aff.id) {
            <div class="item">
              <span class="badge">{{ aff.typeException }}</span>
              <strong>{{ aff.gestionnaireId }}</strong>
              <span>{{ aff.dateDebut }} → {{ aff.dateFin || 'en cours' }}</span>
              <span class="badge">{{ aff.statut }}</span>
              @if (aff.statut === 'ACTIVE') {
                <button type="button" class="danger" (click)="revoquerException(aff.id)">
                  Révoquer
                </button>
              }
            </div>
          } @empty {
            @if (bienExceptionId()) {
              <p class="muted">Aucune exception pour ce bien.</p>
            }
          }
        </div>
      </section>
    }

    <section class="grid two detail">
      <app-alertes-liste [peutGenerer]="true" />
      <app-audit-journal />
    </section>
  `,
  styles: [
    `
      .page-head,
      .toolbar,
      .actions,
      .fields,
      .row,
      .item {
        display: flex;
        gap: 0.75rem;
      }
      .page-head,
      .toolbar,
      .row,
      .item {
        align-items: center;
        justify-content: space-between;
      }
      h1,
      h2,
      p {
        margin-top: 0;
      }
      .grid {
        display: grid;
        gap: 1rem;
        margin-top: 1rem;
      }
      .two {
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      }
      .panel {
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 1rem;
        background: #111827;
      }
      label {
        display: grid;
        gap: 0.35rem;
        margin-bottom: 0.75rem;
        color: #cbd5e1;
      }
      input,
      select {
        width: 100%;
        border: 1px solid #334155;
        border-radius: 6px;
        padding: 0.5rem;
        background: #0f172a;
        color: #e2e8f0;
      }
      .list {
        display: grid;
        gap: 0.5rem;
      }
      .row {
        width: 100%;
        text-align: left;
      }
      .selected {
        border-color: #38bdf8;
      }
      .badge,
      .status {
        color: #bae6fd;
        font-size: 0.85rem;
      }
      .muted,
      small {
        color: #94a3b8;
      }
      .danger {
        border-color: #7f1d1d;
        color: #fecaca;
      }
      .field-help {
        color: #94a3b8;
      }
      .field-error {
        color: #fecaca;
        font-weight: 600;
      }
      @media (max-width: 640px) {
        .page-head,
        .toolbar,
        .fields,
        .item {
          align-items: stretch;
          flex-direction: column;
        }
        .status {
          display: grid;
          gap: 0.5rem;
        }
      }
    `,
  ],
})
export class BailleurDashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly inscription = inject(BailleurInscriptionService);
  private readonly api = inject(S02ApiService);

  readonly username = this.auth.getUsername();
  readonly roles = this.auth.roles;
  readonly message = signal('Prêt');
  readonly chargement = signal(false);
  readonly biens = signal<Bien[]>([]);
  readonly patrimoines = signal<Patrimoine[]>([]);
  readonly typesBiens = signal<TypeBien[]>([]);
  readonly patrimoinesDisponibles = computed(() => {
    const selectionne = this.bienSelectionne()?.patrimoineId;
    return this.patrimoines().filter((p) => p.statut === 'ACTIF' || p.id === selectionne);
  });
  readonly typesBiensDisponibles = computed(() => {
    const selectionne = this.bienSelectionne()?.type;
    return this.typesBiens().filter((t) => t.actif || t.code === selectionne);
  });
  readonly baux = signal<Bail[]>([]);
  readonly affectations = signal<Affectation[]>([]);
  readonly bienSelectionne = signal<Bien | null>(null);
  readonly bienSelectionneId = computed(() => this.bienSelectionne()?.id ?? null);
  readonly bailSelectionne = signal<Bail | null>(null);
  readonly affectationsPatrimoine = signal<Partial<Record<string, Affectation[]>>>({});
  readonly patrimoineExceptionId = signal('');
  readonly bienExceptionId = signal('');
  readonly exceptionsBien = signal<Affectation[]>([]);

  readonly patrimoinesAvecAffectationActive = computed(() => {
    const parPatrimoine = this.affectationsPatrimoine();
    return this.patrimoines().filter(p =>
      (parPatrimoine[p.id] ?? []).some(a => a.statut === 'ACTIVE'),
    );
  });

  readonly biensPatrimoineException = computed(() =>
    this.biens().filter(b => b.patrimoineId === this.patrimoineExceptionId()),
  );

  selectionnerBail(bail: Bail): void {
    this.bailSelectionne.set(bail);
  }

  readonly bienForm = new FormGroup({
    adresse: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    type: new FormControl('APPARTEMENT', { nonNullable: true, validators: [Validators.required] }),
    statut: new FormControl<StatutBien>('LIBRE', { nonNullable: true, validators: [Validators.required] }),
    patrimoineId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly bailForm = new FormGroup({
    locataireNom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    locataireEmail: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    loyerHc: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    provisionCharges: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    depotGarantie: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    dateDebut: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    dateFin: new FormControl('', { nonNullable: true }),
    devise: new FormControl<string>('EUR', { nonNullable: true, validators: [Validators.required] }),
  });

  readonly affectationForm = new FormGroup({
    gestionnaireId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    typeHonoraires: new FormControl<TypeHonoraires>('POURCENTAGE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    montantHonoraires: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    dateDebut: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    dateFin: new FormControl('', { nonNullable: true }),
  });

  readonly affectationPatrimoineForm = new FormGroup({
    patrimoineId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    gestionnaireId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    typeHonoraires: new FormControl<TypeHonoraires>('POURCENTAGE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    montantHonoraires: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    dateDebut: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    dateFin: new FormControl('', { nonNullable: true }),
  });

  readonly patrimoineModifId = signal('');

  readonly patrimoineForm = new FormGroup({
    nom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    adresse: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    ville: new FormControl<string | null>(null, { nonNullable: false }),
    commune: new FormControl<string | null>(null, { nonNullable: false }),
    quartier: new FormControl<string | null>(null, { nonNullable: false }),
    provinceEtat: new FormControl<string | null>(null, { nonNullable: false }),
    pays: new FormControl<string | null>(null, { nonNullable: false }),
    description: new FormControl<string | null>(null, { nonNullable: false }),
    referenceInterne: new FormControl<string | null>(null, { nonNullable: false }),
  });

  readonly exceptionForm = new FormGroup({
    patrimoineId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    bienId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    gestionnaireId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    typeHonoraires: new FormControl<TypeHonoraires>('POURCENTAGE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    montantHonoraires: new FormControl(0, { nonNullable: true, validators: [Validators.min(0)] }),
    dateDebut: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    dateFin: new FormControl('', { nonNullable: true }),
    typeException: new FormControl<'INCLUSION' | 'EXCLUSION'>('EXCLUSION', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  ngOnInit(): void {
    if (!this.auth.hasRole('BAILLEUR')) {
      this.chargerBiens();
      return;
    }

    this.chargerReferentielsBien();
    this.inscription.inscrire().pipe(
      finalize(() => this.chargerBiens()),
    ).subscribe();
  }

  private chargerReferentielsBien(): void {
    this.api.listerPatrimoines().subscribe({
      next: (patrimoines) => {
        this.patrimoines.set(patrimoines);
        this.chargerAffectationsPatrimoine(patrimoines);
      },
    });
    this.api.listerTypesBiens().subscribe({ next: (typesBiens) => this.typesBiens.set(typesBiens) });
  }

  private chargerAffectationsPatrimoine(patrimoines: Patrimoine[] = this.patrimoines()): void {
    if (!patrimoines.length) {
      return;
    }
    forkJoin(
      patrimoines.map(p =>
        this.api.listerAffectationsPatrimoine(p.id).pipe(map(a => [p.id, a] as const)),
      ),
    ).subscribe({
      next: (resultats) => this.affectationsPatrimoine.set(Object.fromEntries(resultats)),
      error: (err: unknown) => this.signalerErreur(err),
    });
  }

  private chargerExceptionsBien(bienId: string): void {
    this.api.listerAffectations(bienId).subscribe({
      next: (affectations) =>
        this.exceptionsBien.set(affectations.filter(a => a.typeException !== null)),
      error: (err: unknown) => this.signalerErreur(err),
    });
  }

  chargerBiens(): void {
    this.executer('Chargement des biens', () =>
      this.api.listerBiens().subscribe({
        next: (biens) => {
          this.biens.set(biens);
          this.message.set(`${biens.length} bien(s)`);
          const selection = this.bienSelectionneId();
          if (selection && !biens.some((bien) => bien.id === selection)) {
            this.reinitialiserBien();
          }
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  selectionnerBien(bien: Bien): void {
    this.bienSelectionne.set(bien);
    this.bailSelectionne.set(null);
    this.bienForm.setValue({
      adresse: bien.adresse,
      type: bien.type,
      statut: bien.statut,
      patrimoineId: bien.patrimoineId,
    });
    this.chargerDetails(bien.id);
  }

  reinitialiserBien(): void {
    this.bienSelectionne.set(null);
    this.bailSelectionne.set(null);
    this.bienForm.reset({ adresse: '', type: 'APPARTEMENT', statut: 'LIBRE', patrimoineId: '' });
    this.baux.set([]);
    this.affectations.set([]);
  }

  enregistrerBien(): void {
    if (this.bienForm.invalid) {
      return;
    }
    const payload = this.bienPayload();
    const selection = this.bienSelectionne();
    const action = selection
      ? this.api.modifierBien(selection.id, payload)
      : this.api.creerBien(payload);

    this.executer('Enregistrement du bien', () =>
      action.subscribe({
        next: (bien) => {
          this.message.set(selection ? 'Bien modifié' : 'Bien créé');
          this.bienSelectionne.set(bien);
          this.chargerBiens();
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  archiverBien(): void {
    const bien = this.bienSelectionne();
    if (!bien || !this.confirmerAction('Archiver ce bien ?')) {
      return;
    }
    this.executer('Archivage du bien', () =>
      this.api.archiverBien(bien.id).subscribe({
        next: (archive) => {
          this.message.set('Bien archivé');
          this.bienSelectionne.set(archive);
          this.bienForm.patchValue({ statut: archive.statut });
          this.chargerBiens();
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  creerBail(): void {
    const bienId = this.bienSelectionneId();
    if (!bienId || this.bailForm.invalid) {
      return;
    }
    this.executer('Création du bail', () =>
      this.api.creerBail(bienId, this.bailPayload()).subscribe({
        next: () => {
          this.message.set('Bail créé');
          this.bailForm.reset({
            locataireNom: '',
            locataireEmail: '',
            loyerHc: 0,
            provisionCharges: 0,
            depotGarantie: 0,
            dateDebut: '',
            dateFin: '',
            devise: 'EUR',
          });
          this.chargerDetails(bienId);
          this.chargerBiens();
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  creerAffectation(): void {
    const bienId = this.bienSelectionneId();
    if (!bienId || this.affectationForm.invalid) {
      return;
    }
    this.executer('Création de l’affectation', () =>
      this.api.creerAffectation(this.affectationPayload(bienId)).subscribe({
        next: () => {
          this.message.set('Affectation créée');
          this.affectationForm.reset({
            gestionnaireId: '',
            typeHonoraires: 'POURCENTAGE',
            montantHonoraires: 0,
            dateDebut: '',
            dateFin: '',
          });
          this.chargerDetails(bienId);
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  revoquerAffectation(id: string): void {
    const bienId = this.bienSelectionneId();
    if (!bienId || !this.confirmerAction('Révoquer cette affectation ?')) {
      return;
    }
    this.executer('Révocation', () =>
      this.api.revoquerAffectation(id).subscribe({
        next: () => {
          this.message.set('Affectation révoquée');
          this.chargerDetails(bienId);
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  creerAffectationPatrimoine(): void {
    if (this.affectationPatrimoineForm.invalid) {
      return;
    }
    const form = this.affectationPatrimoineForm.getRawValue();
    const payload: AffectationPayload = {
      patrimoineId: form.patrimoineId,
      gestionnaireId: form.gestionnaireId,
      typeHonoraires: form.typeHonoraires,
      montantHonoraires: form.montantHonoraires,
      dateDebut: form.dateDebut,
      dateFin: form.dateFin || null,
    };
    this.executer('Création affectation patrimoine', () =>
      this.api.creerAffectation(payload).subscribe({
        next: () => {
          this.message.set('Affectation patrimoine créée');
          this.affectationPatrimoineForm.reset({
            patrimoineId: '',
            gestionnaireId: '',
            typeHonoraires: 'POURCENTAGE',
            montantHonoraires: 0,
            dateDebut: '',
            dateFin: '',
          });
          this.chargerAffectationsPatrimoine();
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  revoquerAffectationPatrimoine(id: string): void {
    if (!this.confirmerAction('Révoquer cette affectation patrimoine ?')) {
      return;
    }
    this.executer('Révocation', () =>
      this.api.revoquerAffectation(id).subscribe({
        next: () => {
          this.message.set('Affectation révoquée');
          this.chargerAffectationsPatrimoine();
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  selectionnerPatrimoineException(patrimoineId: string): void {
    this.patrimoineExceptionId.set(patrimoineId);
    this.bienExceptionId.set('');
    this.exceptionsBien.set([]);
    const gestionnaireActif = (this.affectationsPatrimoine()[patrimoineId] ?? []).find(
      a => a.statut === 'ACTIVE',
    )?.gestionnaireId ?? '';
    this.exceptionForm.patchValue({ patrimoineId, bienId: '', gestionnaireId: gestionnaireActif });
  }

  selectionnerBienException(bienId: string): void {
    this.bienExceptionId.set(bienId);
    this.exceptionForm.patchValue({ bienId });
    this.chargerExceptionsBien(bienId);
  }

  creerException(): void {
    if (this.exceptionForm.invalid) {
      return;
    }
    const form = this.exceptionForm.getRawValue();
    const payload: AffectationPayload = {
      bienId: form.bienId,
      gestionnaireId: form.gestionnaireId,
      typeHonoraires: form.typeHonoraires,
      montantHonoraires: form.montantHonoraires,
      dateDebut: form.dateDebut,
      dateFin: form.dateFin || null,
      typeException: form.typeException,
    };
    this.executer("Création de l'exception", () =>
      this.api.creerAffectation(payload).subscribe({
        next: () => {
          this.message.set('Exception créée');
          this.exceptionForm.patchValue({
            gestionnaireId: '',
            montantHonoraires: 0,
            dateDebut: '',
            dateFin: '',
            typeException: 'EXCLUSION',
          });
          this.chargerExceptionsBien(form.bienId);
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  revoquerException(id: string): void {
    const bienId = this.bienExceptionId();
    if (!this.confirmerAction('Révoquer cette exception ?')) {
      return;
    }
    this.executer('Révocation exception', () =>
      this.api.revoquerAffectation(id).subscribe({
        next: () => {
          this.message.set('Exception révoquée');
          if (bienId) {
            this.chargerExceptionsBien(bienId);
          }
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  selectionnerPatrimoineModif(patrimoineId: string): void {
    this.patrimoineModifId.set(patrimoineId);
    const p = this.patrimoines().find(pat => pat.id === patrimoineId);
    if (p) {
      this.patrimoineForm.setValue({
        nom: p.nom,
        adresse: p.adresse,
        ville: p.ville ?? null,
        commune: p.commune ?? null,
        quartier: p.quartier ?? null,
        provinceEtat: p.provinceEtat ?? null,
        pays: p.pays ?? null,
        description: p.description ?? null,
        referenceInterne: p.referenceInterne ?? null,
      });
    }
  }

  modifierPatrimoine(): void {
    const id = this.patrimoineModifId();
    if (!id || this.patrimoineForm.invalid) {
      return;
    }
    const valeurs = this.patrimoineForm.getRawValue();
    const payload: PatrimoinePayload = {
      nom: valeurs.nom,
      adresse: valeurs.adresse,
      ville: valeurs.ville,
      commune: valeurs.commune,
      quartier: valeurs.quartier,
      provinceEtat: valeurs.provinceEtat,
      pays: valeurs.pays,
      description: valeurs.description,
      referenceInterne: valeurs.referenceInterne,
    };
    this.executer('Modification du patrimoine', () =>
      this.api.modifierPatrimoine(id, payload).subscribe({
        next: (p) => {
          this.message.set(`Patrimoine « ${p.nom} » modifié`);
          this.chargerReferentielsBien();
        },
        error: (err: unknown) => this.signalerErreur(err),
        complete: () => this.chargement.set(false),
      }),
    );
  }

  private chargerDetails(bienId: string): void {
    this.api.listerBaux(bienId).subscribe({
      next: (baux) => this.baux.set(baux),
      error: (err: unknown) => this.signalerErreur(err),
    });
    this.api.listerAffectations(bienId).subscribe({
      next: (affectations) => this.affectations.set(affectations),
      error: (err: unknown) => this.signalerErreur(err),
    });
  }

  private bienPayload(): BienPayload {
    return this.bienForm.getRawValue();
  }

  private bailPayload(): BailPayload {
    const form = this.bailForm.getRawValue();
    return {
      ...form,
      locataireEmail: form.locataireEmail || null,
      dateFin: form.dateFin || null,
    };
  }

  private affectationPayload(bienId: string): AffectationPayload {
    const form = this.affectationForm.getRawValue();
    return { ...form, bienId, dateFin: form.dateFin || null };
  }

  private confirmerAction(message: string): boolean {
    return globalThis.confirm(message);
  }

  private executer(message: string, action: () => void): void {
    this.chargement.set(true);
    this.message.set(message);
    action();
  }

  private signalerErreur(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const detail = err.status === 409 ? 'conflit métier' : err.status === 403 ? 'accès refusé' : 'erreur API';
      this.message.set(`${detail} (${err.status})`);
      this.chargement.set(false);
      return;
    }
    this.message.set('erreur inconnue');
    this.chargement.set(false);
  }
}
