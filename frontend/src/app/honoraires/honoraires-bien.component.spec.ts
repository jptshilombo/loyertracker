import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HonorairesBienComponent } from './honoraires-bien.component';

describe('HonorairesBienComponent', () => {
  let fixture: ComponentFixture<HonorairesBienComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HonorairesBienComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(HonorairesBienComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('charge les honoraires du bien ciblé au montage', () => {
    fixture.componentRef.setInput('bienId', 'bien-1');
    fixture.detectChanges();

    const req = http.expectOne('/api/biens/bien-1/honoraires');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'h-1',
        affectationId: 'a-1',
        periode: '2026-01',
        montant: 85,
        statut: 'DU',
        devise: 'EUR',
      },
    ]);
    fixture.detectChanges();

    expect(fixture.componentInstance.honoraires().length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('85,00');
  });
});
