import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api.config';

/** Profil du bailleur courant (V11). L'adresse alimente les mentions de la quittance. */
export interface ProfilBailleur {
  id: string;
  email: string;
  nom: string;
  prenom: string;
  adresse: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProfilService {
  private readonly http = inject(HttpClient);

  consulter(): Observable<ProfilBailleur> {
    return this.http.get<ProfilBailleur>(`${API_BASE_URL}/bailleurs/profil`);
  }

  mettreAJourAdresse(adresse: string): Observable<ProfilBailleur> {
    return this.http.put<ProfilBailleur>(`${API_BASE_URL}/bailleurs/profil`, { adresse });
  }
}
