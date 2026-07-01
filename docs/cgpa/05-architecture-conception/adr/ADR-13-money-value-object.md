# ADR-13 — Value Object `Money` et devise exclusivement portée par le contrat (EP-11)

| Champ | Valeur |
|-------|--------|
| Code de décision | **D-DEV-001** |
| Statut | **Proposée** — en attente de validation PO (Plan d'Exécution `plan-execution-evolutions-ep10-ep13.md`, Sprint 8) |
| Date | 2026-07-01 |
| Phase | 07 — Développement (lot post-`1.5.0`, continu) |
| Documents liés | `analyse-impact-evolutions-ep10-ep13.md` §2, `addendum-backlog-ep10-ep12.md` (US-92/93) |

## Contexte

`Bail.devise` (enum `EUR`/`USD`/`CDF`) a été introduit par la migration **V17** (Sprint 5 Lot B3, US-59) et est aujourd'hui la seule colonne de devise du schéma — aucune duplication n'existe sur `Paiement`, `Honoraire`, `Garantie`. La règle métier « la devise est portée exclusivement par le contrat » est donc **déjà respectée au niveau du modèle de données**.

## Problème

L'exploration du code de génération de documents révèle un **défaut fonctionnel réel** : `DocumentHtmlBuilder.euros()` (`backend/.../documents/DocumentHtmlBuilder.java:74-76`) formate systématiquement les montants avec le suffixe `"€"` codé en dur, quelle que soit la devise réelle du bail. `QuittanceService.assembler()` ne lit jamais `bail.getDevise()` avant de construire `DonneesDocument` — la devise est perdue avant le rendu. Un bail en USD ou en CDF produit donc aujourd'hui une quittance affichant un montant suivi de « € », ce qui est incorrect et trompeur pour l'utilisateur.

Par ailleurs, aucun VO `Money` n'existe dans le code : tous les montants (`Bail`, `Paiement`, `Honoraire`, `Garantie`) sont de simples `BigDecimal`, sans association explicite à une devise sauf sur `Bail` lui-même.

## Décision

**Introduire un Value Object `Money(amount, currency)` porté par les couches de service/présentation, sans jamais dupliquer physiquement la devise sur une table autre que `bail`.**

Principes retenus :

1. **Aucune nouvelle colonne devise** sur `Paiement`, `Honoraire`, `Garantie` ou toute autre table — la devise reste une propriété exclusive de `Bail.devise`.
2. **`Money` est un objet de construction, pas un mapping JPA `@Embeddable` répété** : sur `Bail`, il enveloppe `loyerHc`/`provisionCharges`/`loyerCc`/`depotGarantie` en les associant à `this.devise` au moment de l'accès (méthode utilitaire côté entité ou service). Sur `Paiement`/`Honoraire`, `Money` est construit à la volée via une factory (`Money.of(bail.getDevise(), montant)`), résolue par la relation existante vers le `bailId`.
3. **`DonneesDocument`** (couche de génération de quittance/avis d'échéance) porte des `Money` au lieu de `BigDecimal` nus — le rendu (`DocumentHtmlBuilder`) formate chaque montant selon sa devise réelle, remplaçant l'actuel `euros()` unique par un formatage devise-aware (un format par valeur de `Devise`, à valider avec le PO pour CDF en particulier, qui n'a pas de convention de symbole aussi établie que EUR/USD).
4. **Test existant réécrit consciemment** : `DocumentHtmlBuilderTest` verrouille aujourd'hui le comportement bugué (assertions littérales `"€"`) — sa réécriture est un changement de comportement assumé et documenté, pas une régression.
5. **Portée frontend** : le typage `devise: string` des interfaces TypeScript (`s02-api.service.ts`) est durci en union `'EUR' | 'USD' | 'CDF'`. L'extension de l'affichage de la devise aux vues Paiements/Honoraires (aujourd'hui muettes sur ce point) est une story distincte (US-93), dont la portée reste à confirmer par le PO — elle n'est pas requise par la règle métier initiale, qui porte sur les documents.

## Conséquences

- ✅ Corrige un bug réel visible par l'utilisateur final (quittances/avis d'échéance affichant la mauvaise devise pour tout bail non-EUR).
- ✅ Aucune duplication de devise introduite — conforme strictement à la règle métier du PO.
- ✅ N'affecte pas le schéma de `Bail` (la colonne `devise` existe déjà depuis V17) — aucune migration Flyway requise pour cette évolution.
- ⚠️ Nécessite une décision de format d'affichage par devise avant codage (EUR : « 800,00 € », USD : à trancher entre « $800.00 » et « 800,00 USD », CDF : aucune convention établie dans le projet à ce jour).
- ⚠️ Le ledger de garantie (ADR-14, EP-12) devra utiliser ce même VO `Money` pour ses mouvements — séquencement Sprint 8 avant Sprint 9/10 recommandé dans le Plan d'Exécution.

## Risques

| Risque | Niveau | Mitigation |
|--------|--------|------------|
| Format d'affichage CDF non normé, risque d'incohérence entre bailleurs | Moyen | Décision PO explicite avant codage, documentée dans la clôture de Sprint 8 |
| Duplication accidentelle de devise sur `Paiement`/`Honoraire` par erreur d'implémentation | Moyen | Revue de code dédiée avant fusion : aucune colonne `devise` ne doit apparaître sur ces tables |
| Régression du test existant perçue comme une rupture non intentionnelle | Faible | Documentée explicitement dans cette ADR et dans le rapport de clôture Sprint 8 comme changement de comportement volontaire |

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| Dupliquer `devise` sur `Paiement`/`Honoraire`/`Garantie` pour simplifier le formatage local | Rejeté explicitement par la règle métier du PO (« ne jamais enregistrer la devise dans les documents/autres entités ») |
| Utiliser une librairie tierce de type `javax.money`/Moneta | Sur-ingénierie pour 3 devises fixes (EUR/USD/CDF) déjà contraintes par `CHECK` en base ; un VO simple suffit et reste cohérent avec le style du projet (pas de dépendance nouvelle non justifiée) |
| Corriger uniquement `DocumentHtmlBuilder.euros()` sans introduire de VO `Money` | Traiterait le symptôme (affichage) sans structurer le passage de la devise à travers `QuittanceService`/`DonneesDocument` ; risque de récidive si un nouveau type de document est ajouté plus tard |
