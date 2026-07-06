import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'bailleur' },
  {
    path: 'bailleur',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./bailleur/dashboard/dashboard.component').then((m) => m.BailleurDashboardComponent),
  },
  {
    path: 'bailleur/profil',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./bailleur/profil/profil.component').then((m) => m.ProfilComponent),
  },
  {
    path: 'gestionnaire',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./gestionnaire/dashboard/dashboard.component').then(
        (m) => m.GestionnaireDashboardComponent,
      ),
  },
  // Vérification publique d'une quittance certifiée (US-103) : SANS authGuard — cible du QR
  // imprimé, atteinte par des tiers non authentifiés (locataire, CAF, banque). La page pose
  // elle-même un `noindex`. Déclarée avant le fallback `**` pour ne pas être captée par lui.
  {
    path: 'verify/receipt/:id',
    loadComponent: () =>
      import('./public/verify-receipt/verify-receipt.component').then(
        (m) => m.VerifyReceiptComponent,
      ),
  },
  { path: '**', redirectTo: 'bailleur' },
];
