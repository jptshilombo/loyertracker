import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { S03ApiService } from './s03-api.service';

describe('S03ApiService', () => {
  let service: S03ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), S03ApiService],
    });
    service = TestBed.inject(S03ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('liste les paiements d un bien', (done) => {
    service.listerPaiements('bien-1').subscribe((paiements) => {
      expect(paiements.length).toBe(1);
      expect(paiements[0].statut).toBe('EN_RETARD');
      done();
    });

    const req = http.expectOne('/api/biens/bien-1/paiements');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'p-1',
        bienId: 'bien-1',
        bailId: 'bail-1',
        periode: '2026-01',
        montantAttendu: 850,
        montantRecu: 0,
        resteDu: 850,
        dateExigibilite: '2026-02-01',
        statut: 'EN_RETARD',
      },
    ]);
  });

  it('pointe un loyer pour une periode', () => {
    service.pointer('bien-1', '2026-01', { montantRecu: 850, statut: 'RECU' }).subscribe();

    const req = http.expectOne('/api/biens/bien-1/paiements/2026-01/pointage');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ montantRecu: 850, statut: 'RECU' });
    req.flush({});
  });

  it('declenche la generation des echeances', (done) => {
    service.declencherEcheances().subscribe((res) => {
      expect(res.echeancesCreees).toBe(3);
      expect(res.loyersEnRetard).toBe(2);
      done();
    });

    const req = http.expectOne('/api/batch/echeances');
    expect(req.request.method).toBe('POST');
    req.flush({ echeancesCreees: 3, loyersEnRetard: 2 });
  });

  it('telecharge la quittance en PDF (blob)', () => {
    service.telechargerQuittance('bien-1', '2026-01').subscribe();
    const req = http.expectOne('/api/biens/bien-1/paiements/2026-01/quittance');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF-'], { type: 'application/pdf' }));
  });

  it('telecharge l avis d echeance en PDF (blob)', () => {
    service.telechargerAvisEcheance('bien-1', '2026-02').subscribe();
    const req = http.expectOne('/api/biens/bien-1/paiements/2026-02/avis-echeance');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['%PDF-'], { type: 'application/pdf' }));
  });

  it('liste depose et restitue une garantie', () => {
    service.listerGaranties('bien-1', 'bail-1').subscribe();
    let req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties');
    expect(req.request.method).toBe('GET');
    req.flush([]);

    service
      .deposerGarantie('bien-1', 'bail-1', {
        montant: 850,
        typeGarantie: 'CAUTION',
        dateDepot: '2026-01-01',
      })
      .subscribe();
    req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties');
    expect(req.request.method).toBe('POST');
    req.flush({});

    service
      .restituer('bien-1', 'bail-1', 'g-1', {
        type: 'PARTIELLE',
        montantRetenu: 100,
        motifRetenue: 'Dégâts',
      })
      .subscribe();
    req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties/g-1/restitution');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      type: 'PARTIELLE',
      montantRetenu: 100,
      motifRetenue: 'Dégâts',
    });
    req.flush({});
  });
});
