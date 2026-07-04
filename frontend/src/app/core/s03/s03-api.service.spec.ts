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

  it('retient sur un loyer impayé (US-95)', () => {
    service
      .retenirSurLoyer('bien-1', 'bail-1', 'g-1', { paiementId: 'p-1', montant: 850 })
      .subscribe();

    const req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties/g-1/retenue-loyer');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ paiementId: 'p-1', montant: 850 });
    req.flush({});
  });

  it('complète une garantie active (US-96)', () => {
    service
      .complementer('bien-1', 'bail-1', 'g-1', { montant: 150, motif: 'Réapprovisionnement' })
      .subscribe();

    const req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties/g-1/complement');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ montant: 150, motif: 'Réapprovisionnement' });
    req.flush({});
  });

  it('liste les mouvements du ledger d une garantie (US-97)', (done) => {
    service.listerMouvements('bien-1', 'bail-1', 'g-1').subscribe((mouvements) => {
      expect(mouvements.length).toBe(1);
      expect(mouvements[0].type).toBe('DEPOT_INITIAL');
      done();
    });

    const req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties/g-1/mouvements');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'm-1',
        garantieId: 'g-1',
        dateMouvement: '2026-01-01T10:00:00Z',
        type: 'DEPOT_INITIAL',
        debit: 0,
        credit: 850,
        soldeApres: 850,
        motif: 'Dépôt initial de la garantie',
        utilisateur: 'bailleur@exemple.org',
        commentaire: null,
        referenceDocument: null,
      },
    ]);
  });

  it('exporte les mouvements en CSV (blob, US-97)', () => {
    service.exporterMouvements('bien-1', 'bail-1', 'g-1').subscribe();

    const req = http.expectOne('/api/biens/bien-1/baux/bail-1/garanties/g-1/mouvements/export');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['date;type\n'], { type: 'text/csv' }));
  });
});
