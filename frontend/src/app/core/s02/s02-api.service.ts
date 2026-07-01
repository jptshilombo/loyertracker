import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api/api.config';

export type StatutBien = 'LIBRE' | 'LOUE' | 'EN_TRAVAUX' | 'ARCHIVE';
export type TypeHonoraires = 'POURCENTAGE' | 'FORFAIT';

export interface Bien {
  id: string;
  adresse: string;
  type: string;
  statut: StatutBien;
  patrimoineId: string;
}

export interface BienPayload {
  adresse: string;
  type: string;
  statut: StatutBien;
  patrimoineId: string;
}

export interface Patrimoine {
  id: string;
  nom: string;
  adresse: string;
  ville: string | null;
  commune: string | null;
  quartier: string | null;
  provinceEtat: string | null;
  pays: string | null;
  description: string | null;
  referenceInterne: string | null;
  statut: string;
}

export interface PatrimoinePayload {
  nom: string;
  adresse: string;
  ville: string | null;
  commune: string | null;
  quartier: string | null;
  provinceEtat: string | null;
  pays: string | null;
  description: string | null;
  referenceInterne: string | null;
}

export interface TypeBien {
  code: string;
  libelle: string;
  actif: boolean;
}

export interface Bail {
  id: string;
  bienId: string;
  locataireNom: string;
  locataireEmail: string | null;
  loyerHc: number;
  provisionCharges: number;
  loyerCc: number;
  depotGarantie: number;
  dateDebut: string;
  dateFin: string | null;
  statut: string;
  devise: string;
}

export interface BailPayload {
  locataireNom: string;
  locataireEmail: string | null;
  loyerHc: number;
  provisionCharges: number;
  depotGarantie: number;
  dateDebut: string;
  dateFin: string | null;
  devise: string;
}

export interface Affectation {
  id: string;
  bienId: string | null;
  patrimoineId: string | null;
  gestionnaireId: string;
  typeHonoraires: TypeHonoraires;
  montantHonoraires: number;
  dateDebut: string;
  dateFin: string | null;
  statut: string;
  dateRevocation: string | null;
  typeException: 'INCLUSION' | 'EXCLUSION' | null;
}

export interface AffectationPayload {
  bienId?: string;
  patrimoineId?: string;
  gestionnaireId: string;
  typeHonoraires: TypeHonoraires;
  montantHonoraires: number;
  dateDebut: string;
  dateFin: string | null;
  typeException?: 'INCLUSION' | 'EXCLUSION';
}

@Injectable({ providedIn: 'root' })
export class S02ApiService {
  private readonly http = inject(HttpClient);

  listerBiens(): Observable<Bien[]> {
    return this.http.get<Bien[]>(`${API_BASE_URL}/biens`);
  }

  listerPatrimoines(): Observable<Patrimoine[]> {
    return this.http.get<Patrimoine[]>(`${API_BASE_URL}/patrimoines`);
  }

  listerTypesBiens(): Observable<TypeBien[]> {
    return this.http.get<TypeBien[]>(`${API_BASE_URL}/types-biens`);
  }

  creerBien(payload: BienPayload): Observable<Bien> {
    return this.http.post<Bien>(`${API_BASE_URL}/biens`, payload);
  }

  modifierBien(id: string, payload: BienPayload): Observable<Bien> {
    return this.http.put<Bien>(`${API_BASE_URL}/biens/${id}`, payload);
  }

  archiverBien(id: string): Observable<Bien> {
    return this.http.patch<Bien>(`${API_BASE_URL}/biens/${id}/archivage`, {});
  }

  creerBail(bienId: string, payload: BailPayload): Observable<Bail> {
    return this.http.post<Bail>(`${API_BASE_URL}/biens/${bienId}/baux`, payload);
  }

  listerBaux(bienId: string): Observable<Bail[]> {
    return this.http.get<Bail[]>(`${API_BASE_URL}/biens/${bienId}/baux`);
  }

  creerAffectation(payload: AffectationPayload): Observable<Affectation> {
    return this.http.post<Affectation>(`${API_BASE_URL}/affectations`, payload);
  }

  revoquerAffectation(id: string): Observable<Affectation> {
    return this.http.post<Affectation>(`${API_BASE_URL}/affectations/${id}/revocation`, {});
  }

  listerAffectations(bienId: string): Observable<Affectation[]> {
    return this.http.get<Affectation[]>(`${API_BASE_URL}/biens/${bienId}/affectations`);
  }

  listerAffectationsPatrimoine(patrimoineId: string): Observable<Affectation[]> {
    return this.http.get<Affectation[]>(`${API_BASE_URL}/patrimoines/${patrimoineId}/affectations`);
  }

  modifierPatrimoine(id: string, payload: PatrimoinePayload): Observable<Patrimoine> {
    return this.http.put<Patrimoine>(`${API_BASE_URL}/patrimoines/${id}`, payload);
  }
}
