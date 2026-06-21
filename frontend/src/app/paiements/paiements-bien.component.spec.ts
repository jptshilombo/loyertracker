import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

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
