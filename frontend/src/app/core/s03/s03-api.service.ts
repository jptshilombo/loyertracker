import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api/api.config';
import { Devise } from '../s02/s02-api.service';

export type StatutPaiement = 'A_VENIR' | 'IMPAYE' | 'PARTIEL' | 'RECU' | 'EN_RETARD';
export type StatutGarantie = 'DETENU' | 'RESTITUE_PARTIEL' | 'RESTITUE_TOTAL';
export type TypeRestitution = 'PARTIELLE' | 'TOTALE';
export type TypeMouvementGarantie =
  | 'DEPOT_INITIAL'
  | 'COMPLEMENT'
  | 'RETENUE_LOYER'
  | 'RETENUE_CHARGES'
  | 'RETENUE_REPARATION'
  | 'RESTITUTION'
  | 'AJUSTEMENT'
  | 'ANNULATION';

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
  /** Devise du bail parent (US-93, ADR-13). */
  devise: Devise;
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
  /** Solde courant du ledger (Sprint 9, ADR-14) — diverge de `montant` après retenue/complément. */
  soldeActuel: number;
}

export interface GarantieMovement {
  id: string;
  garantieId: string;
  dateMouvement: string;
  type: TypeMouvementGarantie;
  debit: number;
  credit: number;
  soldeApres: number;
  motif: string | null;
  utilisateur: string | null;
  commentaire: string | null;
  referenceDocument: string | null;
}

export interface RetenueLoyerPayload {
  paiementId: string;
  montant: number;
}

export interface ComplementPayload {
  montant: number;
  motif: string;
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

  /** Quittance de loyer (PDF) d'une période soldée (statut RECU). */
  telechargerQuittance(bienId: string, periode: string): Observable<Blob> {
    return this.telechargerDocument(bienId, periode, 'quittance');
  }

  /** Avis d'échéance (PDF) d'une période non soldée. */
  telechargerAvisEcheance(bienId: string, periode: string): Observable<Blob> {
    return this.telechargerDocument(bienId, periode, 'avis-echeance');
  }

  private telechargerDocument(
    bienId: string,
    periode: string,
    document: 'quittance' | 'avis-echeance',
  ): Observable<Blob> {
    return this.http.get(`${API_BASE_URL}/biens/${bienId}/paiements/${periode}/${document}`, {
      responseType: 'blob',
    });
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

  /** Retenue explicite sur un loyer impayé (US-95) — jamais un prélèvement automatique. */
  retenirSurLoyer(
    bienId: string,
    bailId: string,
    garantieId: string,
    payload: RetenueLoyerPayload,
  ): Observable<Garantie> {
    return this.http.post<Garantie>(
      `${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties/${garantieId}/retenue-loyer`,
      payload,
    );
  }

  /** Réapprovisionnement d'une garantie active (US-96). */
  complementer(
    bienId: string,
    bailId: string,
    garantieId: string,
    payload: ComplementPayload,
  ): Observable<Garantie> {
    return this.http.post<Garantie>(
      `${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties/${garantieId}/complement`,
      payload,
    );
  }

  /** Historique des mouvements du ledger d'une garantie (US-97). */
  listerMouvements(
    bienId: string,
    bailId: string,
    garantieId: string,
  ): Observable<GarantieMovement[]> {
    return this.http.get<GarantieMovement[]>(
      `${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties/${garantieId}/mouvements`,
    );
  }

  /** Export CSV de l'historique des mouvements (US-97). */
  exporterMouvements(bienId: string, bailId: string, garantieId: string): Observable<Blob> {
    return this.http.get(
      `${API_BASE_URL}/biens/${bienId}/baux/${bailId}/garanties/${garantieId}/mouvements/export`,
      { responseType: 'blob' },
    );
  }
}
