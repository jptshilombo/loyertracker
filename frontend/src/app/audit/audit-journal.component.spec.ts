import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuditJournalComponent } from './audit-journal.component';

describe('AuditJournalComponent', () => {
  let fixture: ComponentFixture<AuditJournalComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AuditJournalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(AuditJournalComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('charge le journal d audit au montage', () => {
    fixture.detectChanges();

    const req = http.expectOne('/api/audit');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'au-1',
        acteurId: 'act-1',
        acteurRole: 'BAILLEUR',
        action: 'POINTER_PAIEMENT',
        entityType: 'paiement',
        entityId: 'p-1',
        horodatage: '2026-06-10T08:00:00Z',
      },
    ]);

    expect(fixture.componentInstance.entrees().length).toBe(1);
  });
});
