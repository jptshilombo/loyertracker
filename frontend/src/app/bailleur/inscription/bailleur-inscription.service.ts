import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api.config';

export interface BailleurInscriptionResult {
  status: 'created' | 'already-registered';
}

@Injectable({ providedIn: 'root' })
export class BailleurInscriptionService {
  private readonly http = inject(HttpClient);

  inscrire(): Observable<BailleurInscriptionResult> {
    return this.http.post(`${API_BASE_URL}/bailleurs/inscription`, {}).pipe(
      map(() => ({ status: 'created' as const })),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 409) {
          return of({ status: 'already-registered' as const });
        }
        throw error;
      }),
    );
  }
}
