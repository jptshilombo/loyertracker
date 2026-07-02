import { Devise } from '../../core/s02/s02-api.service';

// Espace insécable U+00A0 (ADR-13, décision PO kickoff 2026-07-02) : échappement explicite,
// miroir exact du backend (com.loyertracker.baux.Money), plutôt que de dépendre du séparateur de
// regroupement par défaut de l'ICU du runtime.
const NBSP = '\u00A0';

/** Formatage propre à chaque devise (décision PO, ADR-13, kickoff 2026-07-02). */
export function formatMoney(montant: number, devise: Devise): string {
  switch (devise) {
    case 'USD':
      return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(montant);
    case 'EUR':
      return `${formatFr(montant)}${NBSP}€`;
    case 'CDF':
      return `${formatFr(montant)}${NBSP}CDF`;
  }
}

function formatFr(montant: number): string {
  return new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    .formatToParts(montant)
    .map((part) => (part.type === 'group' ? NBSP : part.value))
    .join('');
}
