import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { BailleurDashboardComponent } from './dashboard.component';

describe('BailleurDashboardComponent', () => {
  let fixture: ComponentFixture<BailleurDashboardComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BailleurDashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            getUsername: () => 'alice',
            roles: ['BAILLEUR'],
            hasRole: (role: string) => role === 'BAILLEUR',
          },
        },
      ],
    });
    fixture = TestBed.createComponent(BailleurDashboardComponent);
    http = TestBed.inject(HttpTestingController);

    fixture.detectChanges(); // ngOnInit -> inscription, puis chargement biens/référentiels

    http.expectOne('/api/bailleurs/inscription').flush({});
    http.expectOne('/api/biens').flush([]);
    http
      .expectOne('/api/patrimoines')
      .flush([{ id: 'patrimoine-1', nom: 'Patrimoine principal', statut: 'ACTIF' }]);
    http
      .expectOne('/api/types-biens')
      .flush([{ code: 'APPARTEMENT', libelle: 'Appartement', actif: true }]);
    // Affectations patrimoine chargées après listerPatrimoines (Sprint 4 E2).
    http.expectOne('/api/patrimoines/patrimoine-1/affectations').flush([]);
    // Composants enfants toujours rendus dans le tableau de bord (alertes, audit).
    http.expectOne('/api/alertes').flush([]);
    http.expectOne('/api/audit').flush([]);
  });

  afterEach(() => {
    http.verify();
  });

  it('régression Hotfix 2026-06-24 : le formulaire bien envoie un patrimoineId', () => {
    const cmp = fixture.componentInstance;
    expect(cmp.bienForm.invalid).toBe(true); // patrimoineId vide par défaut → requis

    cmp.bienForm.setValue({
      adresse: '12 rue des Lilas',
      type: 'APPARTEMENT',
      statut: 'LIBRE',
      patrimoineId: 'patrimoine-1',
    });
    expect(cmp.bienForm.valid).toBe(true);

    cmp.enregistrerBien();

    const req = http.expectOne('/api/biens');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.patrimoineId).toBe('patrimoine-1');
    req.flush({
      id: 'bien-1',
      adresse: '12 rue des Lilas',
      type: 'APPARTEMENT',
      statut: 'LIBRE',
      patrimoineId: 'patrimoine-1',
    });

    http.expectOne('/api/biens').flush([]); // rechargement de la liste après création
  });

  it('pré-remplit patrimoine et type à la sélection d’un bien existant', () => {
    const cmp = fixture.componentInstance;
    cmp.selectionnerBien({
      id: 'bien-1',
      adresse: '12 rue des Lilas',
      type: 'APPARTEMENT',
      statut: 'LIBRE',
      patrimoineId: 'patrimoine-1',
    });

    http.expectOne('/api/biens/bien-1/baux').flush([]);
    http.expectOne('/api/biens/bien-1/affectations').flush([]);

    expect(cmp.bienForm.getRawValue().patrimoineId).toBe('patrimoine-1');
    expect(cmp.bienForm.valid).toBe(true);
  });

  it('expose le référentiel des types de biens actifs pour le sélecteur', () => {
    const cmp = fixture.componentInstance;
    expect(cmp.typesBiensDisponibles().map((t) => t.code)).toEqual(['APPARTEMENT']);
    expect(cmp.patrimoinesDisponibles().map((p) => p.id)).toEqual(['patrimoine-1']);
  });

  describe('Sprint 4 — affectations patrimoine et exceptions', () => {
    const affectationPatrimoineActive = {
      id: 'aff-pat-1',
      patrimoineId: 'patrimoine-1',
      bienId: null,
      gestionnaireId: 'gest-uuid-1',
      typeHonoraires: 'POURCENTAGE' as const,
      montantHonoraires: 10,
      dateDebut: '2026-01-01',
      dateFin: null,
      statut: 'ACTIVE',
      dateRevocation: null,
      typeException: null,
    };

    it('peuple le signal affectationsPatrimoine au démarrage', () => {
      expect(fixture.componentInstance.affectationsPatrimoine()['patrimoine-1']).toEqual([]);
    });

    it('section exception masquée si aucune affectation patrimoine active', () => {
      expect(fixture.componentInstance.patrimoinesAvecAffectationActive()).toEqual([]);
    });

    it('crée une affectation patrimoine et recharge', () => {
      const cmp = fixture.componentInstance;
      cmp.affectationPatrimoineForm.setValue({
        patrimoineId: 'patrimoine-1',
        gestionnaireId: 'gest-uuid-1',
        typeHonoraires: 'POURCENTAGE',
        montantHonoraires: 10,
        dateDebut: '2026-01-01',
        dateFin: '',
      });

      cmp.creerAffectationPatrimoine();

      const req = http.expectOne('/api/affectations');
      expect(req.request.method).toBe('POST');
      expect(req.request.body.patrimoineId).toBe('patrimoine-1');
      expect(req.request.body.bienId).toBeUndefined();
      req.flush(affectationPatrimoineActive);

      http.expectOne('/api/patrimoines/patrimoine-1/affectations').flush([]);
    });

    it("selectionnerPatrimoineException pré-remplit gestionnaireId depuis l'affectation active", () => {
      const cmp = fixture.componentInstance;
      cmp.affectationsPatrimoine.set({ 'patrimoine-1': [affectationPatrimoineActive] });

      cmp.selectionnerPatrimoineException('patrimoine-1');

      expect(cmp.patrimoineExceptionId()).toBe('patrimoine-1');
      expect(cmp.exceptionForm.value.gestionnaireId).toBe('gest-uuid-1');
      expect(cmp.patrimoinesAvecAffectationActive().map((p) => p.id)).toEqual(['patrimoine-1']);
    });

    it('crée une exception EXCLUSION sur un bien et recharge les exceptions', () => {
      const cmp = fixture.componentInstance;
      cmp.affectationsPatrimoine.set({ 'patrimoine-1': [affectationPatrimoineActive] });

      cmp.selectionnerPatrimoineException('patrimoine-1');
      cmp.selectionnerBienException('bien-1');
      http.expectOne('/api/biens/bien-1/affectations').flush([]);

      cmp.exceptionForm.patchValue({ montantHonoraires: 5, dateDebut: '2026-02-01', typeException: 'EXCLUSION' });

      cmp.creerException();

      const req = http.expectOne('/api/affectations');
      expect(req.request.method).toBe('POST');
      expect(req.request.body.bienId).toBe('bien-1');
      expect(req.request.body.typeException).toBe('EXCLUSION');
      expect(req.request.body.patrimoineId).toBeUndefined();
      req.flush({
        id: 'exc-1',
        bienId: 'bien-1',
        patrimoineId: null,
        gestionnaireId: 'gest-uuid-1',
        typeHonoraires: 'POURCENTAGE',
        montantHonoraires: 5,
        dateDebut: '2026-02-01',
        dateFin: null,
        statut: 'ACTIVE',
        dateRevocation: null,
        typeException: 'EXCLUSION',
      });

      http.expectOne('/api/biens/bien-1/affectations').flush([]);
    });

    it('révoque une affectation patrimoine et recharge les affectations', () => {
      const cmp = fixture.componentInstance;
      cmp.revoquerAffectationPatrimoine('aff-pat-1');

      const req = http.expectOne('/api/affectations/aff-pat-1/revocation');
      expect(req.request.method).toBe('POST');
      req.flush({ ...affectationPatrimoineActive, statut: 'REVOQUEE', dateRevocation: '2026-06-27' });

      http.expectOne('/api/patrimoines/patrimoine-1/affectations').flush([]);
    });
  });
});
