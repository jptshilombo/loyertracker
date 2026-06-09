import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api/api.config';

export type StatutPaiement = 'IMPAYE' | 'PARTIEL' | 'RECU' | 'EN_RETARD';
export type StatutGarantie = 'DETENU' | 'RESTITUE_PARTIEL' | 'RESTITUE_TOTAL';
export type TypeRestitution = 'PARTIELLE' | 'TOTALE';

export interface Paiement {
  id: string;
  bienId: string;
  bailId: string;
  periode: string;
  montantAttendu: number;
  montantRecu: number;
  resteDu: number;
  dateExigibilite: string;
  statut: StatutPaiement;
}

export interface PointagePayload {
  montantRecu: number;
  statut: StatutPaiement;
}

export interface Garantie {
  id: string;
  bailId: string;
  montant: number;
  typeGarantie: string;
  dateDepot: string;
  statut: StatutGarantie;
  montantRetenu: number;
  motifRetenue: string | null;
}

export interface GarantiePayload {
  montant: number;
  typeGarantie: string;
  dateDepot: string;
}

export interface RestitutionPayload {
  type: TypeRestitution;
  montantRetenu?: number;
  motifRetenue?: string;
}

export interface DeclenchementEcheances {
  echeancesCreees: number;
  loyersEnRetard: number;
}

/**
 * Accès API du lot S03 (paiements & garanties). Aligné sur {@link S02ApiService} : même origine
 * `/api`, observables typés. Le cloisonnement (bailleur propriétaire / gestionnaire affecté) est
 * assuré côté backend (ReBAC + RLS) ; ce service n'ouvre aucun chemin parallèle.
 */
@Injectable({ providedIn: 'root' })
export class S03ApiService {
  private readonly http = inject(HttpClient);

  listerPaiements(bienId: string): Observable<Paiement[]> {
    return this.http.get<Paiement[]>(`${API_BASE_URL}/biens/${bienId}/paiements`);
  }

  pointer(bienId: string, periode: string, payload: PointagePayload): Observable<Paiement> {
    return this.http.patch<Paiement>(
      `${API_BASE_URL}/biens/${bienId}/paiements/${periode}/pointage`,
      payload,
    );
  }

  declencherEcheances(): Observable<DeclenchementEcheances> {
    return this.http.post<DeclenchementEcheances>(`${API_BASE_URL}/batch/echeances`, {});
  }

  listerGaranties(bienId: string, bailId: string): Observable<Garantie[]> {
    return this.http.get<Garantie[]>(`${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties`);
  }

  deposerGarantie(bienId: string, bailId: string, payload: GarantiePayload): Observable<Garantie> {
    return this.http.post<Garantie>(
      `${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties`,
      payload,
    );
  }

  restituer(
    bienId: string,
    bailId: string,
    garantieId: string,
    payload: RestitutionPayload,
  ): Observable<Garantie> {
    return this.http.post<Garantie>(
      `${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties/${garantieId}/restitution`,
      payload,
    );
  }
}
