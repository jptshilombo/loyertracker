# Checklist QA / recette

> À dérouler au **Gate 7 (QA & recette)**.

## 1. Couverture fonctionnelle
- [ ] Chaque exigence du CDC tracée vers un cas de test
- [ ] Cas nominaux et cas d'erreur couverts
- [ ] Critères d'acceptation vérifiés

## 2. Exécution des tests
- [ ] Tests unitaires passants
- [ ] Tests d'intégration passants
- [ ] Tests end-to-end / parcours utilisateurs
- [ ] Tests de non-régression

## 3. Qualité non fonctionnelle
- [ ] Tests de sécurité (authz/authn, OWASP)
- [ ] Tests de performance / charge (si applicable)
- [ ] Accessibilité / compatibilité navigateurs (web)
- [ ] Tests mobile (si applicable)

## 4. Anomalies
- [ ] Anomalies recensées et qualifiées (sévérité)
- [ ] Aucune anomalie bloquante ouverte
- [ ] Plan de traitement des anomalies restantes

## 5. Données & environnement
- [ ] Recette sur environnement `staging` représentatif
- [ ] Jeux de données de test maîtrisés

## 6. Formalisation
- [ ] Rapport QA complété (`rapport-qa.md`)
- [ ] PV de recette signé (`pv-recette.md`)
