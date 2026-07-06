import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { VerifyReceiptService } from './verify-receipt.service';

describe('VerifyReceiptService', () => {
  let service: VerifyReceiptService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), VerifyReceiptService],
    });
    service = TestBed.inject(VerifyReceiptService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('vérifie une quittance avec le token en paramètre', () => {
    service.verifier('q-1', 'jeton-abc').subscribe((r) => expect(r.resultat).toBe('VALIDE'));

    const req = http.expectOne((r) => r.url === '/api/public/receipts/q-1');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('token')).toBe('jeton-abc');
    req.flush({ resultat: 'VALIDE', quittance: null });
  });

  it('n’ajoute pas de paramètre token quand il est absent', () => {
    service.verifier('q-1', null).subscribe();

    const req = http.expectOne((r) => r.url === '/api/public/receipts/q-1');
    expect(req.request.params.has('token')).toBeFalse();
    req.flush({ resultat: 'INVALIDE', quittance: null });
  });

  it('construit une URL de téléchargement avec le token encodé', () => {
    expect(service.urlTelechargement('q-1', 'a b/c')).toBe(
      '/api/public/receipts/q-1/download?token=a%20b%2Fc',
    );
    expect(service.urlTelechargement('q-1', null)).toBe('/api/public/receipts/q-1/download');
  });
});
