# Addendum Backlog — Epics EP-10, EP-11, EP-12 (Patrimoine enrichi, Devise/Money, Garantie ledger)

| Champ | Valeur |
|-------|--------|
| Document de référence | `product-backlog.md` (✅ Validé — Gate 5 Go, 2026-06-04) et `addendum-patrimoine-backlog.md` (EP-09) — **non modifiés** |
| Statut | **Proposé — en attente de validation PO** (kickoff sprint par sprint, `plan-execution-evolutions-ep10-ep13.md`) |
| Date | 2026-07-01 |
| Décisions liées | ADR-12 (D-PAT-002), ADR-13 (D-DEV-001), ADR-14 (D-GAR-001) |

> **Numérotation.** US-01→85 sont déjà occupées (EP-01→09, produit backlog + addendum Patrimoine). Ce document introduit **US-90 à US-98** sous trois nouveaux epics **EP-10, EP-11, EP-12**, afin de ne créer aucune collision et de ne modifier aucun backlog déjà validé.

---

## EP-10 — Patrimoine enrichi

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-10 | **Patrimoine enrichi** — champs géographiques/administratifs, adresse obligatoire | Post-`1.5.0` (Sprint 7) | Must |

### US-90 — Champs additionnels du Patrimoine

**En tant que** bailleur, **je veux** renseigner la localisation détaillée et des informations administratives sur un patrimoine **afin de** mieux le qualifier et le retrouver.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un bailleur créant/modifiant un patrimoine **W** il renseigne `ville, commune, quartier, province_etat, pays, description, reference_interne` (tous optionnels) **T** les valeurs sont persistées et restituées telles quelles par l'API. **G** un patrimoine sans `adresse` renseignée **W** il tente de le créer/modifier **T** la requête est rejetée (400, `adresse` désormais obligatoire). **G** les patrimoines existants sans `adresse` **W** la migration s'exécute **T** 0 patrimoine ne viole la contrainte `NOT NULL` après migration (backfill si nécessaire, arbitrage PO). |
| Dépendances | Aucune |
| Priorité | Must |
| Points | 5 |
| Risques | Migration bloquante si des patrimoines Production ont `adresse IS NULL` — comptage préalable obligatoire (cf. ADR-12) |
| Source | ADR-12, demande PO Évolution 1 |

### US-91 — Écran de gestion Patrimoine (périmètre à trancher)

**En tant que** bailleur, **je veux** créer et gérer mes patrimoines depuis une interface dédiée **afin de** ne plus dépendre d'un appel API direct.

| Champ | Détail |
|-------|--------|
| Constat | Aucun point de création UI n'existe aujourd'hui (`POST /api/patrimoines` accessible uniquement en API directe) ; le formulaire d'édition inline actuel ne couvre que `nom`/`adresse`. |
| Critères d'acceptation (GWT) | **À trancher au kickoff Sprint 7** : soit extension du formulaire inline existant avec les nouveaux champs, soit livraison d'un écran CRUD Patrimoine dédié incluant la création. |
| Dépendances | US-90 (les champs doivent exister avant l'écran) |
| Priorité | Should *(portée dépendante d'une décision PO explicite)* |
| Points | 3 (extension inline) ou 8 (écran dédié complet) — à recadrer au kickoff |
| Risques | Sous-estimation si le PO opte pour un écran dédié complet plutôt qu'une extension |
| Source | Constat d'analyse d'impact §1.4 |

---

## EP-11 — Devise / Money

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-11 | **Devise portée par le contrat / Value Object Money** — correction de l'affichage devise dans les documents | Post-`1.5.0` (Sprint 8) | Must |

### US-92 — Correction de l'affichage de la devise dans les documents

**En tant que** bailleur ou locataire, **je veux** que la quittance et l'avis d'échéance affichent la devise réelle du contrat **afin de** ne pas être induit en erreur sur le montant dû.

| Champ | Détail |
|-------|--------|
| Constat | `DocumentHtmlBuilder.euros()` affiche systématiquement « € », y compris pour les baux en USD/CDF — bug confirmé (cf. analyse d'impact §2.1). |
| Critères d'acceptation (GWT) | **G** un bail en devise USD ou CDF **W** une quittance ou un avis d'échéance est généré **T** le document affiche les montants dans la devise réelle du bail, correctement formatés. **G** un bail en EUR **W** génération d'un document **T** aucune régression du format actuel. |
| Dépendances | Aucune |
| Priorité | Must |
| Points | 5 |
| Risques | Décision de format d'affichage CDF non normée à trancher avant codage ; réécriture assumée de `DocumentHtmlBuilderTest` |
| Source | ADR-13, demande PO Évolution 2 |

### US-93 — Affichage de la devise sur Paiements et Honoraires (portée à confirmer)

**En tant que** bailleur, **je veux** voir la devise à côté des montants de paiements et d'honoraires **afin d'**avoir une lecture cohérente sur tout le dashboard.

| Champ | Détail |
|-------|--------|
| Constat | `paiements-bien.component.ts` et `honoraires-bien.component.ts` affichent des montants nus, sans devise, contrairement au dashboard Bail qui affiche déjà `{{ bail.devise }}`. |
| Critères d'acceptation (GWT) | **G** une vue Paiements ou Honoraires **W** affichage d'un montant **T** la devise du bail parent est affichée à côté. |
| Dépendances | US-92 (VO `Money` doit exister) |
| Priorité | Should *(hors périmètre strict de la demande initiale, à confirmer par le PO)* |
| Points | 2 |
| Risques | Aucun risque technique significatif — risque de périmètre uniquement |
| Source | Constat d'analyse d'impact §2.1 |

---

## EP-12 — Garantie : compte à mouvements

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-12 | **Garantie — compte à mouvements (ledger)** — modèle, usage métier, réapprovisionnement, historique | Post-`1.5.0` (Sprints 9-10) | Must |

### US-94 — Modèle ledger et migration rétroactive

**En tant que** système, **je veux** que chaque garantie soit soutenue par un historique de mouvements **afin que** son solde soit toujours recalculable et auditable.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une garantie existante en Production **W** la migration s'exécute **T** un mouvement `DEPOT_INITIAL` (et `RESTITUTION`/`AJUSTEMENT` si déjà restituée) est créé, le solde recalculé égale l'état actuel exactement. **G** une modification directe tentée sur `garantie.solde_actuel` **T** impossible — seule l'insertion d'un mouvement fait évoluer le solde (appliqué au niveau service, pas de mutation directe exposée). |
| Dépendances | ADR-14 tranchée (notamment devenir de `bail.depot_garantie`) ; recommandé après US-92 (Money) |
| Priorité | Must |
| Points | 8 |
| Risques | **Élevé** — migration rétroactive de données réelles de Production ; rupture potentielle du batch `GARANTIE_NON_RESTITUEE` si le cache statut n'est pas maintenu de façon synchrone |
| Source | ADR-14, demande PO Évolutions 3/6 |

### US-95 — Utilisation de la garantie sur impayé (décision explicite)

**En tant que** gestionnaire, **je veux** décider explicitement d'utiliser la garantie pour couvrir un loyer impayé, et choisir le montant **afin de** garder le contrôle humain sur cette décision.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un loyer impayé **W** le gestionnaire répond « non » à « utiliser la garantie ? » **T** aucun mouvement n'est créé, aucun prélèvement automatique n'a lieu. **G** le gestionnaire répond « oui » et choisit un montant **T** un mouvement `RETENUE_LOYER` est créé, relié au paiement concerné (`paiement.garantie_movement_id`), tracé dans `audit_log`. |
| Dépendances | US-94 |
| Priorité | Must |
| Points | 5 |
| Risques | Incohérence cross-table `bailleur_id` entre `paiement` et `garantie_movement` non vérifiable par contrainte DB — validation service-level requise |
| Source | ADR-14, demande PO Évolution 4 |

### US-96 — Réapprovisionnement de la garantie

**En tant que** gestionnaire, **je veux** ajouter un complément de garantie à tout moment **afin de** reconstituer le solde après une retenue.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une garantie active **W** le gestionnaire ajoute un complément avec justification **T** un mouvement `COMPLEMENT` est créé, le solde recalculé augmente d'autant, tracé (date, utilisateur, justification) dans `audit_log`. |
| Dépendances | US-94 |
| Priorité | Must |
| Points | 3 |
| Risques | Aucun risque majeur — story additive simple sur le modèle déjà posé par US-94 |
| Source | ADR-14, demande PO Évolution 5 |

### US-97 — Historique de garantie (écran dédié)

**En tant que** bailleur ou gestionnaire, **je veux** consulter l'historique complet des mouvements d'une garantie, triable/filtrable/exportable **afin de** comprendre l'évolution du solde.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une garantie avec plusieurs mouvements **W** consultation de l'historique **T** affichage de date/type/débit/crédit/solde/auteur/observation/document associé, triable par colonne, filtrable, exportable. |
| Dépendances | US-94, US-95, US-96 (mouvements réels à afficher) |
| Priorité | Must |
| Points | 5 |
| Risques | Aucun risque technique majeur — premier écran Garantie autonome (aujourd'hui géré inline) |
| Source | ADR-14, demande PO Évolution 6 |

### US-98 — Note d'architecture Fin de bail (documentation uniquement)

**En tant qu'**architecte, **je veux** documenter comment le ledger de garantie supportera la fin de bail **afin de** préparer l'avenir sans développer prématurément.

| Champ | Détail |
|-------|--------|
| Constat | Restitution totale/partielle et retenues diverses sont déjà nativement couvertes par `TypeMouvementGarantie` — aucune nouvelle table nécessaire. |
| Critères d'acceptation | Note d'architecture ajoutée à ADR-14 (§Décision point 7) décrivant le futur point d'extension (`ClotureBailService`). Aucun code produit. |
| Dépendances | US-94 (le ledger doit exister conceptuellement) |
| Priorité | Should — documentation pure |
| Points | 1 |
| Risques | Aucun — explicitement non développé, cf. demande PO Évolution 7 |
| Source | ADR-14 §Décision point 7, demande PO Évolution 7 |

---

## Récapitulatif & priorisation

| Epic | Stories | Total pts | Must | Should |
|------|---------|-----------|------|--------|
| EP-10 Patrimoine enrichi | US-90→91 | 8-13 | 5 | 3-8 |
| EP-11 Devise/Money | US-92→93 | 7 | 5 | 2 |
| EP-12 Garantie ledger | US-94→98 | 22 | 21 | 1 |

> Charge indicative totale : **37-42 pts** ÷ ~20 pts/sprint (vélocité de référence du projet) ≈ **2 sprints de développement pur**, découpés en **4 sprints gouvernés** (Sprint 7 à 10, `plan-execution-evolutions-ep10-ep13.md`) pour isoler le risque élevé de la migration rétroactive du ledger Garantie (US-94) et permettre une validation PO intermédiaire à chaque étape (format devise, périmètre écran Patrimoine, devenir de `bail.depot_garantie`).

## Dépendances & risques (synthèse)

| Élément | Type | Impact |
|---------|------|--------|
| US-90 doit précéder US-91 | Dépendance technique | Bloquant Sprint 7 |
| US-92 recommandé avant US-94→97 (Money avant Garantie) | Dépendance de conception | Séquencement Sprint 8 → 9/10 |
| US-94 doit précéder US-95/96/97 | Dépendance technique | Bloquant Sprint 9→10 |
| Comptage `patrimoine.adresse IS NULL` en Production | Risque donnée | À exécuter avant tout commit de migration US-90 |
| Décision PO sur `bail.depot_garantie` | Risque gouvernance | Bloquant avant migration US-94 |
| Migration rétroactive des garanties existantes | Risque donnée majeur | Vérification manuelle en Staging avant fusion US-94 |
