# Addendum CDC — Introduction de la notion de Patrimoine

| Champ | Valeur |
|-------|--------|
| Document de référence | `cahier-des-charges.md` (✅ Validé — Gate 3 Go, 2026-06-04) — **non modifié** |
| Document de référence | `dossier-architecture.md` (✅ Validé — Gate 4 Go, 2026-06-04) — **non modifié** |
| Statut de l'addendum | **Accepté — Plan d'Exécution Patrimoine approuvé (GO) par le PO le 2026-06-21** ; aucun codage ni migration SQL engagé à ce stade (Sprint 1 autorisé à démarrer, `plan-execution-patrimoine.md`) |
| Date | 2026-06-21 |
| Décision liée | D-PAT-001 / ADR-11 |
| Base besoin | `docs/cgpa/02-expression-besoin/addendum-patrimoine.md` (BF-90→BF-96) |

---

## 1. Exigences fonctionnelles détaillées (addendum)

### 1.1 Module Patrimoine *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-90 | Gestion des patrimoines (CRUD) | ED un bailleur authentifié · Q il crée/renomme/archive un patrimoine · A le patrimoine est persisté, rattaché à son `bailleurId`, visible uniquement par lui (et les gestionnaires affectés à ce patrimoine). ED un patrimoine avec au moins une affectation patrimoine `ACTIVE` · Q le bailleur tente de l'archiver · A la requête est rejetée (400, **RS-06**, validé par le PO le 2026-06-21) ; révocation explicite préalable requise (cohérent EF-22). | Must | BF-90 |
| EF-91 | Typologie administrable des biens | ED un bailleur · Q il crée ou modifie un bien · A le `type` est choisi parmi une liste administrable (`Appartement`, `Boutique`, `Bureau`, `Villa`, `Terrain`, `Entrepôt`, `Autre`) ; une valeur hors liste est rejetée (400). | Must | BF-91 |
| EF-92 | Rattachement obligatoire Bien → Patrimoine | ED un bailleur · Q il crée un bien · A le bien est obligatoirement rattaché à un patrimoine existant de ce bailleur ; un `patrimoineId` d'un autre bailleur est rejeté (404/403). | Must | BF-92 |

### 1.2 Module Affectation enrichi *(étend EP-03 existant)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-93 | Affectation d'un gestionnaire à un patrimoine | ED un bailleur et un gestionnaire invité · Q il crée une affectation portant sur un `patrimoineId` (et non un `bienId`) · A l'affectation est persistée `ACTIVE`, et le gestionnaire accède à tous les biens présents/futurs du patrimoine, sous réserve d'EF-95. | Must | BF-93 |
| EF-94 | Affectation bien en complément ou restriction | ED une affectation patrimoine `ACTIVE` pour un gestionnaire · Q le bailleur crée une affectation bien avec `typeException = INCLUSION` ou `EXCLUSION` sur un bien de ce patrimoine pour ce même gestionnaire · A l'exception est persistée et appliquée selon EF-95. | Must | BF-94 |
| EF-95 | Priorité de l'affectation bien sur l'affectation patrimoine | ED un gestionnaire avec une affectation patrimoine `ACTIVE` et une affectation bien `ACTIVE` sur un bien de ce patrimoine · Q le périmètre effectif est résolu · A pour ce bien, le résultat de l'affectation **bien** (`INCLUSION` → accès, `EXCLUSION` → refus) prévaut sur l'héritage patrimoine. | Must | BF-95 |
| EF-96 | *(Non-régression, déjà couvert)* Unicité du contrat (`Bail`) actif par bien | Critère déjà couvert par EF-12 (`uq_bail_actif`). Aucune action de développement requise. | — | BF-96 |

---

## 2. Exigences non fonctionnelles (addendum)

| ID | Catégorie | Exigence | Critère d'acceptation | Source |
|----|-----------|----------|-----------------------|--------|
| ENF-90 | Sécurité — Héritage & exceptions d'affectation | La résolution du périmètre effectif d'un gestionnaire (patrimoine ∪ inclusions − exclusions) est centralisée dans `AuthorizationService` (ADR-02), jamais dupliquée côté contrôleur ou frontend. | ED un jeu de tests d'autorisation couvrant les 4 combinaisons (patrimoine seul, patrimoine+inclusion, patrimoine+exclusion, bien seul) · Q ils s'exécutent · A 0 accès hors périmètre effectif. | RM-90→RM-93 |

---

## 3. Registre des règles métier (RM) — *nouveau registre*

> Aucun registre « règles métier » numéroté n'existait avant cette décision (les règles étaient portées par les critères ED/Q/A des EF). Ce registre est créé pour les règles structurantes de la décision Patrimoine et pourra être étendu aux lots futurs.

| ID | Règle métier | Exigence(s) liée(s) |
|----|--------------|----------------------|
| RM-90 | Un bailleur peut posséder un ou plusieurs patrimoines. | EF-90 |
| RM-91 | Un patrimoine appartient à un seul bailleur. | EF-90 |
| RM-92 | Un patrimoine peut contenir plusieurs biens ; un bien appartient obligatoirement à un seul patrimoine. | EF-92 |
| RM-93 | Les types de biens sont choisis dans une liste administrable (`Appartement`, `Boutique`, `Bureau`, `Villa`, `Terrain`, `Entrepôt`, `Autre`). | EF-91 |
| RM-94 | Un bien peut avoir plusieurs contrats (`Bail`) dans son historique ; un seul contrat `ACTIF` est autorisé simultanément par bien. | EF-96 (= EF-12 existant) |
| RM-95 | Un gestionnaire peut être affecté à un ou plusieurs patrimoines, et/ou à un ou plusieurs biens spécifiques. | EF-93/94 |
| RM-96 | Une affectation **bien** est prioritaire sur une affectation **patrimoine** héritée, pour le bien concerné. | EF-95 |
| RM-97 | Une affectation bien peut être de type `INCLUSION` (octroie l'accès) ou `EXCLUSION` (retire l'accès hérité). | EF-94 |
| RM-98 | Le périmètre effectif d'un gestionnaire sur un patrimoine = (biens du patrimoine si affectation patrimoine ACTIVE) ∪ (biens en `INCLUSION` ACTIVE) − (biens en `EXCLUSION` ACTIVE), évalué par gestionnaire. | EF-95, ENF-90 |
| RM-99 | Toute table métier de ce périmètre porte `bailleur_id` (cohérence ADR-01) ; `Patrimoine` n'est pas une exception. | ADR-01, ADR-11 |

> ✅ **RM-98 validé par le PO le 2026-06-21** : la formule de résolution est confirmée telle que proposée. Deux règles complémentaires actées dans la même décision : une `EXCLUSION` créée sans affectation patrimoine active correspondante pour ce gestionnaire est **rejetée en 400** (RS-04, `securite-patrimoine.md` §9) ; une `INCLUSION` redondante avec une affectation patrimoine déjà active sur le même bien est **tolérée** (idempotente). Réserve Sprint 2 (`plan-execution-patrimoine.md`) levée.

---

## 4. Modèle de données (proposition logique — aucune migration SQL produite)

### 4.1 Diagramme logique mis à jour

```
Bailleur (1) ───< (N) Patrimoine (1) ───< (N) Bien (1) ───< (N) Bail [="Contrat"]
    │                     │                    │                    │
    │                     │                    │ (1)                │ (1)
    │                     │                    ├───< (N) Affectation [bien_id]  >─── (N) Gestionnaire
    │                     │                    │                    │
    │                     └───< (N) Affectation [patrimoine_id] >─── (N) Gestionnaire
    │                                           │
    │                                           ├───< (N) Paiement ───< (1) Garantie
    │                                           └───< (N) Alerte
    │
    ├───< (N) Honoraire (rattaché à Affectation + période, inchangé)
    ├───< (N) AuditLog
    └───< (N) Invitation

TypeBien (référentiel administrable) ◄──── Bien.type
```

> **Lecture :** `Affectation` porte désormais **soit** `patrimoine_id` **soit** `bien_id` (jamais les deux) — cf. §4.3. `Bail` reste l'entité technique ; « Contrat » est le terme produit utilisé dans la décision (cf. ADR-11 §Décision point 3).

### 4.2 Nouvelles entités / attributs (proposition)

| Entité | Nouveauté | Détail |
|--------|-----------|--------|
| `Patrimoine` *(nouvelle)* | Entité complète | `id` (UUID), `bailleurId` (UUID, NOT NULL — cohérence ADR-01), `nom` (String, NOT NULL), `statut` (`ACTIF`\|`ARCHIVE`), `dateCreation`. |
| `Bien` | `patrimoineId` *(nouveau, NOT NULL)* | FK vers `Patrimoine`. Remplace l'absence actuelle de regroupement. |
| `Bien` | `type` *(contraint)* | Passe de chaîne libre à référence vers `TypeBien` (ou `CHECK` SQL sur liste fermée si table de référence jugée disproportionnée à l'implémentation). |
| `TypeBien` *(nouvelle, référentiel)* | Entité de référence | `code` (`APPARTEMENT`\|`BOUTIQUE`\|`BUREAU`\|`VILLA`\|`TERRAIN`\|`ENTREPOT`\|`AUTRE`), `libelle`, `actif` (booléen, pour permettre l'administration sans suppression physique). |
| `Affectation` | `patrimoineId` *(nouveau, nullable)* | Mutuellement exclusif avec `bienId` (devient nullable). Contrainte d'intégrité conceptuelle : exactement un des deux est renseigné. |
| `Affectation` | `typeException` *(nouveau, nullable)* | `INCLUSION`\|`EXCLUSION` — pertinent uniquement quand `bienId` est renseigné **et** qu'une affectation patrimoine existe en parallèle pour le même gestionnaire. `NULL` quand l'affectation bien est autonome (aucun héritage patrimoine concerné). |

### 4.3 Cardinalités & contraintes d'intégrité (conceptuelles)

| Règle | Contrainte conceptuelle (à concrétiser à l'implémentation, **aucune migration produite ici**) |
|-------|----------------------------------------------------------------------------------------------|
| RM-91 | `patrimoine.bailleur_id NOT NULL` |
| RM-92 | `bien.patrimoine_id NOT NULL` (FK) ; un bien n'a qu'un seul `patrimoine_id` |
| RM-94 (= EF-12 existant) | Inchangé : `CREATE UNIQUE INDEX uq_bail_actif ON bail (bien_id) WHERE statut = 'ACTIF';` |
| Exclusivité patrimoine/bien sur `Affectation` | `CHECK ((patrimoine_id IS NOT NULL AND bien_id IS NULL) OR (patrimoine_id IS NULL AND bien_id IS NOT NULL))` |
| Unicité affectation patrimoine active | `CREATE UNIQUE INDEX uq_affectation_patrimoine_active ON affectation (patrimoine_id, gestionnaire_id) WHERE statut = 'ACTIVE' AND patrimoine_id IS NOT NULL;` *(empêche les doublons d'affectation patrimoine pour un même gestionnaire — à confirmer si plusieurs gestionnaires peuvent partager un même patrimoine, hypothèse ouverte §EB addendum)* |
| `typeException` cohérent | `CHECK (type_exception IS NULL OR bien_id IS NOT NULL)` — une exception n'a de sens qu'au niveau bien |

### 4.4 Index de performance (proposition)

`bien(patrimoine_id)`, `affectation(patrimoine_id, statut)`, `affectation(bien_id, gestionnaire_id, statut)` — pour préserver ENF-06 (< 2 s / 50 biens) malgré la jointure supplémentaire patrimoine → bien sur les dashboards.

### 4.5 Impact migration base de données *(narratif — aucun script produit)*

1. Création des tables `patrimoine` et `type_bien` (ou contrainte `CHECK` si table de référence jugée superflue), RLS `ENABLE`+`FORCE` sur `patrimoine` (même policy `bailleur_id` que les tables existantes, ADR-01).
2. **Migration de données** : pour chaque bailleur existant ayant au moins un bien, création d'un patrimoine par défaut (ex. « Patrimoine principal ») et rattachement de tous ses biens existants — nécessaire pour satisfaire `bien.patrimoine_id NOT NULL` sans rupture de service. *(Étape sensible identifiée dans ADR-11 §Risques.)*
3. Ajout de `affectation.patrimoine_id` (nullable), `affectation.type_exception` (nullable), passage de `affectation.bien_id` en nullable, ajout du `CHECK` d'exclusivité.
4. Migration des valeurs existantes de `bien.type` (chaîne libre) vers les codes `TypeBien` les plus proches, avec **vérification manuelle des valeurs non mappables** (risque de perte d'information si saisies très libres) — à cadrer précisément en Sprint 1 du Plan d'Exécution.

---

## 5. Contrats d'API impactés (proposition — non implémentée)

| Endpoint (proposé) | Méthode | Description | Sécurité |
|---------------------|---------|--------------|----------|
| `/api/patrimoines` | GET/POST | Liste / création de patrimoines | BAILLEUR ; périmètre = ses patrimoines |
| `/api/patrimoines/{id}` | PUT/DELETE (archivage) | Modification / archivage d'un patrimoine | BAILLEUR seul |
| `/api/biens` *(existant, étendu)* | POST/PUT | Création/modification d'un bien | Ajoute `patrimoineId` obligatoire, `type` validé contre la liste administrable |
| `/api/types-biens` | GET (+ administration) | Liste des types administrables | Lecture : tous rôles authentifiés ; administration : BAILLEUR (à confirmer, cf. hypothèse EB addendum §4) |
| `/api/affectations` *(existant, étendu)* | POST | Création d'une affectation patrimoine **ou** bien (`typeException` si bien + héritage patrimoine) | BAILLEUR seul ; 400 si `patrimoineId` et `bienId` renseignés simultanément ou aucun des deux |
| `/api/patrimoines/{id}/affectations` | GET | Historique des affectations d'un patrimoine | BAILLEUR seul |

> Contrats détaillés (schémas req/resp) à figer en OpenAPI **au début de l'implémentation**, après approbation du Plan d'Exécution — cohérent avec la pratique déjà actée pour le reste du projet (OpenAPI non encore produit, dette déjà connue §13 `project-state.md`).

---

## 6. Matrice de traçabilité (addendum)

| Besoin (EB) | Exigence (CDC) | Règle métier | Cas de test prévu |
|-------------|------------------|---------------|---------------------|
| BF-90 | EF-90 | RM-90/91 | TC-92 CRUD patrimoine + cloisonnement bailleur + rejet archivage si affectation patrimoine `ACTIVE` (RS-06) |
| BF-91 | EF-91 | RM-93 | TC-93 rejet type hors liste administrable (400) |
| BF-92 | EF-92 | RM-92 | TC-94 rattachement obligatoire + rejet patrimoine d'un autre bailleur (404/403) |
| BF-93 | EF-93 | RM-95 | TC-95 affectation patrimoine → accès hérité à tous les biens |
| BF-94/95 | EF-94/95 | RM-96/97/98 | TC-96 priorité bien sur patrimoine (4 combinaisons : patrimoine seul, +inclusion, +exclusion, bien seul) |
| BF-96 | EF-96 | RM-94 | TC-97 non-régression EF-12 (un seul contrat actif/bien) — réutilise les tests existants, aucun nouveau test requis |

---

## 7. Score de maturité de l'addendum (/20)

| Axe | Note (0–4) | Commentaire |
|-----|-----------|-------------|
| Complétude | 4 | RM-98 (algorithme de résolution) validé par le PO le 2026-06-21 |
| Qualité | 4 | Critères ED/Q/A testables sur chaque EF |
| Sécurité | 3 | Extension ReBAC validée sur le papier (RM-98/RS-04/RS-06) mais non encore testée en code (zone de risque prioritaire, cf. `securite-patrimoine.md`) |
| Traçabilité | 4 | Matrice BF→EF→RM→TC complète, numérotation sans collision (90+) |
| Automatisation | 0 | Aucun code, aucune migration — conforme à la contrainte « ne rien coder » de cette analyse |
| **Total** | **15/20** | « Solide » — RM-98 validé par le PO (réserve Sprint 2 levée) ; **ne constitue pas un Gate**, ce score qualifie uniquement la maturité documentaire de l'addendum avant Plan d'Exécution |
