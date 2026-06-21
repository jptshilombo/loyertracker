import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ProfilBailleur, ProfilService } from './profil.service';

/**
 * Profil du bailleur courant (V11) : consultation de l'identité et saisie de l'adresse postale,
 * mention obligatoire de la quittance de loyer. L'adresse est requise avant toute génération de
 * quittance (lot suivant) — d'où l'invite explicite quand elle est absente.
 */
@Component({
  selector: 'app-profil',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="profil">
      <header>
        <h1>Mon profil</h1>
        <a routerLink="/bailleur">← Retour au tableau de bord</a>
      </header>

      @if (profil(); as p) {
        <dl class="identite">
          <dt>Nom</dt>
          <dd>{{ p.prenom }} {{ p.nom }}</dd>
          <dt>Email</dt>
          <dd>{{ p.email }}</dd>
        </dl>

        @if (!p.adresse) {
          <p class="invite" role="status">
            Renseignez votre adresse postale : elle figure obligatoirement sur les quittances de loyer.
          </p>
        }

        <form [formGroup]="form" (ngSubmit)="enregistrer()" class="panel">
          <label>
            Adresse postale
            <textarea formControlName="adresse" rows="3" maxlength="500"></textarea>
          </label>
          <button type="submit" [disabled]="form.invalid || enregistrement()">Enregistrer</button>
        </form>
      } @else {
        <p>Chargement…</p>
      }

      @if (message(); as m) {
        <p class="message" role="status">{{ m }}</p>
      }
    </section>
  `,
})
export class ProfilComponent implements OnInit {
  private readonly api = inject(ProfilService);

  readonly profil = signal<ProfilBailleur | null>(null);
  readonly enregistrement = signal(false);
  readonly message = signal<string | null>(null);

  readonly form = new FormGroup({
    adresse: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    this.api.consulter().subscribe({
      next: (p) => {
        this.profil.set(p);
        this.form.patchValue({ adresse: p.adresse ?? '' });
      },
      error: () => this.message.set('Impossible de charger le profil.'),
    });
  }

  enregistrer(): void {
    if (this.form.invalid) {
      return;
    }
    this.enregistrement.set(true);
    this.message.set(null);
    this.api.mettreAJourAdresse(this.form.getRawValue().adresse.trim()).subscribe({
      next: (p) => {
        this.profil.set(p);
        this.message.set('Adresse enregistrée.');
      },
      error: () => {
        this.message.set("Échec de l'enregistrement.");
        this.enregistrement.set(false);
      },
      complete: () => this.enregistrement.set(false),
    });
  }
}
