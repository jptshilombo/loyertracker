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
});
