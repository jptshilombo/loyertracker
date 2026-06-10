import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api/api.config';

export type StatutHonoraire = 'DU' | 'EN_ATTENTE' | 'PAYE';
export type TypeAlerte = 'LOYER_EN_RETARD' | 'FIN_BAIL' | 'PREAVIS' | 'GARANTIE_NON_RESTITUEE';
export type StatutAlerte = 'NON_LUE' | 'LUE';

export interface Honoraire {
  id: string;
  affectationId: string;
  periode: string;
  montant: number;
  statut: StatutHonoraire;
}

export interface Alerte {
  id: string;
  type: TypeAlerte;
  bienId: string;
  bailId: string | null;
  periode: string | null;
  message: string;
  statut: StatutAlerte;
  dateCreation: string;
  dateLecture: string | null;
}

export interface AuditEntry {
  id: string;
  acteurId: string;
  acteurRole: string;
  action: string;
  entityType: string;
  entityId: string | null;
  horodatage: string;
}

export interface RecalculHonoraires {
  honorairesCalcules: number;
}

export interface GenerationAlertes {
  alertesCreees: number;
}

/**
 * Accès API du lot S04 (honoraires, alertes de pilotage, journal d'audit). Aligné sur
 * {@link S03ApiService} : même origine `/api`, observables typés. Le cloisonnement (bailleur
 * propriétaire / gestionnaire affecté, audit réservé au bailleur) est assuré côté backend
 * (ReBAC + RLS + `@PreAuthorize`) ; ce service n'ouvre aucun chemin parallèle.
 */
@Injectable({ providedIn: 'root' })
export class S04ApiService {
  private readonly http = inject(HttpClient);

  listerHonoraires(bienId: string): Observable<Honoraire[]> {
    return this.http.get<Honoraire[]>(`${API_BASE_URL}/biens/${bienId}/honoraires`);
  }

  changerStatutHonoraire(id: string, statut: StatutHonoraire): Observable<Honoraire> {
    return this.http.patch<Honoraire>(`${API_BASE_URL}/honoraires/${id}/statut`, { statut });
  }

  recalculerHonoraires(): Observable<RecalculHonoraires> {
    return this.http.post<RecalculHonoraires>(`${API_BASE_URL}/batch/honoraires`, {});
  }

  listerAlertes(): Observable<Alerte[]> {
    return this.http.get<Alerte[]>(`${API_BASE_URL}/alertes`);
  }

  marquerAlerteLue(id: string): Observable<Alerte> {
    return this.http.patch<Alerte>(`${API_BASE_URL}/alertes/${id}/lecture`, {});
  }

  genererAlertes(): Observable<GenerationAlertes> {
    return this.http.post<GenerationAlertes>(`${API_BASE_URL}/batch/alertes`, {});
  }

  listerAudit(): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${API_BASE_URL}/audit`);
  }
}
