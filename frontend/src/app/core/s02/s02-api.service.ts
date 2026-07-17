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

/** Devises supportées (US-59, ADR-13) — durci en union plutôt que string libre (US-93). */
export type Devise = 'EUR' | 'USD' | 'CDF';

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
  devise: Devise;
}

/**
 * `depotGarantie` n'est plus saisi à la création du bail (ADR-14 §8, Sprint 9) : aucune
 * `Garantie` n'existe encore à cet instant. Le dépôt se déclare via le flux « Ajouter garantie »
 * existant, après la création du bail. `Bail.depotGarantie` (lecture) reste exposé par l'API,
 * désormais calculé côté backend.
 *
 * Depuis V26 (EP-15 Sprint C), le locataire n'est plus du texte libre : `locataireId` doit
 * référencer un `Locataire` existant, non archivé. La lecture (`Bail.locataireNom/Email`) reste
 * inchangée, dérivée du `Locataire` lié.
 */
export interface BailPayload {
  locataireId: string;
  loyerHc: number;
  provisionCharges: number;
  dateDebut: string;
  dateFin: string | null;
  devise: Devise;
}

/** Vue minimale d'un Locataire pour le sélecteur de création de bail (EP-15 Sprint C). */
export interface Locataire {
  id: string;
  nom: string;
  prenom: string | null;
  email: string | null;
  statut: string;
}

export interface LocataireQuickAddPayload {
  nom: string;
  prenom?: string | null;
  email?: string | null;
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

  /** Locataires du bailleur (BAILLEUR uniquement) — sélecteur de création de bail. */
  listerLocataires(): Observable<Locataire[]> {
    return this.http.get<Locataire[]>(`${API_BASE_URL}/locataires`);
  }

  /** Création rapide d'un Locataire (BAILLEUR uniquement) depuis le formulaire de bail. */
  creerLocataire(payload: LocataireQuickAddPayload): Observable<Locataire> {
    return this.http.post<Locataire>(`${API_BASE_URL}/locataires`, payload);
  }

  /**
   * Locataires ACTIVE du bailleur propriétaire d'un bien — lecture seule, ouverte au
   * GESTIONNAIRE affecté (contrairement à `listerLocataires`, réservé BAILLEUR).
   */
  listerLocatairesDuBien(bienId: string): Observable<Locataire[]> {
    return this.http.get<Locataire[]>(`${API_BASE_URL}/biens/${bienId}/locataires`);
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
