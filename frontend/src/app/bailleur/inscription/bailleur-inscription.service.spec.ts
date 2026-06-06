import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BailleurInscriptionService } from './bailleur-inscription.service';

describe('BailleurInscriptionService', () => {
  let service: BailleurInscriptionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), BailleurInscriptionService],
    });
    service = TestBed.inject(BailleurInscriptionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('retourne created quand l inscription API repond 201', (done) => {
    service.inscrire().subscribe((result) => {
      expect(result.status).toBe('created');
      done();
    });

    const req = http.expectOne('/api/bailleurs/inscription');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'bailleur-id' }, { status: 201, statusText: 'Created' });
  });

  it('traite le conflit 409 comme un bailleur deja inscrit', (done) => {
    service.inscrire().subscribe((result) => {
      expect(result.status).toBe('already-registered');
      done();
    });

    const req = http.expectOne('/api/bailleurs/inscription');
    req.flush({}, { status: 409, statusText: 'Conflict' });
  });

  it('propage les autres erreurs API', (done) => {
    service.inscrire().subscribe({
      next: () => fail('expected an error'),
      error: (error: HttpErrorResponse) => {
        expect(error.status).toBe(500);
        done();
      },
    });

    const req = http.expectOne('/api/bailleurs/inscription');
    req.flush({}, { status: 500, statusText: 'Server Error' });
  });
});
