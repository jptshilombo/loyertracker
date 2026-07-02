import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BailPayload, S02ApiService } from './s02-api.service';

describe('S02ApiService', () => {
  let service: S02ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), S02ApiService],
    });
    service = TestBed.inject(S02ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('liste les biens', (done) => {
    service.listerBiens().subscribe((biens) => {
      expect(biens.length).toBe(1);
      expect(biens[0].adresse).toBe('10 rue A');
      done();
    });

    const req = http.expectOne('/api/biens');
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 'bien-1', adresse: '10 rue A', type: 'APPARTEMENT', statut: 'LIBRE', patrimoineId: 'patrimoine-1' },
    ]);
  });

  it('liste les patrimoines et les types de biens', () => {
    service.listerPatrimoines().subscribe();
    let req = http.expectOne('/api/patrimoines');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'patrimoine-1', nom: 'Patrimoine principal', statut: 'ACTIF' }]);

    service.listerTypesBiens().subscribe();
    req = http.expectOne('/api/types-biens');
    expect(req.request.method).toBe('GET');
    req.flush([{ code: 'APPARTEMENT', libelle: 'Appartement', actif: true }]);
  });

  it('cree modifie et archive un bien', () => {
    const payload = {
      adresse: '20 rue B',
      type: 'MAISON',
      statut: 'LIBRE' as const,
      patrimoineId: 'patrimoine-1',
    };

    service.creerBien(payload).subscribe();
    let req = http.expectOne('/api/biens');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'bien-2', ...payload });

    service.modifierBien('bien-2', { ...payload, statut: 'EN_TRAVAUX' }).subscribe();
    req = http.expectOne('/api/biens/bien-2');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 'bien-2', ...payload, statut: 'EN_TRAVAUX' });

    service.archiverBien('bien-2').subscribe();
    req = http.expectOne('/api/biens/bien-2/archivage');
    expect(req.request.method).toBe('PATCH');
    req.flush({ id: 'bien-2', ...payload, statut: 'ARCHIVE' });
  });

  it('cree et liste les baux', () => {
    const payload: BailPayload = {
      locataireNom: 'Locataire',
      locataireEmail: 'locataire@test.local',
      loyerHc: 850,
      provisionCharges: 0,
      dateDebut: '2026-06-01',
      dateFin: null,
      devise: 'EUR',
    };

    service.creerBail('bien-1', payload).subscribe();
    let req = http.expectOne('/api/biens/bien-1/baux');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'bail-1', bienId: 'bien-1', ...payload, loyerCc: 850, statut: 'ACTIF' });

    service.listerBaux('bien-1').subscribe();
    req = http.expectOne('/api/biens/bien-1/baux');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('cree revoque et liste les affectations', () => {
    const payload = {
      bienId: 'bien-1',
      gestionnaireId: 'gestionnaire-1',
      typeHonoraires: 'POURCENTAGE' as const,
      montantHonoraires: 10,
      dateDebut: '2026-06-01',
      dateFin: null,
    };

    service.creerAffectation(payload).subscribe();
    let req = http.expectOne('/api/affectations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'affectation-1', ...payload, statut: 'ACTIVE', dateRevocation: null });

    service.revoquerAffectation('affectation-1').subscribe();
    req = http.expectOne('/api/affectations/affectation-1/revocation');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'affectation-1', ...payload, statut: 'REVOQUEE', dateRevocation: '2026-06-07T00:00:00Z' });

    service.listerAffectations('bien-1').subscribe();
    req = http.expectOne('/api/biens/bien-1/affectations');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
