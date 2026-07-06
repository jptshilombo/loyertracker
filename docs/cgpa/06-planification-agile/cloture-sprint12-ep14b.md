# Clôture Sprint 12 — EP-14b Vérification publique des quittances

| Champ | Valeur |
|---|---|
| Date | 2026-07-06 |
| Stories | US-102, US-103, US-104 |
| PR | #202, merge `75646d8` |
| Release cible | `1.9.0` avec le Sprint 11 |
| Décision CDO | **GO — Sprint clôturé** |

## Résultat

- **US-102** : endpoints publics de vérification et téléchargement, projection K2 stricte,
  échec indifférencié, re-hash PDF et compteurs par quittance.
- **US-103** : page Angular publique sans `authGuard`, états valide/invalide/remplacée,
  téléchargement officiel et `noindex`.
- **US-104** : métriques Prometheus, journal RGPD-minimal et rate-limit Nginx.
- Documentation utilisateur, rotation HMAC, changelog et release notes `1.9.0` livrés.

## Vérifications

- CI post-merge `75646d8` intégralement verte : Backend, Frontend, Sécurité, Packaging Docker et
  CodeQL Java/TypeScript.
- Backend 162 tests ; frontend 94 tests ; Quality Gate Sonar backend PASS, couverture nouvelle
  89,8 %, zéro nouvelle violation.
- Revue sécurité dédiée PASS sans réserve bloquante.
- Images immuables API/Web `sha-75646d8f` publiées sur GHCR.
- Staging : smoke 62/0, parcours public invalide et valide, PDF intègre, rate-limit 429 et métriques
  vérifiés en conditions réelles.

## Périmètre et suite

Aucune story reportée dans EP-14b. La clôture du Sprint n'autorise pas la Production. La prochaine
décision distincte après le Gate Staging est le Gate Production unique `1.9.0`, couvrant Sprints
11 et 12 conformément à ADR-15 K5.
