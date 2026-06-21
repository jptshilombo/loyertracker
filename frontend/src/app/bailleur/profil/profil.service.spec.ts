import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ProfilService } from './profil.service';

describe('ProfilService', () => {
  let service: ProfilService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ProfilService],
    });
    service = TestBed.inject(ProfilService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulte le profil du bailleur', () => {
    service.consulter().subscribe((profil) => expect(profil.id).toBe('b-1'));

    const req = http.expectOne('/api/bailleurs/profil');
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'b-1',
      email: 'alice@example.test',
      nom: 'Durand',
      prenom: 'Alice',
      adresse: null,
    });
  });

  it('met à jour l adresse postale', () => {
    service.mettreAJourAdresse('10 rue du Bailleur').subscribe();

    const req = http.expectOne('/api/bailleurs/profil');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ adresse: '10 rue du Bailleur' });
    req.flush({
      id: 'b-1',
      email: 'alice@example.test',
      nom: 'Durand',
      prenom: 'Alice',
      adresse: '10 rue du Bailleur',
    });
  });
});
