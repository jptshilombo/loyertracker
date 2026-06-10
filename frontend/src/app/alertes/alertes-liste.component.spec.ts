import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AlertesListeComponent } from './alertes-liste.component';

describe('AlertesListeComponent', () => {
  let fixture: ComponentFixture<AlertesListeComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AlertesListeComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AlertesListeComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('charge les alertes au montage et affiche les NON_LUE en tête', () => {
    fixture.detectChanges();

    const req = http.expectOne('/api/alertes');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'al-lue',
        type: 'FIN_BAIL',
        bienId: 'b-1',
        bailId: 'ba-1',
        periode: '2026-09',
        message: 'Fin de bail',
        statut: 'LUE',
        dateCreation: '2026-06-09T07:00:00Z',
        dateLecture: '2026-06-09T08:00:00Z',
      },
      {
        id: 'al-nonlue',
        type: 'LOYER_EN_RETARD',
        bienId: 'b-1',
        bailId: 'ba-1',
        periode: '2026-05',
        message: 'Loyer en retard',
        statut: 'NON_LUE',
        dateCreation: '2026-06-08T07:00:00Z',
        dateLecture: null,
      },
    ]);

    const triees = fixture.componentInstance.alertesTriees();
    expect(triees.length).toBe(2);
    expect(triees[0].id).toBe('al-nonlue');
  });
});
