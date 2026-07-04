import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import {
  Garantie,
  GarantieMovement,
  Paiement,
  S03ApiService,
} from '../core/s03/s03-api.service';
import { GarantiesBailComponent } from './garanties-bail.component';

function garantie(surcharge: Partial<Garantie> = {}): Garantie {
  return {
    id: 'g-1',
    bailId: 'bail-1',
    montant: 850,
    typeGarantie: 'CAUTION',
    dateDepot: '2026-01-01',
    statut: 'DETENU',
    montantRetenu: 0,
    motifRetenue: null,
    soldeActuel: 850,
    ...surcharge,
  };
}

function mouvement(surcharge: Partial<GarantieMovement> = {}): GarantieMovement {
  return {
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
    ...surcharge,
  };
}

function paiement(surcharge: Partial<Paiement> = {}): Paiement {
  return {
    id: 'p-1',
    bienId: 'bien-1',
    bailId: 'bail-1',
    periode: '2026-01',
    montantAttendu: 850,
    montantRecu: 0,
    resteDu: 850,
    dateExigibilite: '2026-02-01',
    statut: 'EN_RETARD',
    devise: 'EUR',
    ...surcharge,
  };
}

describe('GarantiesBailComponent', () => {
  let api: jasmine.SpyObj<S03ApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<S03ApiService>('S03ApiService', [
      'listerGaranties',
      'deposerGarantie',
      'restituer',
      'retenirSurLoyer',
      'complementer',
      'listerMouvements',
      'exporterMouvements',
      'listerPaiements',
    ]);
    api.listerGaranties.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [GarantiesBailComponent],
      providers: [{ provide: S03ApiService, useValue: api }],
    });
  });

  function creer() {
    const fixture = TestBed.createComponent(GarantiesBailComponent);
    fixture.componentRef.setInput('bienId', 'bien-1');
    fixture.componentRef.setInput('bailId', 'bail-1');
    fixture.detectChanges();
    return { fixture, cmp: fixture.componentInstance };
  }

  it('charge les garanties du bail au démarrage', () => {
    api.listerGaranties.and.returnValue(of([garantie()]));
    const { cmp } = creer();

    expect(api.listerGaranties).toHaveBeenCalledWith('bien-1', 'bail-1');
    expect(cmp.garanties().length).toBe(1);
    expect(cmp.message()).toBe('1 garantie(s)');
    expect(cmp.chargement()).toBeFalse();
  });

  it('recharge et referme tout quand le bail change', () => {
    const { fixture, cmp } = creer();
    cmp.selection.set(garantie());
    cmp.action.set('COMPLEMENT');
    cmp.historiqueOuvert.set('g-1');

    fixture.componentRef.setInput('bailId', 'bail-2');
    fixture.detectChanges();

    expect(api.listerGaranties).toHaveBeenCalledWith('bien-1', 'bail-2');
    expect(cmp.selection()).toBeNull();
    expect(cmp.action()).toBeNull();
    expect(cmp.historiqueOuvert()).toBeNull();
  });

  it('dépose une garantie puis recharge la liste', () => {
    const { cmp } = creer();
    api.deposerGarantie.and.returnValue(of(garantie()));
    cmp.depotForm.setValue({ montant: 850, typeGarantie: 'CAUTION', dateDepot: '2026-01-01' });

    cmp.deposer();

    expect(api.deposerGarantie).toHaveBeenCalledWith('bien-1', 'bail-1', {
      montant: 850,
      typeGarantie: 'CAUTION',
      dateDepot: '2026-01-01',
    });
    // La liste rechargée (mock vide) écrase le message de succès transitoire.
    expect(cmp.message()).toBe('0 garantie(s)');
    expect(cmp.depotForm.getRawValue().dateDepot).toBe('');
    expect(api.listerGaranties).toHaveBeenCalledTimes(2);
  });

  it('ne dépose pas quand le formulaire est invalide', () => {
    const { cmp } = creer();
    // dateDepot vide → form invalide
    cmp.deposer();
    expect(api.deposerGarantie).not.toHaveBeenCalled();
  });

  it('ouvre chaque sous-formulaire avec un état réinitialisé', () => {
    const { cmp } = creer();
    const g = garantie();
    api.listerPaiements.and.returnValue(of([]));

    cmp.ouvrir(g, 'RESTITUTION');
    expect(cmp.action()).toBe('RESTITUTION');
    expect(cmp.restitutionForm.getRawValue().type).toBe('TOTALE');

    cmp.ouvrir(g, 'RETENUE');
    expect(cmp.action()).toBe('RETENUE');
    expect(api.listerPaiements).toHaveBeenCalledWith('bien-1');

    cmp.ouvrir(g, 'COMPLEMENT');
    expect(cmp.action()).toBe('COMPLEMENT');
    expect(cmp.complementForm.getRawValue()).toEqual({ montant: null, motif: '' });
    expect(cmp.selection()).toBe(g);
  });

  it('restitue totalement une garantie', () => {
    const { cmp } = creer();
    api.restituer.and.returnValue(of(garantie({ statut: 'RESTITUE_TOTAL', soldeActuel: 0 })));
    cmp.ouvrir(garantie(), 'RESTITUTION');

    cmp.restituer();

    expect(api.restituer).toHaveBeenCalledWith('bien-1', 'bail-1', 'g-1', {
      type: 'TOTALE',
      montantRetenu: undefined,
      motifRetenue: undefined,
    });
    expect(cmp.selection()).toBeNull();
    expect(api.listerGaranties).toHaveBeenCalledTimes(2);
  });

  it('restitue partiellement avec montant retenu et motif', () => {
    const { cmp } = creer();
    api.restituer.and.returnValue(of(garantie({ statut: 'RESTITUE_PARTIEL' })));
    cmp.ouvrir(garantie(), 'RESTITUTION');
    cmp.restitutionForm.setValue({ type: 'PARTIELLE', montantRetenu: 100, motifRetenue: 'Dégâts' });

    cmp.restituer();

    expect(api.restituer).toHaveBeenCalledWith('bien-1', 'bail-1', 'g-1', {
      type: 'PARTIELLE',
      montantRetenu: 100,
      motifRetenue: 'Dégâts',
    });
  });

  it('bloque une restitution partielle incomplète ou excédant le solde', () => {
    const { cmp } = creer();
    cmp.ouvrir(garantie({ soldeActuel: 200 }), 'RESTITUTION');

    cmp.restitutionForm.setValue({ type: 'PARTIELLE', montantRetenu: null, motifRetenue: null });
    cmp.restituer();
    expect(cmp.message()).toBe('Partielle : montant retenu (> 0) et motif requis');

    cmp.restitutionForm.setValue({ type: 'PARTIELLE', montantRetenu: 300, motifRetenue: 'Dégâts' });
    cmp.restituer();
    expect(cmp.message()).toBe('Le montant retenu ne peut excéder le solde de la garantie');

    cmp.selection.set(null);
    cmp.restituer();
    expect(api.restituer).not.toHaveBeenCalled();
  });

  it('retient sur un loyer impayé puis recharge la liste', () => {
    const { cmp } = creer();
    api.listerPaiements.and.returnValue(of([paiement()]));
    api.retenirSurLoyer.and.returnValue(of(garantie({ soldeActuel: 0 })));
    cmp.ouvrir(garantie(), 'RETENUE');
    cmp.retenueForm.setValue({ paiementId: 'p-1', montant: 850 });

    cmp.retenir();

    expect(api.retenirSurLoyer).toHaveBeenCalledWith('bien-1', 'bail-1', 'g-1', {
      paiementId: 'p-1',
      montant: 850,
    });
    expect(cmp.selection()).toBeNull();
    expect(api.listerGaranties).toHaveBeenCalledTimes(2);
  });

  it('ne propose comme impayés que les loyers restant dus du bail courant', () => {
    const { cmp } = creer();
    api.listerPaiements.and.returnValue(
      of([
        paiement({ id: 'p-1', statut: 'EN_RETARD' }),
        paiement({ id: 'p-2', statut: 'IMPAYE' }),
        paiement({ id: 'p-3', statut: 'RECU' }),
        paiement({ id: 'p-4', statut: 'IMPAYE', bailId: 'autre-bail' }),
      ]),
    );

    cmp.ouvrir(garantie(), 'RETENUE');

    expect(cmp.impayes().map((p) => p.id)).toEqual(['p-1', 'p-2']);
  });

  it('bloque une retenue excédant le solde ou le reste dû', () => {
    const { cmp } = creer();
    api.listerPaiements.and.returnValue(of([paiement({ resteDu: 100 })]));
    cmp.ouvrir(garantie({ soldeActuel: 200 }), 'RETENUE');

    cmp.retenueForm.setValue({ paiementId: 'p-1', montant: 300 });
    cmp.retenir();
    expect(cmp.message()).toBe('Le montant ne peut excéder le solde de la garantie');

    cmp.retenueForm.setValue({ paiementId: 'p-1', montant: 150 });
    cmp.retenir();
    expect(cmp.message()).toBe('Le montant ne peut excéder le reste dû du loyer');

    cmp.retenueForm.setValue({ paiementId: '', montant: null });
    cmp.retenir();
    expect(api.retenirSurLoyer).not.toHaveBeenCalled();
  });

  it('complète une garantie puis recharge la liste', () => {
    const { cmp } = creer();
    api.complementer.and.returnValue(of(garantie({ soldeActuel: 1000 })));
    cmp.ouvrir(garantie(), 'COMPLEMENT');
    cmp.complementForm.setValue({ montant: 150, motif: 'Réapprovisionnement' });

    cmp.complementer();

    expect(api.complementer).toHaveBeenCalledWith('bien-1', 'bail-1', 'g-1', {
      montant: 150,
      motif: 'Réapprovisionnement',
    });
    expect(cmp.selection()).toBeNull();
    expect(api.listerGaranties).toHaveBeenCalledTimes(2);
  });

  it('bloque un complément sans montant positif ou sans motif', () => {
    const { cmp } = creer();
    cmp.ouvrir(garantie(), 'COMPLEMENT');

    cmp.complementForm.setValue({ montant: 150, motif: '   ' });
    cmp.complementer();
    expect(cmp.message()).toBe('Complément : montant (> 0) et motif requis');

    cmp.complementForm.setValue({ montant: null, motif: 'Motif' });
    cmp.complementer();
    expect(api.complementer).not.toHaveBeenCalled();
  });

  it('ouvre puis referme l historique des mouvements', () => {
    const { cmp } = creer();
    const g = garantie();
    api.listerMouvements.and.returnValue(of([mouvement()]));

    cmp.basculerHistorique(g);
    expect(api.listerMouvements).toHaveBeenCalledWith('bien-1', 'bail-1', 'g-1');
    expect(cmp.historiqueOuvert()).toBe('g-1');
    expect(cmp.mouvements().length).toBe(1);
    expect(cmp.message()).toBe('1 mouvement(s)');

    cmp.basculerHistorique(g);
    expect(cmp.historiqueOuvert()).toBeNull();
    expect(cmp.mouvements()).toEqual([]);
  });

  it('filtre les mouvements par type et expose les types disponibles triés', () => {
    const { cmp } = creer();
    cmp.mouvements.set([
      mouvement({ id: 'm-1', type: 'RETENUE_LOYER' }),
      mouvement({ id: 'm-2', type: 'DEPOT_INITIAL' }),
      mouvement({ id: 'm-3', type: 'RETENUE_LOYER' }),
    ]);

    expect(cmp.typesDisponibles()).toEqual(['DEPOT_INITIAL', 'RETENUE_LOYER']);

    cmp.filtrerType({ target: { value: 'RETENUE_LOYER' } } as unknown as Event);
    expect(cmp.filtreType()).toBe('RETENUE_LOYER');
    expect(cmp.mouvementsAffiches().map((m) => m.id)).toEqual(['m-1', 'm-3']);
  });

  it('trie les mouvements par colonne, avec inversion au second clic', () => {
    const { cmp } = creer();
    cmp.mouvements.set([
      mouvement({ id: 'm-1', debit: 50, type: 'RETENUE_LOYER' }),
      mouvement({ id: 'm-2', debit: 0, type: 'DEPOT_INITIAL' }),
      mouvement({ id: 'm-3', debit: 200, type: 'AJUSTEMENT' }),
    ]);

    cmp.trier('debit');
    expect(cmp.mouvementsAffiches().map((m) => m.id)).toEqual(['m-2', 'm-1', 'm-3']);
    expect(cmp.indicateurTri('debit')).toBe(' ↑');

    cmp.trier('debit');
    expect(cmp.mouvementsAffiches().map((m) => m.id)).toEqual(['m-3', 'm-1', 'm-2']);
    expect(cmp.indicateurTri('debit')).toBe(' ↓');

    cmp.trier('type');
    expect(cmp.mouvementsAffiches().map((m) => m.id)).toEqual(['m-3', 'm-2', 'm-1']);
    expect(cmp.indicateurTri('debit')).toBe('');
  });

  it('exporte l historique en CSV', () => {
    const { cmp } = creer();
    api.exporterMouvements.and.returnValue(of(new Blob(['date;type\n'], { type: 'text/csv' })));
    spyOn(URL, 'createObjectURL').and.returnValue('blob:fake');
    spyOn(URL, 'revokeObjectURL');

    cmp.exporterCsv(garantie());

    expect(api.exporterMouvements).toHaveBeenCalledWith('bien-1', 'bail-1', 'g-1');
    expect(cmp.message()).toBe('Historique exporté');
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalled();
  });

  it('traduit les erreurs HTTP et les erreurs inconnues', () => {
    const { cmp } = creer();
    const cas: [number, string][] = [
      [400, 'données invalides (400)'],
      [404, 'introuvable (404)'],
      [409, 'transition interdite (409)'],
      [403, 'accès refusé (403)'],
      [500, 'erreur API (500)'],
    ];

    for (const [status, message] of cas) {
      api.exporterMouvements.and.returnValue(throwError(() => new HttpErrorResponse({ status })));
      cmp.exporterCsv(garantie());
      expect(cmp.message()).toBe(message);
      expect(cmp.chargement()).toBeFalse();
    }

    api.exporterMouvements.and.returnValue(throwError(() => new Error('réseau')));
    cmp.exporterCsv(garantie());
    expect(cmp.message()).toBe('erreur inconnue');
  });
});
