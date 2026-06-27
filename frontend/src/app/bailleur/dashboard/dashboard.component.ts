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
        <span>Inscription : {{ inscriptionStatus() }}</span>
      </div>
    </header>

    <section class="toolbar">
      <button type="button" (click)="chargerBiens()" [disabled]="chargement()">Rafraîchir</button>
      <span>{{ message() }}</span>
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
              <span>{{ bail.loyerCc }} · {{ bail.dateDebut }} → {{ bail.dateFin || 'en cours' }}</span>
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
    `,
  ],
})
export class BailleurDashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly inscription = inject(BailleurInscriptionService);
  private readonly api = inject(S02ApiService);

  readonly username = this.auth.getUsername();
  readonly roles = this.auth.roles;
  readonly inscriptionStatus = signal('en cours');
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
  readonly affectationsPatrimoine = signal<Record<string, Affectation[]>>({});
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
      this.inscriptionStatus.set('non applicable');
      this.chargerBiens();
      return;
    }

    this.chargerReferentielsBien();
    this.inscription.inscrire().pipe(
      finalize(() => this.chargerBiens()),
    ).subscribe({
      next: (result) => {
        this.inscriptionStatus.set(result.status === 'created' ? 'créée' : 'déjà existante');
      },
      error: (err: HttpErrorResponse) => this.inscriptionStatus.set(`erreur ${err.status}`),
    });
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
    if (!bien) {
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
    this.executer('Création de l affectation', () =>
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
    if (!bienId) {
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
