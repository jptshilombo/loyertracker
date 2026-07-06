import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { PublicReceipt, VerifyReceiptService } from './verify-receipt.service';
import { VerifyReceiptComponent } from './verify-receipt.component';

const quittance: PublicReceipt = {
  numero: 'QT-2026-000001',
  version: 1,
  statut: 'EMISE',
  bailleurNom: 'Alice Durand',
  bailleurAdresse: '10 rue du Bailleur',
  locataireNom: 'Bob Martin',
  patrimoineNom: 'Patrimoine principal',
  bienAdresse: 'Bolia 9 Matonge',
  periode: '2026-07',
  periodeLibelle: 'juillet 2026',
  devise: 'EUR',
  loyerHc: '700.00',
  provisionCharges: '50.00',
  loyerCc: '750.00',
  montantRecu: '750.00',
  dateEmission: '2026-07-06',
  contentHash: 'a'.repeat(64),
  remplacanteNumero: null,
  remplacanteVersion: null,
};

describe('VerifyReceiptComponent', () => {
  let api: jasmine.SpyObj<VerifyReceiptService>;

  function configurer(token: string | null = 'jeton'): void {
    api = jasmine.createSpyObj<VerifyReceiptService>('VerifyReceiptService', [
      'verifier',
      'urlTelechargement',
    ]);
    api.urlTelechargement.and.returnValue('/api/public/receipts/q-1/download?token=jeton');

    TestBed.configureTestingModule({
      imports: [VerifyReceiptComponent],
      providers: [
        { provide: VerifyReceiptService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'q-1' }),
              queryParamMap: convertToParamMap(token ? { token } : {}),
            },
          },
        },
      ],
    });
  }

  function creer(): VerifyReceiptComponent {
    const fixture = TestBed.createComponent(VerifyReceiptComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('affiche le verdict authentique et les données K2 pour une quittance valide', () => {
    configurer();
    api.verifier.and.returnValue(of({ resultat: 'VALIDE', quittance }));

    const cmp = creer();

    expect(api.verifier).toHaveBeenCalledWith('q-1', 'jeton');
    expect(cmp.etat()).toBe('valide');
    expect(cmp.recu()).toEqual(quittance);
  });

  it('affiche un verdict invalide indifférencié sans données', () => {
    configurer('mauvais-jeton');
    api.verifier.and.returnValue(of({ resultat: 'INVALIDE', quittance: null }));

    const cmp = creer();

    expect(cmp.etat()).toBe('invalide');
    expect(cmp.recu()).toBeNull();
  });

  it('bascule sur un état indisponible en cas de panne technique (sans oracle)', () => {
    configurer();
    api.verifier.and.returnValue(throwError(() => new Error('500')));

    const cmp = creer();

    expect(cmp.etat()).toBe('indisponible');
    expect(cmp.recu()).toBeNull();
  });

  it('formate les montants dans la devise du document', () => {
    configurer();
    api.verifier.and.returnValue(of({ resultat: 'VALIDE', quittance }));
    const cmp = creer();

    expect(cmp.montant('750.00', 'EUR')).toContain('750');
    expect(cmp.montant('abc', 'EUR')).toBe('abc EUR');
  });
});
