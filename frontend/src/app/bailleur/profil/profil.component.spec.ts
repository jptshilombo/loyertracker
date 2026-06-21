import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProfilComponent } from './profil.component';
import { ProfilBailleur, ProfilService } from './profil.service';

const profilSansAdresse: ProfilBailleur = {
  id: 'b-1',
  email: 'alice@example.test',
  nom: 'Durand',
  prenom: 'Alice',
  adresse: null,
};

describe('ProfilComponent', () => {
  let api: jasmine.SpyObj<ProfilService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<ProfilService>('ProfilService', [
      'consulter',
      'mettreAJourAdresse',
    ]);
    api.consulter.and.returnValue(of(profilSansAdresse));

    TestBed.configureTestingModule({
      imports: [ProfilComponent],
      providers: [provideRouter([]), { provide: ProfilService, useValue: api }],
    });
  });

  function creer(): ProfilComponent {
    const fixture = TestBed.createComponent(ProfilComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('charge le profil et initialise le formulaire', () => {
    const cmp = creer();

    expect(cmp.profil()).toEqual(profilSansAdresse);
    expect(cmp.form.getRawValue()).toEqual({ adresse: '' });
  });

  it('enregistre une adresse normalisée', () => {
    const cmp = creer();
    const profilAvecAdresse = { ...profilSansAdresse, adresse: '10 rue du Bailleur' };
    api.mettreAJourAdresse.and.returnValue(of(profilAvecAdresse));
    cmp.form.setValue({ adresse: '  10 rue du Bailleur  ' });

    cmp.enregistrer();

    expect(api.mettreAJourAdresse).toHaveBeenCalledWith('10 rue du Bailleur');
    expect(cmp.profil()).toEqual(profilAvecAdresse);
    expect(cmp.message()).toBe('Adresse enregistrée.');
    expect(cmp.enregistrement()).toBeFalse();
  });

  it('ne soumet pas un formulaire invalide', () => {
    const cmp = creer();
    cmp.form.setValue({ adresse: '' });

    cmp.enregistrer();

    expect(api.mettreAJourAdresse).not.toHaveBeenCalled();
  });

  it('signale les erreurs de chargement et d enregistrement', () => {
    api.consulter.and.returnValue(throwError(() => new Error('chargement')));
    const cmp = creer();
    expect(cmp.message()).toBe('Impossible de charger le profil.');

    cmp.form.setValue({ adresse: '10 rue du Bailleur' });
    api.mettreAJourAdresse.and.returnValue(throwError(() => new Error('enregistrement')));
    cmp.enregistrer();

    expect(cmp.message()).toBe("Échec de l'enregistrement.");
    expect(cmp.enregistrement()).toBeFalse();
  });
});
