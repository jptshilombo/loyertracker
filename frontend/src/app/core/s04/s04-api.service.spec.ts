import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { S04ApiService } from './s04-api.service';

describe('S04ApiService', () => {
  let service: S04ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), S04ApiService],
    });
    service = TestBed.inject(S04ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('liste les honoraires d un bien', (done) => {
    service.listerHonoraires('bien-1').subscribe((honoraires) => {
      expect(honoraires.length).toBe(1);
      expect(honoraires[0].statut).toBe('DU');
      done();
    });

    const req = http.expectOne('/api/biens/bien-1/honoraires');
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 'h-1', affectationId: 'a-1', periode: '2026-01', montant: 85, statut: 'DU' },
    ]);
  });

  it('change le statut d un honoraire', () => {
    service.changerStatutHonoraire('h-1', 'PAYE').subscribe();

    const req = http.expectOne('/api/honoraires/h-1/statut');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ statut: 'PAYE' });
    req.flush({});
  });

  it('declenche le recalcul des honoraires', (done) => {
    service.recalculerHonoraires().subscribe((res) => {
      expect(res.honorairesCalcules).toBe(4);
      done();
    });

    const req = http.expectOne('/api/batch/honoraires');
    expect(req.request.method).toBe('POST');
    req.flush({ honorairesCalcules: 4 });
  });

  it('liste les alertes et marque une alerte lue', () => {
    service.listerAlertes().subscribe();
    let req = http.expectOne('/api/alertes');
    expect(req.request.method).toBe('GET');
    req.flush([]);

    service.marquerAlerteLue('al-1').subscribe();
    req = http.expectOne('/api/alertes/al-1/lecture');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('declenche la generation des alertes', (done) => {
    service.genererAlertes().subscribe((res) => {
      expect(res.alertesCreees).toBe(3);
      done();
    });

    const req = http.expectOne('/api/batch/alertes');
    expect(req.request.method).toBe('POST');
    req.flush({ alertesCreees: 3 });
  });

  it('liste le journal d audit', (done) => {
    service.listerAudit().subscribe((entrees) => {
      expect(entrees.length).toBe(1);
      expect(entrees[0].action).toBe('POINTER_PAIEMENT');
      done();
    });

    const req = http.expectOne('/api/audit');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'au-1',
        acteurId: 'act-1',
        acteurRole: 'BAILLEUR',
        action: 'POINTER_PAIEMENT',
        entityType: 'paiement',
        entityId: 'p-1',
        horodatage: '2026-06-10T08:00:00Z',
      },
    ]);
  });
});
