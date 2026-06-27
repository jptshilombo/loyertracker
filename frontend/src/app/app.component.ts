import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { NavbarComponent } from './shared/navbar/navbar.component';

@Component({
    selector: 'app-root',
    imports: [RouterOutlet, NavbarComponent],
    template: `
    <a class="skip-link" href="#main-content">Aller au contenu principal</a>
    <app-navbar></app-navbar>
    <main id="main-content" class="container">
      <router-outlet></router-outlet>
    </main>
  `
})
export class AppComponent {}
