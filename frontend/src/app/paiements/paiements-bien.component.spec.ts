import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Paiement, S03ApiService } from '../core/s03/s03-api.service';
import { PaiementsBienComponent } from './paiements-bien.component';

function paiement(statut: Paiement['statut']): Paiement {
  return {
    id: 'p-1',
    bienId: 'bien-1',
    bailId: 'bail-1',
    periode: '2026-01',
    montantAttendu: 850,
    montantRecu: statut === 'RECU' ? 850 : 0,
    resteDu: statut === 'RECU' ? 0 : 850,
    dateExigibilite: '2026-02-01',
    statut,
  };
}

describe('PaiementsBienComponent', () => {
  let api: jasmine.SpyObj<S03ApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<S03ApiService>('S03ApiService', [
      'listerPaiements',
      'pointer',
      'declencherEcheances',
      'telechargerQuittance',
      'telechargerAvisEcheance',
    ]);
    api.listerPaiements.and.returnValue(of([]));
    api.telechargerQuittance.and.returnValue(of(new Blob(['%PDF-'], { type: 'application/pdf' })));
    api.telechargerAvisEcheance.and.returnValue(
      of(new Blob(['%PDF-'], { type: 'application/pdf' })),
    );

    TestBed.configureTestingModule({
      imports: [PaiementsBienComponent],
      providers: [{ provide: S03ApiService, useValue: api }],
    });
  });

  function creer() {
    const fixture = TestBed.createComponent(PaiementsBienComponent);
    fixture.componentRef.setInput('bienId', 'bien-1');
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('charge, sélectionne et pointe un loyer', () => {
    const cmp = creer();
    const p = paiement('EN_RETARD');
    api.pointer.and.returnValue(of(paiement('RECU')));

    cmp.selectionner(p);
    expect(cmp.selection()).toBe(p);
    expect(cmp.pointageForm.getRawValue()).toEqual({ montantRecu: 0, statut: 'EN_RETARD' });

    cmp.pointageForm.setValue({ montantRecu: 850, statut: 'RECU' });
    cmp.pointer();

    expect(api.pointer).toHaveBeenCalledWith('bien-1', '2026-01', {
      montantRecu: 850,
      statut: 'RECU',
    });
    expect(cmp.selection()).toBeNull();
    expect(api.listerPaiements).toHaveBeenCalledTimes(2);
  });

  it('bloque les pointages incohérents avant l appel API', () => {
    const cmp = creer();
    const p = paiement('EN_RETARD');
    cmp.selectionner(p);

    cmp.pointageForm.setValue({ montantRecu: 0, statut: 'PARTIEL' });
    cmp.pointer();
    expect(cmp.message()).toBe('PARTIEL : 0 < reçu < attendu');

    cmp.pointageForm.setValue({ montantRecu: 849, statut: 'RECU' });
    cmp.pointer();
    expect(cmp.message()).toBe('RECU : reçu >= attendu');

    cmp.selection.set(null);
    cmp.pointer();
    expect(api.pointer).not.toHaveBeenCalled();
  });

  it('déclenche les échéances puis recharge les paiements', () => {
    const cmp = creer();
    api.declencherEcheances.and.returnValue(of({ echeancesCreees: 2, loyersEnRetard: 1 }));

    cmp.declencher();

    expect(api.declencherEcheances).toHaveBeenCalled();
    expect(api.listerPaiements).toHaveBeenCalledTimes(2);
    expect(cmp.chargement()).toBeFalse();
  });

  it('traduit les erreurs HTTP et les erreurs inconnues', () => {
    const cmp = creer();
    const p = paiement('RECU');
    const cas: Array<[number, string]> = [
      [400, 'incohérence (400)'],
      [404, 'introuvable (404)'],
      [403, 'accès refusé (403)'],
      [500, 'erreur API (500)'],
    ];

    for (const [status, message] of cas) {
      api.telechargerQuittance.and.returnValue(
        throwError(() => new HttpErrorResponse({ status })),
      );
      cmp.telecharger(p, 'quittance');
      expect(cmp.message()).toBe(message);
      expect(cmp.chargement()).toBeFalse();
    }

    api.telechargerQuittance.and.returnValue(throwError(() => new Error('réseau')));
    cmp.telecharger(p, 'quittance');
    expect(cmp.message()).toBe('erreur inconnue');
  });

  it('télécharge la quittance pour un loyer RECU', () => {
    const cmp = creer();
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake');
    spyOn(URL, 'revokeObjectURL');

    cmp.telecharger(paiement('RECU'), 'quittance');

    expect(api.telechargerQuittance).toHaveBeenCalledWith('bien-1', '2026-01');
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });

  it('télécharge l avis d échéance pour un loyer non soldé', () => {
    const cmp = creer();
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake');
    spyOn(URL, 'revokeObjectURL');

    cmp.telecharger(paiement('EN_RETARD'), 'avis-echeance');

    expect(api.telechargerAvisEcheance).toHaveBeenCalledWith('bien-1', '2026-01');
    expect(URL.createObjectURL).toHaveBeenCalled();
  });
});
