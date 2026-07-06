import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../core/api/api.config';

/**
 * Projection publique stricte d'une quittance certifiée (contrat K2, ADR-15 D5). Ne porte
 * volontairement ni le mode de paiement ni la garantie retenue : la reconstruction côté serveur
 * garantit qu'aucun champ hors contrat ne transite (US-102, test de non-fuite).
 */
export interface PublicReceipt {
  numero: string;
  version: number;
  statut: 'EMISE' | 'ANNULEE' | 'REMPLACEE';
  bailleurNom: string;
  bailleurAdresse: string;
  locataireNom: string;
  patrimoineNom: string;
  bienAdresse: string;
  periode: string;
  periodeLibelle: string;
  devise: string;
  loyerHc: string;
  provisionCharges: string;
  loyerCc: string;
  montantRecu: string;
  dateEmission: string;
  contentHash: string;
  remplacanteNumero: string | null;
  remplacanteVersion: number | null;
}

/** Réponse de vérification indifférenciée : `INVALIDE` ne divulgue jamais la cause (aucun oracle). */
export interface VerificationResponse {
  resultat: 'VALIDE' | 'INVALIDE';
  quittance: PublicReceipt | null;
}

/**
 * Accès à la surface publique de vérification des quittances (US-102). Appels **sans Bearer** :
 * l'exclusion `/api/public/` de l'intercepteur (voir `app.config.ts`) permet à un tiers non
 * authentifié d'appeler ces endpoints. La seule preuve d'autorisation est le token du QR.
 */
@Injectable({ providedIn: 'root' })
export class VerifyReceiptService {
  private readonly http = inject(HttpClient);

  verifier(id: string, token: string | null): Observable<VerificationResponse> {
    return this.http.get<VerificationResponse>(`${API_BASE_URL}/public/receipts/${id}`, {
      params: token ? { token } : {},
    });
  }

  /** URL de téléchargement de l'exemplaire officiel (navigation directe : déclenche le PDF). */
  urlTelechargement(id: string, token: string | null): string {
    const query = token ? `?token=${encodeURIComponent(token)}` : '';
    return `${API_BASE_URL}/public/receipts/${id}/download${query}`;
  }
}
