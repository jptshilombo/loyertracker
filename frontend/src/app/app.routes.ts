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
  { path: '**', redirectTo: 'bailleur' },
];
