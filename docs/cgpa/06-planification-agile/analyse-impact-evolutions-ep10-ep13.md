# Analyse d'Impact — Évolutions Patrimoine enrichi / Devise-Money / Garantie ledger / Fin de bail

| Champ | Valeur |
|-------|--------|
| Statut | **Documentation seule — aucun développement engagé.** Conforme à la règle CGPA « Codage suspendu : Plan d'Exécution requis avant modification du code ». |
| Date | 2026-07-01 |
| Demandeur | PO (jordan) |
| Niveau | **Niveau 3** (changement structurant du modèle de données + sécurité, plusieurs entités) |
| Documents liés | À produire après validation : ADR-12→15, addendum backlog EP-10→13, `plan-execution-evolutions-ep10-ep13.md` |
| Contexte de gouvernance | Release `1.5.0` a atteint `PRODUCTION_DEPLOYED` (2026-07-01 11:02 UTC) mais **n'est pas encore clôturée** — Hypercare T0/T+12/T+24 et décision CDO restent dus. Cette analyse est purement documentaire et n'ouvre aucun Sprint ; elle ne dépend pas de la clôture `1.5.0` mais tout Sprint réel (Sprint 7+) devra être kick-offé séparément, sans confondre clôture de release et autorisation de nouveaux travaux (règle CGPA explicite). |

---

## 0. Méthode

Trois explorations factuelles du code ont été menées avant toute conclusion (aucune modification) :
1. Modèle Patrimoine/Bien/Bail actuel.
2. Gestion actuelle de la devise dans les documents (quittance, avis d'échéance).
3. Modèle Garantie actuel et patterns RLS/audit à répliquer.

Les constats ci-dessous citent des fichiers et lignes réels — ce sont des faits, pas des hypothèses.

---

## 1. Évolution 1 — Patrimoine (enrichissement de champs)

### 1.1 Constat : le patrimoine est déjà une entité de premier niveau

Contrairement à l'énoncé de la demande, **Patrimoine est déjà un niveau intermédiaire obligatoire** entre `Bailleur` et `Bien`, livré et déployé en Production sous l'Epic **EP-09** (`ADR-11`, US-80→85, Sprints 1-4, migrations V12→V16). Le cardinalité demandée est **déjà en place** :

- `Bailleur (1) ──< (N) Patrimoine` — RLS `bailleur_id`.
- `Patrimoine (1) ──< (N) Bien` — **obligatoire**, `bien.patrimoine_id NOT NULL` depuis V12, `@Column(nullable = false)` sur `Bien.patrimoineId`.
- Aucune régression à craindre sur ce point : rien à faire ici.

Ce qui **manque réellement** par rapport à la demande, ce sont les **champs**. Schéma actuel de `patrimoine` (V12 + V16) :

```
id, bailleur_id, nom (NOT NULL), statut (ACTIF/ARCHIVE, NOT NULL), date_creation, adresse (nullable, V16)
```

Champs demandés absents : `ville`, `commune`, `quartier`, `province_etat`, `pays`, `description`, `reference_interne`. Le champ `statut` (ACTIF/ARCHIVE) **existe déjà** — rien à ajouter là-dessus.

### 1.2 Changement de comportement requis (point d'attention)

La demande rend `nom` et `adresse` **obligatoires**. `nom` l'est déjà (`NOT NULL` depuis V12). **`adresse` est aujourd'hui nullable** (ajouté par V16 comme colonne optionnelle) — la rendre `NOT NULL` est un **changement cassant** : il faut d'abord vérifier s'il existe des patrimoines en Production avec `adresse IS NULL`, et si oui, définir une valeur de bascule (ex. reprise manuelle ou valeur placeholder à corriger a posteriori) avant `ALTER COLUMN adresse SET NOT NULL`. C'est une migration à deux temps (backfill puis contrainte), sur le modèle déjà pratiqué en V12 pour `bien.patrimoine_id`.

### 1.3 Fichiers impactés (backend)

- `Patrimoine.java` — ajout des 7 champs + constructeur/`modifier()` étendu.
- `PatrimoineDto.java`, `PatrimoineRequest.java` — extension avec validation Bean (`@NotBlank` sur nom/adresse, `@Size` sur les nouveaux champs texte).
- `PatrimoineService.renommer()` (ou renommage en `modifier()`) — signature étendue.
- `PatrimoineController.java` — inchangé en surface (mêmes endpoints), payloads étendus.
- Nouvelle migration Flyway (V19) : `ALTER TABLE patrimoine ADD COLUMN ville/commune/quartier/province_etat/pays/description/reference_interne`, backfill `adresse`, puis `SET NOT NULL` sur `adresse`.
- `RgpdService` — l'export bailleur inclut déjà les patrimoines (à vérifier : le DTO d'export doit suivre l'extension de `PatrimoineDto`).

### 1.4 Fichiers impactés (frontend)

- Aucune page CRUD Patrimoine dédiée n'existe aujourd'hui (confirmé : gestion inline dans `dashboard.component.ts`, formulaire actuel limité à `nom`/`adresse`). Il n'existe **aucun point de création UI** (`POST /api/patrimoines` uniquement en API directe) — écart déjà documenté (`plan-execution-hotfix-bien-patrimoine-frontend.md`).
- Étendre le formulaire "Modifier un patrimoine" existant avec les 7 nouveaux champs.
- `s02-api.service.ts` — interfaces `Patrimoine`/`PatrimoinePayload` à étendre.
- **Décision à trancher avec le PO** : cette évolution est-elle l'occasion de livrer enfin un écran CRUD Patrimoine dédié (création UI incluse), ou reste-t-on sur l'extension du formulaire inline existant ? Ce point n'est pas tranché par la demande initiale.

### 1.5 Rapports impactés

Aucun rapport (quittance/avis d'échéance) n'affiche aujourd'hui les champs Patrimoine — pas d'impact PDF direct pour cette évolution seule.

### 1.6 Risques

| Risque | Niveau | Mitigation |
|---|---|---|
| Patrimoines existants avec `adresse` NULL → migration bloquante | Moyen | Requête de comptage à exécuter avant migration (Sprint dédié) ; backfill placeholder si nécessaire |
| Confusion avec le travail déjà livré (EP-09) → double développement | Faible | Cette analyse documente explicitement ce qui existe déjà (§1.1) |
| Absence de tests de non-régression sur `PatrimoineDto` (consommé par `RgpdService`, `AffectationController`) | Faible | Suite de tests existante (`PatrimoineServiceTest` et équivalents) à étendre, pas à réécrire |

---

## 2. Évolution 2 — Devise / `Money` Value Object

### 2.1 Constat : la devise existe déjà sur le contrat (Bail), mais est ignorée dans les documents

- `Bail.devise` existe déjà (`Devise` enum `EUR/USD/CDF`, colonne `bail.devise VARCHAR(3) NOT NULL DEFAULT 'EUR'`, migration **V17**, `ck_bail_devise CHECK`). La règle métier « la devise est portée exclusivement par le contrat » **est donc déjà respectée au niveau du schéma** — aucune duplication de devise n'existe sur `Paiement`, `Honoraire`, `Garantie`, `Patrimoine`, `Bien`.
- **Bug réel confirmé** : `DocumentHtmlBuilder.euros()` (`backend/.../documents/DocumentHtmlBuilder.java:74-76`) formatte **tout montant avec un « € » codé en dur**, quel que soit `bail.getDevise()`. `QuittanceService.assembler()` ne lit même pas `bail.getDevise()` en construisant `DonneesDocument` — la devise est **perdue** avant d'atteindre le rendu. Les quittances et avis d'échéance d'un bail en USD ou CDF affichent donc un montant en « € » aujourd'hui, ce qui est trompeur et doit être corrigé — c'est le cœur légitime de cette évolution.
- Le test `DocumentHtmlBuilderTest.java:37` verrouille actuellement ce comportement bugué (assertions littérales `"€"`) — il devra être réécrit, pas juste étendu.
- Le frontend (`dashboard.component.ts`) affiche déjà `{{ bail.devise }}` en toutes lettres à côté des montants de bail — **aucune régression frontend** sur ce point précis. En revanche, `paiements-bien.component.ts` et `honoraires-bien.component.ts` n'affichent **aucune devise** aujourd'hui (montants nus) : à corriger dans le cadre de cette évolution si le PO le souhaite (hors périmètre strict si on limite l'évolution aux documents PDF).

### 2.2 Conception proposée — `Money`

Créer un VO `Money` (`amount: BigDecimal`, `currency: Devise`) **non persistant en tant que colonne dupliquée** :
- Sur `Bail` : `Money` peut envelopper `loyerHc`/`provisionCharges`/`loyerCc`/`depotGarantie` en les associant à `this.devise` au moment de la construction (mapping JPA inchangé : la colonne `devise` reste unique en base, `Money` est reconstruit par une méthode utilitaire côté entité/service, pas via `@Embeddable` répété par montant — pour ne pas dupliquer physiquement 4 colonnes de devise).
- Sur `Paiement`, `Honoraire` : ces entités n'ont et n'auront **aucune colonne devise propre** ; `Money` y est construit à la volée par un factory (`Money.of(paiement.getBail().getDevise(), montant)`), résolu via le `bailId` déjà présent — respecte strictement l'exigence « ne jamais dupliquer la devise ».
- Sur `DonneesDocument` (documents) : remplacer les `BigDecimal` bruts par des `Money`, et faire porter le formatage (symbole/suffixe par devise) dans une méthode dédiée du VO ou de `DocumentHtmlBuilder`, remplaçant `euros()`.

### 2.3 Fichiers impactés

**Backend** : `Devise.java` (inchangé), nouveau `Money.java` (VO, package `com.loyertracker.commun` ou `com.loyertracker.baux`), `Bail.java` (méthodes d'accès `Money`), `BailDto.java` (retrait du fallback `Devise.EUR.name()` codé en dur ligne 16 — actuellement un défaut silencieux qui masque un `devise` null, à clarifier : `devise` est `NOT NULL` en base donc ce fallback est probablement du code mort/défensif à vérifier), `QuittanceService.java` (lire `bail.getDevise()`), `DonneesDocument.java` (ajout du champ devise/Money), `DocumentHtmlBuilder.java` (remplacer `euros()` par un formatage devise-aware), `DocumentHtmlBuilderTest.java` (réécriture des assertions).

**Frontend** : `s02-api.service.ts` (typer `devise` en union `'EUR'|'USD'|'CDF'` plutôt que `string` brut — durcissement mineur), `paiements-bien.component.ts`, `honoraires-bien.component.ts` (afficher la devise si le PO valide l'extension de périmètre).

### 2.4 Risques

| Risque | Niveau | Mitigation |
|---|---|---|
| Formatage devise-aware incorrect pour CDF (pas de convention de symbole standard) | Moyen | Trancher avec le PO le format d'affichage par devise (ex. « 1 000,00 CDF », « $1,000.00 », « 800,00 € ») avant codage |
| Test verrouillé sur le bug actuel (`DocumentHtmlBuilderTest`) | Faible | Réécriture assumée, documentée comme changement de comportement volontaire |
| Confusion Money VO ↔ duplication accidentelle de devise sur Paiement/Honoraire | Moyen | Revue de code dédiée : `Money` ne doit jamais être annoté `@Embeddable` avec une colonne devise physique sur ces tables |

---

## 3. Évolutions 3-6 — Garantie : compte à mouvements (ledger)

### 3.1 Constat : Garantie est aujourd'hui un instantané plat, pas un grand livre

Table `garantie` (V1) : `id, bailleur_id, bail_id, montant, type_garantie, date_depot, statut (DETENU/RESTITUE_PARTIEL/RESTITUE_TOTAL), montant_retenu, motif_retenue`. `GarantieService.restituer()` mute directement `statut`/`montant_retenu` — **aucun historique de mouvement n'existe**, contrairement à l'exigence « le solde est toujours recalculable, aucune modification directe du solde n'est autorisée ».

**Point d'attention découvert** : `bail.depot_garantie` (colonne V1 sur `bail`) est un **champ dupliqué historique**, distinct de la table `garantie` — deux sources de vérité coexistent déjà aujourd'hui pour le montant de dépôt. Cette évolution est l'occasion de clarifier/résorber cette duplication (à trancher avec le PO : `bail.depot_garantie` devient-il un champ d'affichage dérivé du ledger, ou reste-t-il un champ contractuel distinct du dépôt réellement versé ?).

### 3.2 Conception proposée

- **`Garantie`** devient l'agrégat racine du compte : `id, contratId (bailId), montantInitial, soldeActuel (dérivé, recalculé), dateCreation, statut`. Le `statut`/`montantRetenu`/`motifRetenue` actuels sont soit supprimés, soit conservés comme **colonnes de cache** recalculées à chaque mouvement (recommandé pour la performance de lecture, à condition qu'un test verrouille l'invariant « cache == somme des mouvements »).
- **`GarantieMovement`** (nouvelle table `garantie_movement`) : `id, garantie_id, date, type (TypeMouvementGarantie), debit, credit, solde_apres, motif, utilisateur, commentaire, reference_document`. RLS via `bailleur_id` (copié sur la table, pattern V12 déjà identifié).
- **`TypeMouvementGarantie`** enum : `DEPOT_INITIAL, COMPLEMENT, RETENUE_LOYER, RETENUE_CHARGES, RETENUE_REPARATION, RESTITUTION, AJUSTEMENT, ANNULATION` — mirroring exact du pattern `StatutPaiement`/`TypeException` (enum Java + `CHECK` DB, pas de type ENUM Postgres natif, conforme à la convention du projet).
- **Utilisation de la garantie (Évolution 4)** : aucun prélèvement automatique. `GarantieService` expose une opération explicite (ex. `POST /garanties/{id}/mouvements` avec `type=RETENUE_LOYER`, `montant`, `paiementId`) déclenchée par le gestionnaire après choix explicite oui/non + montant. `Paiement` gagne une **FK nullable** `garantie_movement_id` (migration additive simple, pattern déjà utilisé pour `bien.patrimoine_id` en V12, sans backfill nécessaire ici car nullable et aucune donnée existante à réconcilier).
- **Réapprovisionnement (Évolution 5)** : mouvement `COMPLEMENT`, mêmes garde-fous (date, utilisateur, justification obligatoire dans `motif`).
- **Historique (Évolution 6)** : nouvel endpoint `GET /garanties/{id}/mouvements` + écran Angular dédié (tri/filtre/export) — première vraie page Garantie autonome (aujourd'hui la garantie est gérée inline dans `garanties-bail.component.ts`, sans historique).
- **Audit** : chaque mouvement appelle `AuditService.enregistrer(authentication, bailleurId, "<ACTION>", "garantie_movement", movementId)` — pattern strictement identique à `RgpdService.anonymiserLocataire` (`EFFACEMENT_LOCATAIRE`) déjà en Production.

### 3.3 Points de compatibilité à traiter explicitement

- **Batch alertes** (`V9`/`V10`, alerte `GARANTIE_NON_RESTITUEE`) lit directement `garantie.statut` et `garantie.bail_id` en `SECURITY DEFINER`/BYPASSRLS. Si `statut` devient un champ dérivé/cache, la requête batch reste compatible **seulement si le cache est maintenu de façon transactionnelle et synchrone** à chaque mouvement (pas de recalcul asynchrone) — à valider en conception détaillée.
- **`RgpdService`** exporte aujourd'hui `GarantieDto::from(Garantie)` en lisant `GarantieRepository.findByBailIdOrderByDateDepotDesc` — l'export RGPD devra être étendu pour inclure l'historique des mouvements (cohérent avec le droit d'accès RGPD, qui doit couvrir l'intégralité des données personnelles/financières liées).
- **Postgres ne vérifie pas nativement la cohérence `bailleur_id`** entre `paiement` et `garantie_movement` via la FK ajoutée : à valider au niveau service (pattern déjà utilisé par `GarantieService.exigerBailDuBien`), pas de contrainte DB cross-table possible directement.

### 3.4 Fichiers impactés

**Backend** : `Garantie.java`, `GarantieRepository.java`, `GarantieService.java`, `GarantieController.java`, `GarantieDto.java`/`GarantieRequest.java`/`RestitutionRequest.java` (retravaillés), nouveaux `GarantieMovement.java`, `GarantieMovementRepository.java`, `TypeMouvementGarantie.java`, `GarantieMovementDto.java`, extension `Paiement.java` (FK nullable), `RgpdService.java`, requêtes SQL du batch alertes (`V9`/`V10`, si le format `statut` change de sémantique).

**Migrations Flyway** : nouvelle(s) migration(s) V20+ : création `garantie_movement` (RLS pattern V12), migration de données (chaque `garantie` existante génère un mouvement `DEPOT_INITIAL` rétroactif pour amorcer le ledger sans perte d'historique métier, et un mouvement `RESTITUTION`/`AJUSTEMENT` rétroactif pour les garanties déjà `RESTITUE_PARTIEL`/`RESTITUE_TOTAL`), ajout `paiement.garantie_movement_id`.

**Frontend** : `s03-api.service.ts` (types `GarantieMovement`, méthodes `listerMouvements`, `deposerComplement`, `utiliserGarantie`), extension de `garanties-bail.component.ts` (choix oui/non + montant pour retenue), nouveau composant historique garantie (tri/filtre/export — Évolution 6).

### 3.5 Risques

| Risque | Niveau | Mitigation |
|---|---|---|
| Migration rétroactive des garanties existantes en Production (génération de mouvements a posteriori) | **Élevé** | Script de migration de données dédié + vérification manuelle du solde recalculé == `montant - montant_retenu` actuel avant bascule ; à tester d'abord en Staging avec un jeu de données représentatif |
| Rupture du batch alertes `GARANTIE_NON_RESTITUEE` si le champ `statut` change de sémantique/emplacement | **Élevé** | Conserver `statut` comme colonne physique sur `garantie` (cache recalculé), ne pas la supprimer ; tests de non-régression du batch obligatoires avant fusion |
| Incohérence cross-table `bailleur_id` entre `paiement.garantie_movement_id` et `garantie_movement` non vérifiable par contrainte DB | Moyen | Validation service-level explicite (pattern `exigerBailDuBien`), test dédié |
| Duplication historique `bail.depot_garantie` vs ledger | Moyen | Décision PO à trancher en conception détaillée (§3.1) |

---

## 4. Évolution 7 — Fin de bail (préparation architecture uniquement)

Aucun développement demandé à ce stade — uniquement préparer le terrain. Recommandation minimale et réversible :
- Le modèle `GarantieMovement` de l'Évolution 3 couvre déjà nativement `RESTITUTION` (totale ou partielle) et les retenues (`RETENUE_LOYER/CHARGES/REPARATION`) comme des mouvements typés — **aucune nouvelle table n'est nécessaire pour préparer la fin de bail**, le ledger est conçu pour l'absorber directement.
- Point d'extension à documenter dans l'ADR (pas à coder) : un futur processus « clôture de bail » pourrait devenir un service dédié (`ClotureBailService`) orchestrant plusieurs mouvements de garantie en une transaction (restitution + retenues multiples), sans changement de schéma supplémentaire.
- Aucun impact fichier à ce stade — seulement une note d'architecture dans l'ADR Garantie (§ »Extensions futures »).

---

## 5. Risques transverses

| Risque | Niveau | Portée |
|---|---|---|
| Volume de travail réel largement supérieur à une release mineure — nécessite un découpage en plusieurs sprints gouvernés distincts (voir Plan d'Exécution) | Élevé | Toutes évolutions |
| Confusion clôture Sprint / autorisation Production (règle CGPA explicite) | Gouvernance | À surveiller à chaque Gate |
| `1.5.0` non clôturée (Hypercare en attente) au moment du démarrage d'un nouveau Sprint | Gouvernance | Documentaire seulement — ne bloque pas cette analyse, mais tout Sprint réel doit être kick-offé sous décision PO distincte |
| Money VO + Garantie ledger touchent tous deux les rapports PDF et les exports RGPD — dépendance croisée à séquencer (Money doit précéder ou accompagner le ledger pour l'affichage des montants de mouvement) | Moyen | Séquencement du plan |

---

## 6. Migrations Flyway proposées (récapitulatif, aucune créée à ce stade)

| Version proposée | Contenu | Évolution |
|---|---|---|
| V19 | `patrimoine` : ajout `ville, commune, quartier, province_etat, pays, description, reference_interne` ; backfill + `adresse SET NOT NULL` | 1 |
| V20 | Création `garantie_movement` (RLS pattern V12), backfill rétroactif des garanties existantes en mouvements `DEPOT_INITIAL`/`RESTITUTION`/`AJUSTEMENT` | 3 |
| V21 | `paiement.garantie_movement_id` (FK nullable) | 4 |

Numérotation indicative — à ajuster selon l'ordre réel de fusion des sprints.

---

## 7. Impact documentation (à produire après validation du plan)

- ADR-12 : Extension des champs Patrimoine (nom/adresse obligatoires + champs optionnels).
- ADR-13 : `Money` Value Object — devise portée exclusivement par le contrat.
- ADR-14 : Garantie — compte à mouvements (ledger), y compris note d'extension Fin de bail.
- Addendum backlog **EP-10** (Patrimoine enrichi), **EP-11** (Devise/Money), **EP-12** (Garantie ledger) — numérotation choisie pour ne pas entrer en collision avec EP-01→09 déjà validés.
- Mise à jour `product-backlog.md` (renvoi vers les addenda, pas de modification du contenu déjà validé — règle CGPA).
- Mise à jour ERD / modèle de domaine, `CHANGELOG.md` (`[Non publié]`), `docs/project-state.md` (bandeau de cadrage).

---

## 8. Conclusion de l'analyse d'impact

Les 3 évolutions structurantes (Patrimoine enrichi, Money VO, Garantie ledger) sont **additives et compatibles** avec l'architecture existante (RLS, ReBAC, Flyway, audit) — aucune ne nécessite de casser un contrat d'API existant, à l'exception du changement de nullabilité de `patrimoine.adresse` (§1.2, à cadrer en premier). Le risque principal est la **migration rétroactive du ledger Garantie** (§3.5) qui touche des données réelles de Production et doit faire l'objet d'un cadrage Sprint dédié avec vérification manuelle avant toute fusion.

**Aucun développement n'est engagé par ce document.** Voir `plan-execution-evolutions-ep10-ep13.md` pour le découpage en sprints gouvernés proposé.
