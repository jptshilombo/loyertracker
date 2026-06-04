# ADR-03 — RGPD by design

| Champ | Valeur |
|-------|--------|
| Statut | Acceptée |
| Date | 2026-06-04 |
| Phase | 05 — Architecture |
| Condition Gate 2 | « RGPD by design » |
| Exigences couvertes | ENF-04, ENF-05, BNF-04, BNF-13 |

## Contexte

LoyerTracker traite des **données personnelles** (locataires, bailleurs, gestionnaires). Le RGPD impose minimisation, base légale, droits d'accès/rectification/**effacement**, conservation et registre. Or il existe un **conflit direct** entre :
- le **droit à l'effacement** d'un locataire (ENF-04), et
- l'**obligation de conservation** de la chaîne d'audit et des pièces (bail + 3 ans, ENF-05).

Une suppression physique du locataire casserait l'intégrité financière (paiements, honoraires, garanties) et la traçabilité.

## Décision

**Pseudonymisation** plutôt que suppression physique, comme mécanisme d'effacement :

1. À la demande d'effacement, les **PII du locataire** (nom, contacts) sont anonymisées/effacées ; les enregistrements financiers et d'audit liés sont **conservés** mais **dissociés** de l'identité.
2. **Cartographie PII** : seules les entités `Locataire`, `Bailleur`, `Gestionnaire` portent des données personnelles → minimisation appliquée dès la conception du schéma.
3. **Registre des traitements** minimal (cf. EB Annexe A.2), incluant durées de conservation.
4. **Droit d'accès / portabilité** : endpoint d'**export** (`/api/rgpd/export`) = sérialisation des données scopées `bailleurId`.
5. L'opération d'effacement est elle-même **tracée** dans `AuditLog`.

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| **Suppression physique (hard delete)** | Casse l'intégrité référentielle (paiements/honoraires orphelins) et viole l'obligation de conservation/audit. |
| **Conservation intégrale sans effacement** | Non conforme au droit à l'effacement (RGPD art. 17). |
| **Chiffrement réversible des PII avec destruction de clé (crypto-shredding)** | Plus lourd à opérer ; bénéfice marginal vs pseudonymisation pour le périmètre MVP. Réévaluable post-MVP. |

## Conséquences

- ✅ Concilie effacement et conservation : conforme RGPD sans perte d'intégrité.
- ✅ Minimisation native par cartographie PII restreinte.
- ⚠️ Le schéma doit isoler les PII pour permettre une anonymisation ciblée (champs identifiants séparés des données métier).
- ⚠️ L'export RGPD doit être tenu cohérent avec l'évolution du modèle de données.
