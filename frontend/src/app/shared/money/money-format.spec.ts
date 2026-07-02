import { formatMoney } from './money-format';

// Espace insécable U+00A0 (miroir du backend com.loyertracker.baux.Money) : échappement explicite,
// ne pas coller un espace ASCII depuis un exemple markdown.
const NBSP = '\u00A0';

describe('formatMoney', () => {
  it('formate EUR sans regroupement', () => {
    expect(formatMoney(800, 'EUR')).toBe(`800,00${NBSP}€`);
  });

  it('formate EUR avec regroupement de milliers', () => {
    expect(formatMoney(1200, 'EUR')).toBe(`1${NBSP}200,00${NBSP}€`);
  });

  it('formate USD au format natif', () => {
    expect(formatMoney(800, 'USD')).toBe('$800.00');
    expect(formatMoney(1000, 'USD')).toBe('$1,000.00');
  });

  it('formate CDF avec regroupement de milliers', () => {
    expect(formatMoney(1000, 'CDF')).toBe(`1${NBSP}000,00${NBSP}CDF`);
  });
});
