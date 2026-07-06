import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { PublicReceipt, VerifyReceiptService } from './verify-receipt.service';

type Etat = 'chargement' | 'valide' | 'invalide' | 'indisponible';

/**
 * Page publique de vérification d'une quittance certifiée (US-103, ADR-15 D5). Cible du QR imprimé :
 * atteinte sans compte, elle rend un verdict d'authenticité lisible par un tiers non technique
 * (locataire, CAF, banque) et permet de télécharger l'exemplaire officiel.
 *
 * <p>Sécurité/RGPD : la page ne fait qu'afficher le contrat public strict (K2) renvoyé par l'API ;
 * un verdict {@code INVALIDE} n'expose aucune cause (pas d'oracle). Elle se déclare {@code noindex}
 * pour rester hors des moteurs de recherche (défense en profondeur avec l'en-tête nginx).</p>
 */
@Component({
  selector: 'app-verify-receipt',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="verify" [attr.data-etat]="etat()">
      <a class="brand" href="/" aria-label="LoyerTracker">LoyerTracker</a>

      @switch (etat()) {
        @case ('chargement') {
          <section class="carte" aria-busy="true">
            <p class="attente" role="status">Vérification en cours…</p>
          </section>
        }

        @case ('valide') {
          @if (recu(); as q) {
            <section class="carte" aria-live="polite">
              <div class="sceau sceau--ok" aria-hidden="true">
                <span class="sceau__marque">✓</span>
                <span class="sceau__legende">Vérifié</span>
              </div>
              <h1 class="verdict verdict--ok">Quittance authentique</h1>
              <p class="sous">Vérifiée le {{ aujourdhui }} par LoyerTracker.</p>

              <div class="etat-ligne">
                <span class="jeton">{{ q.numero }}</span>
                <span class="jeton">Version {{ q.version }}</span>
                <span class="jeton jeton--statut" [attr.data-statut]="q.statut">
                  {{ libelleStatut(q.statut) }}
                </span>
              </div>

              @if (q.statut === 'REMPLACEE' && q.remplacanteNumero) {
                <p class="bandeau" role="note">
                  Cette quittance a été remplacée par
                  <strong>{{ q.remplacanteNumero }}</strong>
                  (version {{ q.remplacanteVersion }}). Demandez la version la plus récente.
                </p>
              }
              @if (q.statut === 'ANNULEE') {
                <p class="bandeau bandeau--rouge" role="note">
                  Cette quittance a été annulée par le bailleur et n'a plus de valeur.
                </p>
              }

              <dl class="donnees">
                <div><dt>Bailleur</dt><dd>{{ q.bailleurNom }}</dd></div>
                <div><dt>Adresse du bailleur</dt><dd>{{ q.bailleurAdresse }}</dd></div>
                <div><dt>Locataire</dt><dd>{{ q.locataireNom }}</dd></div>
                <div><dt>Logement</dt><dd>{{ q.bienAdresse }}</dd></div>
                <div><dt>Patrimoine</dt><dd>{{ q.patrimoineNom }}</dd></div>
                <div><dt>Période</dt><dd>{{ q.periodeLibelle }}</dd></div>
                <div><dt>Loyer hors charges</dt><dd>{{ montant(q.loyerHc, q.devise) }}</dd></div>
                <div><dt>Provision de charges</dt><dd>{{ montant(q.provisionCharges, q.devise) }}</dd></div>
                <div class="donnees__fort"><dt>Montant reçu</dt><dd>{{ montant(q.montantRecu, q.devise) }}</dd></div>
                <div><dt>Émise le</dt><dd>{{ dateFr(q.dateEmission) }}</dd></div>
              </dl>

              <div class="empreinte">
                <span class="empreinte__label">Empreinte SHA-256 du document certifié</span>
                <code class="empreinte__hash">{{ q.contentHash }}</code>
              </div>

              <a class="telecharger" [href]="urlPdf()" rel="nofollow">
                Télécharger le PDF officiel
              </a>
            </section>
          }
        }

        @case ('invalide') {
          <section class="carte" aria-live="polite">
            <div class="sceau sceau--ko" aria-hidden="true">
              <span class="sceau__marque">✕</span>
              <span class="sceau__legende">Non vérifié</span>
            </div>
            <h1 class="verdict verdict--ko">Quittance non authentifiée</h1>
            <p class="sous">
              Ce lien ne correspond à aucune quittance certifiée valide. Vérifiez que vous avez
              scanné le QR code d'origine, sans le modifier.
            </p>
          </section>
        }

        @case ('indisponible') {
          <section class="carte" aria-live="polite">
            <h1 class="verdict">Vérification indisponible</h1>
            <p class="sous">Le service est momentanément injoignable. Réessayez dans un instant.</p>
          </section>
        }
      }

      <footer class="pied">
        La vérification compare le document à l'exemplaire scellé conservé par LoyerTracker.
        Toute modification du PDF invalide cette preuve.
      </footer>
    </main>
  `,
  styles: [
    `
      /* Page-document délibérément claire (cohérente avec le PDF certifié A4), distincte du
         dashboard sombre. Surface papier, encre slate, sceau officiel comme signature. */
      :host {
        display: block;
        min-height: 100vh;
        background: #f7f8fa;
        color: #0f172a;
        font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
        padding: 2.5rem 1.25rem 3rem;
      }

      .brand {
        display: block;
        text-align: center;
        font-weight: 700;
        letter-spacing: 0.02em;
        color: #0f172a;
        text-decoration: none;
        margin-bottom: 1.5rem;
      }
      .brand::before {
        content: '';
        display: inline-block;
        width: 0.55rem;
        height: 0.55rem;
        margin-right: 0.5rem;
        border-radius: 2px;
        background: #38bdf8;
        vertical-align: baseline;
      }

      .carte {
        max-width: 680px;
        margin: 0 auto;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 16px;
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04), 0 12px 32px rgba(15, 23, 42, 0.08);
        padding: 2.5rem 2rem 2rem;
        text-align: center;
      }

      /* Signature : cachet rond façon tampon officiel. */
      .sceau {
        width: 118px;
        height: 118px;
        margin: 0 auto 1.25rem;
        border-radius: 50%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        border: 3px double currentColor;
        animation: cachet 480ms cubic-bezier(0.2, 0.9, 0.3, 1.4) both;
      }
      .sceau--ok {
        color: #047857;
        background: radial-gradient(circle at 50% 40%, #ecfdf5, #ffffff 72%);
      }
      .sceau--ko {
        color: #b42318;
        background: radial-gradient(circle at 50% 40%, #fef2f2, #ffffff 72%);
      }
      .sceau__marque {
        font-size: 2.75rem;
        line-height: 1;
        font-weight: 700;
      }
      .sceau__legende {
        margin-top: 0.2rem;
        font-size: 0.62rem;
        letter-spacing: 0.22em;
        text-transform: uppercase;
        font-weight: 700;
      }

      .verdict {
        font-family: Georgia, 'Times New Roman', serif;
        font-size: 1.9rem;
        line-height: 1.15;
        margin: 0 0 0.35rem;
        letter-spacing: -0.01em;
      }
      .verdict--ok {
        color: #065f46;
      }
      .verdict--ko {
        color: #91180f;
      }
      .sous {
        margin: 0 auto 1.5rem;
        max-width: 46ch;
        color: #475569;
        font-size: 0.95rem;
      }

      .etat-ligne {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        justify-content: center;
        margin-bottom: 1.25rem;
      }
      .jeton {
        font-size: 0.8rem;
        font-weight: 600;
        padding: 0.3rem 0.7rem;
        border-radius: 999px;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #e2e8f0;
      }
      .jeton--statut[data-statut='EMISE'] {
        background: #ecfdf5;
        color: #047857;
        border-color: #a7f3d0;
      }
      .jeton--statut[data-statut='REMPLACEE'] {
        background: #fffbeb;
        color: #b45309;
        border-color: #fde68a;
      }
      .jeton--statut[data-statut='ANNULEE'] {
        background: #fef2f2;
        color: #b42318;
        border-color: #fecaca;
      }

      .bandeau {
        text-align: left;
        margin: 0 0 1.25rem;
        padding: 0.8rem 1rem;
        border-radius: 10px;
        background: #fffbeb;
        border: 1px solid #fde68a;
        color: #7c5310;
        font-size: 0.9rem;
      }
      .bandeau--rouge {
        background: #fef2f2;
        border-color: #fecaca;
        color: #91180f;
      }

      .donnees {
        text-align: left;
        margin: 0 0 1.5rem;
        border-top: 1px solid #eef2f7;
      }
      .donnees > div {
        display: flex;
        justify-content: space-between;
        gap: 1.5rem;
        padding: 0.7rem 0.25rem;
        border-bottom: 1px solid #eef2f7;
      }
      .donnees dt {
        margin: 0;
        color: #64748b;
        font-size: 0.9rem;
      }
      .donnees dd {
        margin: 0;
        text-align: right;
        font-weight: 600;
      }
      .donnees__fort dd {
        font-size: 1.15rem;
        color: #065f46;
      }

      .empreinte {
        text-align: left;
        margin: 0 0 1.75rem;
      }
      .empreinte__label {
        display: block;
        font-size: 0.78rem;
        color: #64748b;
        margin-bottom: 0.35rem;
      }
      .empreinte__hash {
        display: block;
        font-family: ui-monospace, 'SFMono-Regular', 'Cascadia Code', Menlo, monospace;
        font-size: 0.82rem;
        word-break: break-all;
        line-height: 1.5;
        padding: 0.7rem 0.85rem;
        background: #0f172a;
        color: #7dd3fc;
        border-radius: 8px;
      }

      .telecharger {
        display: inline-block;
        text-decoration: none;
        font-weight: 600;
        color: #ffffff;
        background: #0284c7;
        padding: 0.75rem 1.5rem;
        border-radius: 10px;
        transition: background 150ms ease;
      }
      .telecharger:hover {
        background: #0369a1;
      }

      .attente {
        color: #475569;
        padding: 2rem 0;
      }

      .pied {
        max-width: 680px;
        margin: 1.75rem auto 0;
        text-align: center;
        font-size: 0.8rem;
        color: #94a3b8;
        line-height: 1.6;
      }

      a:focus-visible,
      .telecharger:focus-visible {
        outline: 3px solid #38bdf8;
        outline-offset: 3px;
      }

      @keyframes cachet {
        0% {
          opacity: 0;
          transform: scale(1.5) rotate(-9deg);
        }
        60% {
          opacity: 1;
        }
        100% {
          opacity: 1;
          transform: scale(1) rotate(-3deg);
        }
      }

      @media (max-width: 560px) {
        .carte {
          padding: 2rem 1.25rem 1.5rem;
        }
        .donnees > div {
          flex-direction: column;
          gap: 0.15rem;
        }
        .donnees dd {
          text-align: left;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .sceau {
          animation: none;
          transform: rotate(-3deg);
        }
      }
    `,
  ],
})
export class VerifyReceiptComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(VerifyReceiptService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  readonly etat = signal<Etat>('chargement');
  readonly recu = signal<PublicReceipt | null>(null);
  readonly aujourdhui = this.dateFr(new Date().toISOString().slice(0, 10));

  private id = '';
  private token: string | null = null;

  ngOnInit(): void {
    // Hors des moteurs de recherche (défense en profondeur avec l'en-tête nginx `X-Robots-Tag`).
    this.meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
    this.title.setTitle('Vérification d’une quittance — LoyerTracker');

    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.token = this.route.snapshot.queryParamMap.get('token');

    this.api.verifier(this.id, this.token).subscribe({
      next: (r) => {
        if (r.resultat === 'VALIDE' && r.quittance) {
          this.recu.set(r.quittance);
          this.etat.set('valide');
        } else {
          this.etat.set('invalide');
        }
      },
      // Une panne technique (réseau/5xx) n'est pas un verdict : état neutre distinct, sans oracle.
      error: () => this.etat.set('indisponible'),
    });
  }

  urlPdf(): string {
    return this.api.urlTelechargement(this.id, this.token);
  }

  libelleStatut(statut: PublicReceipt['statut']): string {
    return { EMISE: 'Émise', ANNULEE: 'Annulée', REMPLACEE: 'Remplacée' }[statut];
  }

  montant(valeur: string, devise: string): string {
    const n = Number(valeur);
    if (Number.isNaN(n)) {
      return `${valeur} ${devise}`;
    }
    try {
      return new Intl.NumberFormat('fr-FR', { style: 'currency', currency: devise }).format(n);
    } catch {
      return `${n.toFixed(2)} ${devise}`;
    }
  }

  dateFr(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return iso;
    }
    return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  }
}
